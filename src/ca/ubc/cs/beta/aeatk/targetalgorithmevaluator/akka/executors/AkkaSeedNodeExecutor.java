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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import scala.concurrent.duration.FiniteDuration;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Inbox;
import akka.actor.Props;
import akka.contrib.pattern.ClusterSingletonManager;
import akka.contrib.pattern.ClusterSingletonProxy;
import ca.ubc.cs.beta.aeatk.algorithmrunconfiguration.AlgorithmRunConfiguration;
import ca.ubc.cs.beta.aeatk.algorithmrunresult.AlgorithmRunResult;
import ca.ubc.cs.beta.aeatk.algorithmrunresult.ExistingAlgorithmRunResult;
import ca.ubc.cs.beta.aeatk.algorithmrunresult.RunStatus;
import ca.ubc.cs.beta.aeatk.concurrent.threadfactory.SequentiallyNamedThreadFactory;
import ca.ubc.cs.beta.aeatk.misc.jcommander.JCommanderHelper;
import ca.ubc.cs.beta.aeatk.options.AbstractOptions;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.TargetAlgorithmEvaluator;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.TargetAlgorithmEvaluatorRunObserver;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.actors.aeatk.TAEWorkerActor;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.actors.aeatk.TAEWorkerCoordinator;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.actors.cluster.ClusterManagerActor;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.helper.AkkaHelper;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.messages.AlgorithmRunStatus;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.messages.KillLocalRun;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.messages.RequestRunConfigurationUpdate;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.options.AkkaClusterOptions;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.options.AkkaSeedNodeOptions;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.options.AkkaWorkerOptions;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.tae.AkkaTargetAlgorithmEvaluator;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.tae.AkkaTargetAlgorithmEvaluatorFactory;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.tae.AkkaTargetAlgorithmEvaluatorOptions;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.exceptions.TargetAlgorithmAbortException;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.init.TargetAlgorithmEvaluatorLoader;

import com.beust.jcommander.ParameterException;

public class AkkaSeedNodeExecutor {

	public static Logger log;
	
	public static void main(String[] args)
	{
		try
		{
			AkkaSeedNodeOptions opts = new AkkaSeedNodeOptions();
			
		
			try {
			
				JCommanderHelper.parseCheckingForHelpAndVersion(args, opts);
			} finally
			{
				//opts.log.initializeLogging();
				log = LoggerFactory.getLogger(AkkaWorkerExecutor.class);
			}
			
			AkkaClusterOptions akkaClustOptions = opts.clusterOpts;
			
		
			String configuration ="akka {\n" + 
					"  loglevel = \"WARNING\"\n" +
					"  actor {\n" + 
					"    provider = \"akka.cluster.ClusterActorRefProvider\"\n" + 
					"  }\n" + 
					"  remote {\n" + 
					"    log-remote-lifecycle-events = off\n" + 
					"  }\n" + 
					"\n" + 
					"  cluster {\n" +  
					"    auto-down-unreachable-after = 10s\n" + 
					"	  jmx.enabled = " + (akkaClustOptions.jmxEnabled ? "on" : "off") + "\n"+ 
					"	  gossip-interval = "+akkaClustOptions.gossipInterval + " ms\n"+
					"	  leader-actions-interval = "+ akkaClustOptions.leaderActionInterval+" ms\n"+
					"	  unreachable-nodes-reaper-interval = " +akkaClustOptions.unreachableNodesReaperInterval+" ms\n"+
					"	  periodic-tasks-initial-delay = "+ akkaClustOptions.periodicTasksInitialDelay + " ms\n"+
					"	  failure-detector.heartbeat-interval = "+akkaClustOptions.failureDetectorHeartbeatInterval+" ms\n"+
					"     auto-down-unreachable-after = " +akkaClustOptions.autoDownUnreachableAfter+ " ms\n" + 
					"  }\n" + 
					"}\n";
				
				
			ActorSystem system = AkkaHelper.startAkkaSystem(akkaClustOptions.networks, opts.dir, configuration, akkaClustOptions.id);	
			
			
			
			try {
				
			
	
				ActorRef singletonProxyManager = system.actorOf(ClusterSingletonManager.defaultProps(Props.create(TAEWorkerCoordinator.class), "coordinator", "END", null),"singleton");
				
	
				
				ActorRef clusterNode = system.actorOf(Props.create(ClusterManagerActor.class), "clusterManager");
				
				
				try {
					Thread.sleep(1024*1024*1024);
				} catch (InterruptedException e) {
					e.printStackTrace();
					return;
				}
				
			} finally
			{
				system.shutdown();
			}
			
			
		} catch(ParameterException e)
		{
			log.error("Error occurred ", e);
		}
		}
	
}
