package ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka2.executors;

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
import ca.ubc.cs.beta.aeatk.options.AbstractOptions;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.TargetAlgorithmEvaluator;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.TargetAlgorithmEvaluatorRunObserver;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka2.actors.aeatk.TAEWorkerCoordinator;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka2.actors.aeatk.TAEWorkerActor;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka2.actors.cluster.ClusterManagerActor;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka2.helper.AkkaHelper;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka2.messages.AlgorithmRunStatus;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka2.messages.KillLocalRun;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka2.messages.RequestRunConfigurationUpdate;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka2.messages.WhereAreYou;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka2.options.AkkaClusterOptions;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka2.options.AkkaWorkerOptions;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka2.tae.AkkaTargetAlgorithmEvaluator;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka2.tae.AkkaTargetAlgorithmEvaluatorFactory;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka2.tae.AkkaTargetAlgorithmEvaluatorOptions;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka2.worker.AkkaWorker;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.exceptions.TargetAlgorithmAbortException;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.init.TargetAlgorithmEvaluatorBuilder;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.init.TargetAlgorithmEvaluatorLoader;


public class AkkaWorkerExecutor {

	public static Logger log = null;
	
	public static void main(String[] args)
	{
		
		try
		{
			AkkaWorkerOptions opts = new AkkaWorkerOptions();
			
			
			Map<String, AbstractOptions> taeOpts = TargetAlgorithmEvaluatorLoader.getAvailableTargetAlgorithmEvaluators();
			
			
			try {
				System.out.println(Arrays.toString(args));
				JCommanderHelper.parseCheckingForHelpAndVersion(args, opts, taeOpts);
			} finally
			{
				//opts.log.initializeLogging();
				log = LoggerFactory.getLogger(AkkaWorkerExecutor.class);
			}
			
			
			TargetAlgorithmEvaluator tae = opts.taeOptions.getTargetAlgorithmEvaluator(taeOpts);
			
			
			
			
			
		
			AkkaClusterOptions akkaClustOptions = ((AkkaTargetAlgorithmEvaluatorOptions)taeOpts.get(AkkaTargetAlgorithmEvaluatorFactory.getTAEName())).akkaClusterOptions;
				
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
			

			ActorRef singletonProxyManager = system.actorOf(ClusterSingletonManager.defaultProps(Props.create(TAEWorkerCoordinator.class), "coordinator", "END", null),"singleton");
		

		
			ActorRef singleton = system.actorOf(ClusterSingletonProxy.defaultProps("/user/singleton/coordinator",null),"coordinatoryProxy");
			
			ExecutorService execService = Executors.newSingleThreadExecutor(new SequentiallyNamedThreadFactory("Observer Inbox Monitor", true));
			
			
			ActorRef clusterNode = system.actorOf(Props.create(ClusterManagerActor.class), "clusterManager");
			
			
			
			try 
			{
				AkkaWorker worker = new AkkaWorker();
				
				worker.executeWorker(opts, tae, system, execService, singleton);
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
