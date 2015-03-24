package ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.tae;



import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.io.Files;

import scala.concurrent.duration.FiniteDuration;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Inbox;
import akka.actor.Props;
import akka.contrib.pattern.ClusterSingletonManager;
import akka.contrib.pattern.ClusterSingletonProxy;
import ca.ubc.cs.beta.aeatk.algorithmrunconfiguration.AlgorithmRunConfiguration;
import ca.ubc.cs.beta.aeatk.algorithmrunresult.AlgorithmRunResult;
import ca.ubc.cs.beta.aeatk.algorithmrunresult.RunStatus;
import ca.ubc.cs.beta.aeatk.algorithmrunresult.RunningAlgorithmRunResult;
import ca.ubc.cs.beta.aeatk.concurrent.threadfactory.SequentiallyNamedThreadFactory;
import ca.ubc.cs.beta.aeatk.options.AbstractOptions;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.AbstractAsyncTargetAlgorithmEvaluator;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.TargetAlgorithmEvaluator;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.TargetAlgorithmEvaluatorCallback;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.TargetAlgorithmEvaluatorOptions;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.TargetAlgorithmEvaluatorRunObserver;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.actors.aeatk.TAEBridgeActor;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.actors.aeatk.TAEWorkerCoordinator;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.actors.cluster.ClusterManagerActor;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.helper.AkkaHelper;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.messages.AlgorithmRunStatus;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.messages.RequestRunBatch;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.messages.RequestRunConfigurationUpdate;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.messages.ShutdownMessage;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.messages.WhereAreYou;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.options.AkkaWorkerOptions;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.worker.AkkaWorker;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.exceptions.TargetAlgorithmAbortException;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.exceptions.TargetAlgorithmEvaluatorShutdownException;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.init.TargetAlgorithmEvaluatorLoader;


/**
 * Target Algorithm Evaluator Implementation that uses the Akka actor system 
 * @author Steve Ramage <seramage@cs.ubc.ca>
 *
 */
public class AkkaTargetAlgorithmEvaluator extends AbstractAsyncTargetAlgorithmEvaluator {


	


	private final AkkaTargetAlgorithmEvaluatorOptions opts;

	private final ActorSystem system;
	
	
	private final AtomicBoolean notifyShutdownCalled = new AtomicBoolean(false);
	
	private final CountDownLatch inboxProcessingThreadDone = new CountDownLatch(1);
	
	
	private final ConcurrentHashMap<UUID, CallerContext> uuidToCallerContextMap = new ConcurrentHashMap<>();
	
	/**
	 * For every submit token, stores the set of completed run configurations
	 * 
	 */
	private final ConcurrentHashMap<UUID, Map<AlgorithmRunConfiguration, AlgorithmRunResult>> uuidToCompletedRunResults = new ConcurrentHashMap<>();
	
	
	/**
	 * For every submit token, stores the set of outstanding run configurations
	 * 
	 * Entries are created on evaluateRunAsync()
	 * Entries in the set are removed when we get a ProcessRunCompleted message, the key is removed when we dispatch the caller.
	 */
	private final ConcurrentHashMap<UUID, Set<AlgorithmRunConfiguration>> uuidToOutstandingRunConfigsSet = new ConcurrentHashMap<>();
	
	
	private final ConcurrentHashMap<UUID, Map<AlgorithmRunConfiguration, AlgorithmRunResult>> uuidToObserverRunResultMap = new ConcurrentHashMap<>();
	
	
	private final ConcurrentHashMap<UUID, AtomicLong> lastObserverNotificationTime = new ConcurrentHashMap<>();
	
	private final Logger log = LoggerFactory.getLogger(getClass());
	
	private final ExecutorService runCompleteMessageProcessingThreadPool = Executors.newSingleThreadExecutor(new SequentiallyNamedThreadFactory("AKKA TAE Response Processing Thread", false));
	
