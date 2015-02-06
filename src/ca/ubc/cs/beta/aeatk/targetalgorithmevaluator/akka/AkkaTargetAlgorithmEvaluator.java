package ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka;



import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.typesafe.config.ConfigFactory;

import scala.concurrent.duration.FiniteDuration;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Inbox;
import akka.actor.Props;
import ca.ubc.cs.beta.aeatk.algorithmrunconfiguration.AlgorithmRunConfiguration;
import ca.ubc.cs.beta.aeatk.algorithmrunresult.AlgorithmRunResult;
import ca.ubc.cs.beta.aeatk.algorithmrunresult.ExistingAlgorithmRunResult;
import ca.ubc.cs.beta.aeatk.algorithmrunresult.RunningAlgorithmRunResult;
import ca.ubc.cs.beta.aeatk.algorithmrunresult.kill.StatusVariableKillHandler;
import ca.ubc.cs.beta.aeatk.concurrent.threadfactory.SequentiallyNamedThreadFactory;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.AbstractAsyncTargetAlgorithmEvaluator;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.TargetAlgorithmEvaluatorCallback;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.TargetAlgorithmEvaluatorRunObserver;

import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.master.MasterWatchDogActor;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.messages.ObserverUpdateResponse;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.messages.ProcessRunCompletedMessage;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.messages.RequestObserverUpdateMessage;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.messages.ShutdownMessage;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.messages.UpdateObservationStatus;

/**
 * Target Algorithm Evaluator Implementation that uses the Akka actor system 
 * @author Steve Ramage <seramage@cs.ubc.ca>
 *
 */
public class AkkaTargetAlgorithmEvaluator extends AbstractAsyncTargetAlgorithmEvaluator {


	


	private final AkkaTargetAlgorithmEvaluatorOptions opts;

	private final ActorSystem system;
	
	private final ActorRef masterWatchDog; 
	
	private final Inbox inbox;
	
	private final Inbox observerInbox;
	
	
	private final Inbox shutdownInbox;
	
	private final AtomicBoolean stopProcessingInbox = new AtomicBoolean(false);
	
	private final CountDownLatch inboxProcessingThreadDone = new CountDownLatch(1);
	
	
	private final ConcurrentHashMap<SubmitToken, CallerContext> tokenToCallerContextMap = new ConcurrentHashMap<>();
	
	/**
	 * For every submit token, stores the set of completed run configurations
	 * 
	 */
	private final ConcurrentHashMap<SubmitToken, Map<AlgorithmRunConfiguration, AlgorithmRunResult>> tokenToCompletedRunResults = new ConcurrentHashMap<>();
	
	
	/**
	 * For every submit token, stores the set of outstanding run configurations
	 * 
	 * Entries are created on evaluateRunAsync()
	 * Entries in the set are removed when we get a ProcessRunCompleted message, the key is removed when we dispatch the caller.
	 */
	private final ConcurrentHashMap<SubmitToken, Set<AlgorithmRunConfiguration>> tokenToOutstandingRunConfigsSet = new ConcurrentHashMap<>();
	
	
	private final ConcurrentHashMap<SubmitToken, Map<AlgorithmRunConfiguration, AlgorithmRunResult>> tokenToObserverRunResultMap = new ConcurrentHashMap<>();
	
	
	private final ConcurrentHashMap<SubmitToken, AtomicLong> lastObserverNotificationTime = new ConcurrentHashMap<>();
	
	private final Logger log = LoggerFactory.getLogger(getClass());
	
	private final ExecutorService receivingThreadPool = Executors.newSingleThreadExecutor(new SequentiallyNamedThreadFactory("AKKA TAE Response Processing Thread", false));
	
