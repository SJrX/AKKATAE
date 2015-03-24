package ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.executors;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import scala.concurrent.duration.FiniteDuration;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Inbox;
import akka.actor.Props;
import akka.cluster.Cluster;
import akka.contrib.pattern.ClusterSingletonManager;
import akka.contrib.pattern.ClusterSingletonProxy;

import com.beust.jcommander.ParameterException;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import ca.ubc.cs.beta.aeatk.algorithmrunconfiguration.AlgorithmRunConfiguration;
import ca.ubc.cs.beta.aeatk.algorithmrunresult.AlgorithmRunResult;
import ca.ubc.cs.beta.aeatk.algorithmrunresult.ExistingAlgorithmRunResult;
import ca.ubc.cs.beta.aeatk.algorithmrunresult.RunStatus;
import ca.ubc.cs.beta.aeatk.concurrent.threadfactory.SequentiallyNamedThreadFactory;
import ca.ubc.cs.beta.aeatk.misc.jcommander.JCommanderHelper;
import ca.ubc.cs.beta.aeatk.misc.returnvalues.AEATKReturnValues;
import ca.ubc.cs.beta.aeatk.options.AbstractOptions;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.TargetAlgorithmEvaluator;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.TargetAlgorithmEvaluatorRunObserver;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.actors.aeatk.TAEWorkerActor;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.actors.aeatk.TAEWorkerCoordinator;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.actors.cluster.ClusterManagerActor;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.helper.AkkaHelper;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.messages.AlgorithmRunStatus;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.messages.RequestRunConfigurationUpdate;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.messages.WhereAreYou;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.messages.worker.KillLocalRun;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.options.AkkaClusterOptions;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.options.AkkaWorkerOptions;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.tae.AkkaTargetAlgorithmEvaluator;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.tae.AkkaTargetAlgorithmEvaluatorFactory;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.tae.AkkaTargetAlgorithmEvaluatorOptions;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.worker.AkkaWorker;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.exceptions.TargetAlgorithmAbortException;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.init.TargetAlgorithmEvaluatorBuilder;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.init.TargetAlgorithmEvaluatorLoader;


public class AkkaWorkerExecutor {

	public static Logger log = null;
	
	public static void main(String[] args)
	{
		
		try
		{
			final AkkaWorkerOptions opts = new AkkaWorkerOptions();
			
			
			
			Map<String, AbstractOptions> taeOpts = TargetAlgorithmEvaluatorLoader.getAvailableTargetAlgorithmEvaluators();
			
			
			try {
			
				JCommanderHelper.parseCheckingForHelpAndVersion(args, opts, taeOpts);
			} finally
			{
				opts.log.initializeLogging();
				log = LoggerFactory.getLogger(AkkaWorkerExecutor.class);
			}
			
			
			try(TargetAlgorithmEvaluator tae = opts.taeOptions.getTargetAlgorithmEvaluator(taeOpts))
			{
			
				
				
				
				String logLevel;
				
				switch(opts.log.logLevel)
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
					logLevel = opts.log.logLevel.name();
					break;
					
				default:
					throw new IllegalStateException("Unknown log level: " + opts.log.logLevel);
				
				}
			
				AkkaClusterOptions akkaClustOptions = ((AkkaTargetAlgorithmEvaluatorOptions)taeOpts.get(AkkaTargetAlgorithmEvaluatorFactory.getTAEName())).akkaClusterOptions;
					
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
						"    roles = [worker]\n" +
						"    auto-down-unreachable-after = 10s\n" + 
						"	  jmx.enabled = " + (akkaClustOptions.jmxEnabled ? "on" : "off") + "\n"+ 
						"	  gossip-interval = "+akkaClustOptions.gossipInterval + " ms\n"+
						"	  leader-actions-interval = "+ akkaClustOptions.leaderActionInterval+" ms\n"+
						"	  unreachable-nodes-reaper-interval = " +akkaClustOptions.unreachableNodesReaperInterval+" ms\n"+
						"	  periodic-tasks-initial-delay = "+ akkaClustOptions.periodicTasksInitialDelay + " ms\n"+
						"	  failure-detector.heartbeat-interval = "+akkaClustOptions.failureDetectorHeartbeatInterval+" ms\n"+
						"     failure-detector.acceptable-heartbeat-pause = " + akkaClustOptions.failureDetectorAcceptablePause +" ms\n"+
						"     auto-down-unreachable-after = " +akkaClustOptions.autoDownUnreachableAfter+ " ms\n" + 
						"  }\n" + 
						"}\n";
					
					
				ActorSystem system = AkkaHelper.startAkkaSystem(akkaClustOptions.networks, opts.dir, configuration, akkaClustOptions.id);	
				
				
	
				ActorRef singletonProxyManager = system.actorOf(ClusterSingletonManager.defaultProps(Props.create(TAEWorkerCoordinator.class), "coordinator", "END", null),"singleton");
			
	
			
				ActorRef singleton = system.actorOf(ClusterSingletonProxy.defaultProps("/user/singleton/coordinator",null),"coordinatoryProxy");
				
				ExecutorService execService = Executors.newSingleThreadExecutor(new SequentiallyNamedThreadFactory("Observer Inbox Monitor", true));
				
				ScheduledExecutorService ses = Executors.newScheduledThreadPool(3, new SequentiallyNamedThreadFactory("Timeout Limits",true));
				
				
				final Thread currentThread = Thread.currentThread();
				final AtomicBoolean timeoutReached = new AtomicBoolean(false);
				ses.schedule(new Runnable() {

					@Override
					public void run() {
						log.warn("Timelimit has been reached, terminating worker");
						currentThread.interrupt();
						timeoutReached.set(true);
						
					}
				
				}, opts.timeLimit, TimeUnit.SECONDS);
				
				
				ses.schedule(new Runnable()
				{

					@Override
					public void run() {
						
						log.warn("Worker has not shutdown even though the timelimit expired 30 minutes ago, maybe misbehaving TAE?.");
						currentThread.interrupt();
						timeoutReached.set(true);
						Runtime.getRuntime().exit(AEATKReturnValues.DEADLOCK_DETECTED);
						
						
					}
					
				}, Math.max(opts.timeLimit + 1800, Integer.MAX_VALUE), TimeUnit.SECONDS);
				
				
				final AtomicLong lastTimestamp = new AtomicLong(System.currentTimeMillis());
				
				ses.scheduleAtFixedRate(new Runnable(){

					@Override
					public void run() {
						
						if(lastTimestamp.get() + opts.idleLimit < System.currentTimeMillis() + 10)
						{
							log.warn("Worker has been idle for too long, shutting down");
							currentThread.interrupt();
							timeoutReached.set(true);
						}
					}
					
				}, opts.idleLimit, Math.max(opts.idleLimit/10, 15) , TimeUnit.SECONDS);
				
				
				ActorRef clusterNode = system.actorOf(Props.create(ClusterManagerActor.class), "clusterManager");
				
				
				
				try 
				{
					AkkaWorker worker = new AkkaWorker();
					Semaphore noopSemaphore = new Semaphore(1);
					worker.executeWorker(opts, tae, system, execService, singleton, noopSemaphore, timeoutReached, lastTimestamp);
					
				} finally
				{
					execService.shutdownNow();
					system.shutdown();
					ses.shutdown();
				}
			}
				
			
		} catch(ParameterException e)
		{
			log.error("Error occurred ", e);
		}
	}

	
}