	private final ExecutorService taeCallbackCompletionThreadPool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 2, new SequentiallyNamedThreadFactory("AKKA TAE Callback Processing Thread", false));
	
	private final ExecutorService taeObserverNotifyThreadPool = Executors.newCachedThreadPool( new SequentiallyNamedThreadFactory("AKKA TAE Observer Processing Thread", false));
	
	private final ScheduledExecutorService ses = Executors.newScheduledThreadPool(1, new SequentiallyNamedThreadFactory("AKKA TAE Observation Scheduling Thread", false));
	
	private final BlockingQueue<UUID> callbacksToFire = new LinkedBlockingQueue<>();
	
	private final BlockingQueue<UUID> observerToFire = new LinkedBlockingQueue<>();
	
	private final ExecutorService synchronousWorkerThreadPool;
	
	
	private final Inbox completionInbox;
	
	private final Inbox observerInbox;
	
	
	private final Inbox shutdownInbox;
	 
	
	private final ActorRef masterTAE;
	private final ActorRef coordinator;
	
	public static final String ADDITIONAL_RUN_DATA_ENCODED_EXCEPTION_PREFIX = "ENCODED EXCEPTION: ";
	
	private final Semaphore workerRegulatingSemaphore = new Semaphore(0,true);
	
	
	public final AtomicInteger outstandingRunBatches = new AtomicInteger(0);
	 
	@SuppressWarnings("unused")
	public AkkaTargetAlgorithmEvaluator(final AkkaTargetAlgorithmEvaluatorOptions opts, final Map<String, AbstractOptions> otherTAEOptions)
	{
		
		
		String logLevel;
		
		switch(opts.logLevel)
		{
		
		case TRACE:
			logLevel = "DEBUG";
			break;
		case WARN:
			logLevel = "WARNING";
			break;
		case OFF:
		case DEBUG:
		case ERROR:
		case INFO:
			
		default:
			throw new IllegalStateException("Unknown log level: " + opts.logLevel);
		
		}
		
		String configuration ="akka {\n" + 
				"  loglevel = \""+logLevel+"\"\n" +
				"  actor {\n" + 
				"    provider = \"akka.cluster.ClusterActorRefProvider\"\n" + 
				"  }\n" + 
				"  remote {\n" + 
				"    log-remote-lifecycle-events = off\n" + 
				"  }\n" + 
				"\n" + 
				"  cluster {\n" + 		 
				"    auto-down-unreachable-after = 10s\n" + 
				"	  jmx.enabled = " + (opts.akkaClusterOptions.jmxEnabled ? "on" : "off") + "\n"+ 
				"	  gossip-interval = "+opts.akkaClusterOptions.gossipInterval + " ms\n"+
				"	  leader-actions-interval = "+ opts.akkaClusterOptions.leaderActionInterval+" ms\n"+
				"	  unreachable-nodes-reaper-interval = " +opts.akkaClusterOptions.unreachableNodesReaperInterval+" ms\n"+
				"	  periodic-tasks-initial-delay = "+ opts.akkaClusterOptions.periodicTasksInitialDelay + " ms\n"+
				"     failure-detector.acceptable-heartbeat-pause = " + opts.akkaClusterOptions.failureDetectorAcceptablePause +" ms\n"+
				"	  failure-detector.heartbeat-interval = "+opts.akkaClusterOptions.failureDetectorHeartbeatInterval+" ms\n"+
		
				"    auto-down-unreachable-after = " + opts.akkaClusterOptions.autoDownUnreachableAfter + " ms\n" + 
				"  }\n" + 
				"}\n";
			
	
		
		system = AkkaHelper.startAkkaSystem(opts.akkaClusterOptions.networks, opts.dir, configuration, opts.akkaClusterOptions.id);
		
		this.opts = opts;
		
		
		ActorRef singletonProxyManager = system.actorOf(ClusterSingletonManager.defaultProps(Props.create(TAEWorkerCoordinator.class), "coordinator", "END", null),"singleton");
		
		
		ActorRef clusterNode = system.actorOf(Props.create(ClusterManagerActor.class), "clusterManager");
		
		coordinator = system.actorOf(ClusterSingletonProxy.defaultProps("/user/singleton/coordinator",null),"coordinatoryProxy");
		
		
		
		Inbox singletonWaiting = AkkaHelper.getInbox(system);
		
		while(true)
		{
			
			try 
			{
				singletonWaiting.send(coordinator, new WhereAreYou());
				singletonWaiting.receive(new FiniteDuration(10, TimeUnit.SECONDS));
				
				if(true)
				{
					break;
				} else
				{
					throw new TimeoutException();
				}
				
			} catch(TimeoutException e)
			{
				log.info("Waiting for worker coordinator to come online, trying again");
				
				//Doesn't matter
			}	
		} 
		
		
		
		masterTAE = system.actorOf(Props.create(TAEBridgeActor.class,coordinator, opts.observerFrequency,opts.printStatusFrequency), "masterTAE");
		
		
		
		completionInbox = AkkaHelper.getInbox(system);
		
		observerInbox = AkkaHelper.getInbox(system);
		
		
		shutdownInbox= AkkaHelper.getInbox(system);
		
	
		runCompleteMessageProcessingThreadPool.execute( new ProcessRunMessageCompletionHandler());
		
		
		for(int i=0; i < ManagementFactory.getOperatingSystemMXBean().getAvailableProcessors(); i++)
		{
			taeObserverNotifyThreadPool.execute(new TargetAlgorithmEvaluatorObserverNotificationRunnable());
		}
		
		taeObserverNotifyThreadPool.execute(new ObserverMailboxReader());
		
		
		for(int i=0; i < ManagementFactory.getOperatingSystemMXBean().getAvailableProcessors(); i++)
		{
			taeCallbackCompletionThreadPool.execute(new TargetAlgorithmEvaluatorCallbackNotificationRunnable());
		}
		
		
		
		if(this.opts.syncWorker)
		{
			log.info("Starting synchronous worker");
			
			synchronousWorkerThreadPool = Executors.newCachedThreadPool(new SequentiallyNamedThreadFactory("AKKA Target Algorithm Evaluator Worker Threads", true));
			
			Runnable run = new Runnable()
			{

				@Override
				public void run() {
					AkkaWorker worker = new AkkaWorker();
					
					AkkaWorkerOptions workerOptions = new AkkaWorkerOptions();
					
					
					
					
					
					TargetAlgorithmEvaluatorOptions taeOptions = new TargetAlgorithmEvaluatorOptions();
					
					taeOptions.targetAlgorithmEvaluator = opts.tae;
					
					
					if(taeOptions.targetAlgorithmEvaluator.equals(AkkaTargetAlgorithmEvaluatorFactory.getTAEName()))
					{
						//In reality we are just too lazy to allow you to specify a different set of TAE options
						throw new IllegalArgumentException("You cannot create a synchronous worker that that also uses the AKKA TAE to resolve things, maybe this article will satiate you: http://en.wikipedia.org/wiki/List_of_paradoxes");
					}
					
					
						
					Map<String, AbstractOptions> opts;
					if(otherTAEOptions != null)
					{
						opts = otherTAEOptions;
					} else
					{
						log.warn("The method being used to load the target algorithm evaulator is deprecated and consequently your options are being ignored, contact the developer to fix this");
						opts = TargetAlgorithmEvaluatorLoader.getAvailableTargetAlgorithmEvaluators();
					}
					
					TargetAlgorithmEvaluator tae = taeOptions.getTargetAlgorithmEvaluator(opts);
					worker.executeWorker(workerOptions, tae, system, synchronousWorkerThreadPool, coordinator,workerRegulatingSemaphore);
				}
				
			};
			
			synchronousWorkerThreadPool.execute(run);
			
			
		} else
		{
			synchronousWorkerThreadPool = null;
		}
		
		
		
		
		log.debug("AKKA Target Algorithm Started");
		
	}


	@Override
	/***
	 * Sets up available data structures and then notifies the master watch dog of all runs to do.
	 */
	public void evaluateRunsAsync(List<AlgorithmRunConfiguration> runConfigs, TargetAlgorithmEvaluatorCallback taeCallback,	TargetAlgorithmEvaluatorRunObserver runStatusObserver) {
	
		
		
		if(runConfigs.size() == 0)
		{
			if(runStatusObserver != null)
			{
				runStatusObserver.currentStatus(Collections.<AlgorithmRunResult> emptyList());
			}
			
			taeCallback.onSuccess(Collections.<AlgorithmRunResult>emptyList());
			return;
		}
		
		if(notifyShutdownCalled.get())
		{
			throw new IllegalStateException("Target Algorithm Evaluator has already been shutdown, cannot submit runs to it");
		}
		
		
		if(synchronousWorkerThreadPool != null)
		{
			//We have a synchronized worker
			
			int totalOutstandingRuns = this.outstandingRunBatches.incrementAndGet();
			if(totalOutstandingRuns != 1)
			{
				throw new IllegalStateException("Total Outstanding Runs was " + totalOutstandingRuns + ". You cannot use the synchronous worker option unless only one run will be submitted at a time");
			}
			
			this.workerRegulatingSemaphore.release();
		}
		
		
		UUID uuid = UUID.randomUUID();
		RequestRunBatch rrb;
		synchronized(uuid)
		{
			CallerContext ctx = new CallerContext(runConfigs, taeCallback, runStatusObserver);
			uuidToCallerContextMap.put(uuid, ctx);
			uuidToOutstandingRunConfigsSet.put(uuid, Collections.newSetFromMap(new ConcurrentHashMap<AlgorithmRunConfiguration, Boolean>()));
			
			ConcurrentHashMap<AlgorithmRunConfiguration, AlgorithmRunResult> observerMap = new ConcurrentHashMap<AlgorithmRunConfiguration, AlgorithmRunResult>();
			uuidToObserverRunResultMap.put(uuid, observerMap);
			
			List<AlgorithmRunResult> results = new ArrayList<>();
			
			for(AlgorithmRunConfiguration runConfig : runConfigs)
			{
				AlgorithmRunResult result = new RunningAlgorithmRunResult(runConfig, 0, 0, 0, 0, 0, ctx.getKillHandler(runConfig));
				observerMap.put(runConfig,result);
				results.add(result);
			}
			
			if(runStatusObserver != null)
			{
				runStatusObserver.currentStatus(results);
			}
			
			this.lastObserverNotificationTime.put(uuid, new AtomicLong(System.currentTimeMillis()));
			
			uuidToOutstandingRunConfigsSet.get(uuid).addAll(runConfigs);

			uuidToCompletedRunResults.put(uuid, new ConcurrentHashMap<AlgorithmRunConfiguration, AlgorithmRunResult>());
			
		
			
			rrb= new RequestRunBatch(observerInbox.getRef(), completionInbox.getRef(), runConfigs, uuid);
		}
		
		 completionInbox.send(masterTAE, rrb);
		
		
		
		
		
	}

	

	@Override
	public boolean isRunFinal() {
		return false;
	}

	@Override
	public boolean areRunsPersisted() {
		return false;
	}

	@Override
	public boolean areRunsObservable() {
		return true;
	}


	@Override
	public synchronized void notifyShutdown() {
	

		if(!this.notifyShutdownCalled.compareAndSet(false, true))
		{
			
			return;
		}

	
		log.info("Shutting down Target Algorithm Evaluator");
		

		
		ses.scheduleAtFixedRate(new Runnable() {

			@Override
			public void run() {
				shutdownInbox.send(masterTAE, new ShutdownMessage());
				shutdownInbox.send(coordinator, new ShutdownMessage());
				
				shutdownInbox.send(completionInbox.getRef(), new ShutdownMessage());
				shutdownInbox.send(observerInbox.getRef(), new ShutdownMessage());
				
				
			}
			
		}, 0, 2, TimeUnit.SECONDS);
		
		
		this.runCompleteMessageProcessingThreadPool.shutdownNow();
		this.taeCallbackCompletionThreadPool.shutdownNow();
		this.taeObserverNotifyThreadPool.shutdownNow();
		if(synchronousWorkerThreadPool != null)
		{
			this.synchronousWorkerThreadPool.shutdownNow();
		}
		
		
		
		try {
			this.taeCallbackCompletionThreadPool.awaitTermination(365, TimeUnit.DAYS);
			
			this.runCompleteMessageProcessingThreadPool.awaitTermination(365, TimeUnit.DAYS);
			this.taeObserverNotifyThreadPool.awaitTermination(365, TimeUnit.DAYS);
			
			if(synchronousWorkerThreadPool != null)
			{
				this.synchronousWorkerThreadPool.awaitTermination(365,TimeUnit.DAYS);
			}

			//Shutdown this one last
			ses.shutdownNow();
			ses.awaitTermination(365, TimeUnit.DAYS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			log.warn("Interrupted during TAE shutdown, TAE may not have cleaned up properly");
		}
		
		
		
		
		
		shutdownInbox.send(masterTAE, new ShutdownMessage());
		shutdownInbox.send(coordinator, new ShutdownMessage());
		
		shutdownInbox.send(completionInbox.getRef(), new ShutdownMessage());
		shutdownInbox.send(observerInbox.getRef(), new ShutdownMessage());
		
		
		system.shutdown();
	
		TargetAlgorithmEvaluatorShutdownException taeShutdownException = new TargetAlgorithmEvaluatorShutdownException();
		
		log.debug("Notifying callbacks of shutdown");
		for(CallerContext callContext : uuidToCallerContextMap.values())
		{
			try
			{
				callContext.getTargetAlgorithmEvaluatorCallback().onFailure(taeShutdownException);
			} catch(RuntimeException t)
			{
				log.error("Error occurred notifying of failure", t);
			}
		}
		
		log.info("AKKA Target Algorithm Evaluator Shutdown");
	}
	
	
	
	private final class ObserverMailboxReader implements Runnable {
		public void run()
		{
			Thread.currentThread().setName("AKKA Observer Mailbox Reader");
			
			//inbox.send(masterWatchDog, new RegisterMailboxToBeShutdown());
			try 
			{
				while(true)
				{
					Object o;
					try
					{
						 o = observerInbox.receive(new FiniteDuration(28, TimeUnit.DAYS));
					} catch(Throwable t)
					{
						if(t instanceof InterruptedException)
						{
							Thread.currentThread().interrupt();
							return;
							
						} else
						{
							throw t;
						}
					}

					
					if(o instanceof AlgorithmRunStatus)
					{
						AlgorithmRunStatus ars = (AlgorithmRunStatus) o;
						
						UUID uuid = ars.getUUID();
						synchronized(uuid)
						{
								
							AlgorithmRunResult result = ars.getAlgorithmRunResult();
							
							
							if(!result.isRunCompleted())
							{
								result = new RunningAlgorithmRunResult(result.getAlgorithmRunConfiguration(), result.getRuntime(), result.getRunLength(), result.getQuality(), result.getResultSeed(), result.getWallclockExecutionTime(), uuidToCallerContextMap.get(ars.getUUID()).getKillHandler(result.getAlgorithmRunConfiguration()));
							}
							
							
							Map<AlgorithmRunConfiguration, AlgorithmRunResult> runResultMap = uuidToObserverRunResultMap.get(uuid);
							
							if(runResultMap == null)
							{
								//Probably already fired callback
								continue;
							}
							runResultMap.put(ars.getAlgorithmRunResult().getAlgorithmRunConfiguration(), result);
							
							AkkaTargetAlgorithmEvaluator.this.observerToFire.add(ars.getUUID());
							
						} 
						
					} else if(o instanceof ShutdownMessage)
					{
						observerInbox.send(observerInbox.getRef(), o);	
							
					} else
					{
						log.error("Got unknown message on observer inbox: {}" ,o);
					}
					
				}
			} finally
			{
				log.info("AKKA Observer Mailbox Reader shutting down");
			}
		}
	}





	private final class ProcessRunMessageCompletionHandler implements Runnable {
		@Override
		public void run() {
			
			//inbox.send(masterWatchDog, new RegisterMailboxToBeShutdown());
			try 
			{
				while(!Thread.interrupted() )
				{
					Object o = null;
				
					try {
						try {
							o = completionInbox.receive(new FiniteDuration(1, TimeUnit.DAYS));
						} finally
						{
							//System.err.println("Done recieve");
						}
					} catch(Throwable e)
					{
						if(e instanceof InterruptedException)
						{
							Thread.currentThread().interrupt();
							return;
						} else
						{
							System.out.println("Error: " + e);
							throw e;
						}
					}
					//log.info("Message recieved: {}", o);
					if(o == null)
					{
						continue;
					}
					
					
					if(o instanceof AlgorithmRunStatus)
					{
						
						AlgorithmRunStatus ars = (AlgorithmRunStatus) o;
						
						if(ars.getAlgorithmRunResult().getRunStatus() == RunStatus.ABORT)
						{
							String addlRunData = ars.getAlgorithmRunResult().getAdditionalRunData();
							
							if(addlRunData.startsWith(AkkaTargetAlgorithmEvaluator.ADDITIONAL_RUN_DATA_ENCODED_EXCEPTION_PREFIX))
							{
								log.error("Worker encountered error, exception details: {}",  addlRunData.replaceAll(";", "\n"));
							}
						}
						
						UUID uuid = ars.getUUID();
						synchronized(uuid)
						{
							
							
							Set<AlgorithmRunConfiguration> outstandingRunConfigurations = uuidToOutstandingRunConfigsSet.get(ars.getUUID());
							
							

							//handler.onFailure(new TargetAlgorithmAbortException(run));
							
							
							if(outstandingRunConfigurations != null)
							{
								if(outstandingRunConfigurations.remove(ars.getAlgorithmRunResult().getAlgorithmRunConfiguration()))
								{
									
									uuidToCompletedRunResults.get(uuid).put(ars.getAlgorithmRunResult().getAlgorithmRunConfiguration(),ars.getAlgorithmRunResult());
									uuidToObserverRunResultMap.get(uuid).put(ars.getAlgorithmRunResult().getAlgorithmRunConfiguration(),ars.getAlgorithmRunResult());
									
									
									if(outstandingRunConfigurations.size() == 0)
									{
										
										if(synchronousWorkerThreadPool != null)
										{
											outstandingRunBatches.decrementAndGet();
											try {
												workerRegulatingSemaphore.acquire();
											} catch (InterruptedException e) {
											
												e.printStackTrace();
												Thread.currentThread().interrupt();
											}
										}
										
										callbacksToFire.add(uuid);
									} else
									{
										observerToFire.add(uuid);
										log.trace("CHP Left: {}, {}", outstandingRunConfigurations.size(), ars.getAlgorithmRunResult());
									}
								} else
								{
									//It's a duplicate message and drop
								}
							}
						}
						
						
						
					} else if(o instanceof ShutdownMessage)
					{
						completionInbox.send(completionInbox.getRef(), o);
					} else
					{
						log.warn("Recieved unknown message in the completion Inbox: {} , {}", o.getClass().getCanonicalName(), o);
					}
					
					
				}
			} finally
			{
				AkkaTargetAlgorithmEvaluator.this.inboxProcessingThreadDone.countDown();
			}
			Thread.currentThread().interrupt();
			return;
			
			
		}
	}



	private final class TargetAlgorithmEvaluatorCallbackNotificationRunnable implements Runnable {
		@Override
		public void run() 
		{
			try 
			{
				while(true)
				{
					UUID uuid = callbacksToFire.take();
					synchronized (uuid)
					{
						try
						{
							CallerContext callContext = uuidToCallerContextMap.remove(uuid);
							
						
							if(callContext != null)
							{
								//log.debug("Notifying callback for token : {} " ,uuid);
								TargetAlgorithmEvaluatorCallback taeCallback = callContext.getTargetAlgorithmEvaluatorCallback();
								
								List<AlgorithmRunResult> runs = new ArrayList<AlgorithmRunResult>(callContext.getAlgorithmRunConfigurations().size());
								
								AlgorithmRunResult abortedRun = null;
								
								for(AlgorithmRunConfiguration runConfig : callContext.getAlgorithmRunConfigurations())
								{
									if(uuidToCompletedRunResults.get(uuid).get(runConfig).getRunStatus() == RunStatus.ABORT)
									{
										abortedRun = uuidToCompletedRunResults.get(uuid).get(runConfig);
									}
											
									runs.add(uuidToCompletedRunResults.get(uuid).get(runConfig));
								}
							
								if(abortedRun != null)
								{
									taeCallback.onFailure(new TargetAlgorithmAbortException(abortedRun));
								} else
								{
									try 
									{
										
										callContext.getTargetAlgorithmEvaluatorRunObserver().currentStatus(runs);
									} catch(RuntimeException e)
									{
										log.error("Error occured while notifying observer before final run completion", e);
									}
									try 
									{
										taeCallback.onSuccess(runs);
									} catch(RuntimeException e)
									{
										taeCallback.onFailure(e);
									} catch(Throwable t)
									{
										taeCallback.onFailure(new IllegalStateException(t));
									}
								}
								
							}
						} finally
						{
							//Clean up data structures
							uuidToCallerContextMap.remove(uuid);
							uuidToCompletedRunResults.remove(uuid);
							uuidToOutstandingRunConfigsSet.remove(uuid);
							uuidToObserverRunResultMap.remove(uuid);
							lastObserverNotificationTime.remove(uuid);
							//System.err.println("Firing callback for: " + uuid);
						}
					}
					
				}
			} catch(InterruptedException e)
			{
				Thread.currentThread().interrupt();
				return;
			}
		}
	}



	private final class TargetAlgorithmEvaluatorObserverNotificationRunnable implements Runnable 
	{
		public void run()
		{
			while(true)
			{
				UUID uuid;
				try {
					uuid = observerToFire.take();
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					return;
				}
				
				synchronized(uuid)
				{
					CallerContext callContext = uuidToCallerContextMap.get(uuid);
					
					if(callContext == null)
					{ //Callback already fired
						continue;
					}
					
					if(System.currentTimeMillis() - lastObserverNotificationTime.get(uuid).get() > opts.observerFrequency)
					{
						lastObserverNotificationTime.get(uuid).set(System.currentTimeMillis());
					} else
					{
						continue;
					}
					
					List<AlgorithmRunResult> runResult = new ArrayList<AlgorithmRunResult>(callContext.getAlgorithmRunConfigurations().size());
					
					for(AlgorithmRunConfiguration runConfig : callContext.getAlgorithmRunConfigurations())
					{
						
						AlgorithmRunResult resultToAdd =uuidToObserverRunResultMap.get(uuid).get(runConfig);
						
						try 
						{
							if(!uuidToOutstandingRunConfigsSet.get(uuid).contains(runConfig))
							{ //The run is done, and further more by this point, we have a completed run
								resultToAdd = uuidToCompletedRunResults.get(uuid).get(runConfig);
							}
						
						} catch(NullPointerException e)
						{
							e.printStackTrace();
							System.err.println(uuid);
							System.err.println(uuidToOutstandingRunConfigsSet);
							System.err.println(uuidToOutstandingRunConfigsSet.get(uuid));
							System.err.println(runConfig);
							
							
							Runtime.getRuntime().exit(1);
						}
						runResult.add(resultToAdd);
					}
					
					callContext.getTargetAlgorithmEvaluatorRunObserver().currentStatus(runResult);
					
					for(AlgorithmRunConfiguration runConfig : callContext.getAlgorithmRunConfigurations())
					{
						if (callContext.getKillHandler(runConfig).isKilled())
						{
							
							//System.err.println("Kill Recieved: " + runConfig);
							//Only send if the run is still outstanding
							
							if(uuidToCompletedRunResults.get(uuid).get(runConfig) == null)
							{
								//System.err.println("Need to send kill " + runConfig);
								//Object message = new RequestObserverUpdateMessage( new ProcessRunMessage(token, runConfig), true);
								//inbox.send(masterWatchDog, message);
								observerInbox.send(masterTAE, new RequestRunConfigurationUpdate(runConfig, true, uuid));
								
							} else
							{
								//System.err.println("No Kill for:" + runConfig + " because " + tokenToCompletedRunResults.get(token));
							}
							
						} else
						{
							//System.err.println("Kill wasn't requested for:" + runConfig);
						}
						
					}
					
				}
			}
		}
}
}