	private final ExecutorService callbackThreadPool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 2, new SequentiallyNamedThreadFactory("AKKA TAE Callback Processing Thread", false));
	
	private final ExecutorService observerThreadPool = Executors.newCachedThreadPool( new SequentiallyNamedThreadFactory("AKKA TAE Observer Processing Thread", false));
	
	private final ScheduledExecutorService ses = Executors.newScheduledThreadPool(1, new SequentiallyNamedThreadFactory("AKKA TAE Observation Scheduling Thread", false));
	
	private final BlockingQueue<SubmitToken> callbacksToFire = new LinkedBlockingQueue<SubmitToken>();
	
	private final BlockingQueue<SubmitToken> observerToFire = new LinkedBlockingQueue<SubmitToken>();
	
	public AkkaTargetAlgorithmEvaluator(AkkaTargetAlgorithmEvaluatorOptions options)
	{
		this.opts = options;
		String configuration = "akka {\n"+
				"log-dead-letters-during-shutdown = off\n"+
				"  actor {\n"+
				"    provider = \"akka.remote.RemoteActorRefProvider\"\n"+
				"  }\n"+
				""+
				"  remote {\n"+
				"    netty.tcp {\n"+
				"      hostname = \"127.0.0.1\"\n"+
				"      port = 2552\n"+
				"    }\n"+
				"  }\n"+
				""+
				"}\n";
		
		
		
		
		system = ActorSystem.create("AKKA-TAE-Master", ConfigFactory.parseString(configuration));
		
		
		masterWatchDog = system.actorOf(Props.create(MasterWatchDogActor.class), "master");
		
		inbox = Inbox.create(system);
		
		observerInbox = Inbox.create(system);
		
		shutdownInbox = Inbox.create(system);
		
		
		receivingThreadPool.execute( new ProcessRunMessageCompletionHandler());
		
		ses.scheduleAtFixedRate(new UpdateObservationStatusRequestingRunnable(), 2, 2, TimeUnit.SECONDS);
		
		for(int i=0; i < ManagementFactory.getOperatingSystemMXBean().getAvailableProcessors(); i++)
		{
			observerThreadPool.execute(new TargetAlgorithmEvaluatorObserverNotificationRunnable());
		}
		
		observerThreadPool.execute(new ObserverMailboxReader());
		
		
		for(int i=0; i < ManagementFactory.getOperatingSystemMXBean().getAvailableProcessors(); i++)
		{
			callbackThreadPool.execute(new TargetAlgorithmEvaluatorCallbackNotificationRunnable());
		}
		
	}


	@Override
	/***
	 * Sets up available data structures and then notifies the master watch dog of all runs to do.
	 */
	public void evaluateRunsAsync(List<AlgorithmRunConfiguration> runConfigs, TargetAlgorithmEvaluatorCallback taeCallback,	TargetAlgorithmEvaluatorRunObserver runStatusObserver) {
		
		SubmitToken token = new SubmitToken();
		CallerContext ctx = new CallerContext(runConfigs, taeCallback, runStatusObserver);
		tokenToCallerContextMap.put(token, ctx);
		tokenToOutstandingRunConfigsSet.put(token, Collections.newSetFromMap(new ConcurrentHashMap<AlgorithmRunConfiguration, Boolean>()));
		
		ConcurrentHashMap<AlgorithmRunConfiguration, AlgorithmRunResult> observerMap = new ConcurrentHashMap<AlgorithmRunConfiguration, AlgorithmRunResult>();
		tokenToObserverRunResultMap.put(token, observerMap);
		
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
		
		this.lastObserverNotificationTime.put(token, new AtomicLong(System.currentTimeMillis()));
		
		tokenToOutstandingRunConfigsSet.get(token).addAll(runConfigs);

		tokenToCompletedRunResults.put(token, new ConcurrentHashMap<AlgorithmRunConfiguration, AlgorithmRunResult>());
		
		for(AlgorithmRunConfiguration rc : runConfigs)
		{
			inbox.send(masterWatchDog, new ProcessRunMessage(token, rc));
		}
		
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
	public void notifyShutdown() {
	
		if(!this.stopProcessingInbox.compareAndSet(false, true))
		{
			return;
		}
		
		ses.shutdownNow();
		
		shutdownInbox.send(masterWatchDog, new ShutdownMessage());
	
		String response = shutdownInbox.receive(new FiniteDuration(1, TimeUnit.DAYS)).toString();
		
		log.info("Shutting down Inbox Processing Thread");
		try {
			this.inboxProcessingThreadDone.await();
		} catch (InterruptedException e1) {
			Thread.currentThread().interrupt();
		}
		
		log.info("Inbox Processing Thread Terminated");
		system.shutdown();
		receivingThreadPool.shutdown();
		log.info("Waiting for System Thread Termination");
		system.awaitTermination();
		
		log.info("Waiting for Recieving Thread Termination");
		receivingThreadPool.shutdownNow();
		
		try {
			receivingThreadPool.awaitTermination(24,TimeUnit.DAYS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return;
		}
	
		
		log.info("AKKA Target Algorithm Evaluator Shutdown");
	}
	
	
	
	private final class ObserverMailboxReader implements Runnable {
		public void run()
		{
			Thread.currentThread().setName("AKKA Observer Mailbox Reader");
			while(true)
			{
				Object o = observerInbox.receive(new FiniteDuration(28, TimeUnit.DAYS));
				
				
				if(o instanceof ObserverUpdateResponse)
				{
					ObserverUpdateResponse our = (ObserverUpdateResponse) o;
					//System.err.println("Got Observation: " + ((ObserverUpdateResponse) o).getAlgorithmRunResult());
					
					
					AlgorithmRunResult result = our.getAlgorithmRunResult();
					
					
					if(!result.isRunCompleted())
					{
						result = new RunningAlgorithmRunResult(result.getAlgorithmRunConfiguration(), result.getRuntime(), result.getRunLength(), result.getQuality(), result.getResultSeed(), result.getWallclockExecutionTime(), tokenToCallerContextMap.get(our.getProcessRun().getSubmitToken()).getKillHandler(result.getAlgorithmRunConfiguration()));
					}
					tokenToObserverRunResultMap.get(our.getProcessRun().getSubmitToken()).put(our.getAlgorithmRunResult().getAlgorithmRunConfiguration(), result);
					
					
					AkkaTargetAlgorithmEvaluator.this.observerToFire.add(our.getProcessRun().getSubmitToken());
				} else
				{
					log.error("Got unknown message on observer inbox: {}" ,o);
				}
			}
		}
	}



	private final class UpdateObservationStatusRequestingRunnable implements
			Runnable {
		@Override
		public void run() {
			observerInbox.send(masterWatchDog, new UpdateObservationStatus());
		}
	}



	private final class ProcessRunMessageCompletionHandler implements Runnable {
		@Override
		public void run() {
			
			try 
			{
				while(!Thread.interrupted() && !stopProcessingInbox.get())
				{
					Object o = null;
				
					try {
						try {
							o = inbox.receive(new FiniteDuration(1, TimeUnit.DAYS));
						} finally
						{
							//System.err.println("Done recieve");
						}
					} catch(Throwable e)
					{
						System.out.println("Error: " + e);
						throw e;
					}
					if(o == null)
					{
						continue;
					}
					
					
					
					if(o instanceof ProcessRunCompletedMessage)
					{
						ProcessRunCompletedMessage prc = (ProcessRunCompletedMessage) o;
						Set<AlgorithmRunConfiguration> outstandingRunConfigurations = tokenToOutstandingRunConfigsSet.get(prc.getProcessRun().getSubmitToken());
						
						if(outstandingRunConfigurations != null)
						{
							outstandingRunConfigurations.remove(prc.getAlgorithmRunResult().getAlgorithmRunConfiguration());
							
							tokenToCompletedRunResults.get(prc.getProcessRun().getSubmitToken()).put(prc.getAlgorithmRunResult().getAlgorithmRunConfiguration(),prc.getAlgorithmRunResult());
							tokenToObserverRunResultMap.get(prc.getProcessRun().getSubmitToken()).put(prc.getAlgorithmRunResult().getAlgorithmRunConfiguration(),prc.getAlgorithmRunResult());
							
							
							if(outstandingRunConfigurations.size() == 0)
							{
								log.info("Notifying callback");
								tokenToOutstandingRunConfigsSet.remove(prc.getProcessRun().getSubmitToken());
								callbacksToFire.add(prc.getProcessRun().getSubmitToken());
							} else
							{
								observerToFire.add(prc.getProcessRun().getSubmitToken());
								log.trace("CHP Left: {}, {}", outstandingRunConfigurations.size(), prc.getAlgorithmRunResult());
							}
						}
						

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
					SubmitToken token = callbacksToFire.take();
					synchronized (token)
					{
						try
						{
							CallerContext callContext = tokenToCallerContextMap.remove(token);
							
						
							if(callContext != null)
							{
								log.debug("Notifying callback for token : {} " ,token);
								TargetAlgorithmEvaluatorCallback taeCallback = callContext.getTargetAlgorithmEvaluatorCallback();
								
								List<AlgorithmRunResult> runs = new ArrayList<AlgorithmRunResult>(callContext.getAlgorithmRunConfigurations().size());
								
								
								for(AlgorithmRunConfiguration runConfig : callContext.getAlgorithmRunConfigurations())
								{
									runs.add(tokenToCompletedRunResults.get(token).get(runConfig));
								}
							
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
						} finally
						{
							//Clean up data structures
							tokenToCallerContextMap.remove(token);
							tokenToCompletedRunResults.remove(token);
							tokenToOutstandingRunConfigsSet.remove(token);
							tokenToObserverRunResultMap.remove(token);
							lastObserverNotificationTime.remove(token);
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
				SubmitToken token;
				try {
					token = observerToFire.take();
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					return;
				}
				
				synchronized(token)
				{
					CallerContext callContext = tokenToCallerContextMap.get(token);
					
					if(callContext == null)
					{ //Callback already fired
						continue;
					}
					
					if(System.currentTimeMillis() - lastObserverNotificationTime.get(token).get() > 250)
					{
						lastObserverNotificationTime.get(token).set(System.currentTimeMillis());
					} else
					{
						continue;
					}
					
					List<AlgorithmRunResult> runResult = new ArrayList<AlgorithmRunResult>(callContext.getAlgorithmRunConfigurations().size());
					
					for(AlgorithmRunConfiguration runConfig : callContext.getAlgorithmRunConfigurations())
					{
						
						AlgorithmRunResult resultToAdd =tokenToObserverRunResultMap.get(token).get(runConfig);
						
						if(!tokenToOutstandingRunConfigsSet.get(token).contains(runConfig))
						{ //The run is done, and further more by this point, we have a completed run
							resultToAdd = tokenToCompletedRunResults.get(token).get(runConfig);
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
							
							if(tokenToCompletedRunResults.get(token).get(runConfig) == null)
							{
								System.err.println("Sending kill to: " + runConfig);
								Object message = new RequestObserverUpdateMessage( new ProcessRunMessage(token, runConfig), true);
								inbox.send(masterWatchDog, message);
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
