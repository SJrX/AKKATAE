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
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka2.messages.AlgorithmRunStatus;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka2.messages.KillLocalRun;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka2.messages.RequestRunConfigurationUpdate;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka2.messages.WhereAreYou;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka2.options.AkkaWorkerOptions;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka2.tae.AkkaTargetAlgorithmEvaluator;
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
			
			
			
			
				
		
			
				
			String configuration ="akka {\n" + 
					"  loglevel = \"WARNING\"\n" +
					"  actor {\n" + 
					"    provider = \"akka.cluster.ClusterActorRefProvider\"\n" + 
					"  }\n" + 
					"  remote {\n" + 
					"    log-remote-lifecycle-events = off\n" + 
					"    netty.tcp {\n" + 
					"      hostname = \"127.0.0.1\"\n" + 
					"      port = 0\n" + 
					"    }\n" + 
					"  }\n" + 
					"\n" + 
					"  cluster {\n" + 
					"    seed-nodes = [\n" + 
					"      \"akka.tcp://ClusterSystem@127.0.0.1:61001\"]\n" + 
					//,\n" + 
					//"      \"akka.tcp://ClusterSystem@127.0.0.1:61001\"]\n" + 
					"\n" + 
					"    auto-down-unreachable-after = 10s\n" + 
					"	  jmx.enabled = " + (opts.clustOpts.jmxEnabled ? "on" : "off") + "\n"+ 
					"	  gossip-interval = "+opts.clustOpts.gossipInterval + " ms\n"+
					"	  leader-actions-interval = "+ opts.clustOpts.leaderActionInterval+" ms\n"+
					"	  unreachable-nodes-reaper-interval = " +opts.clustOpts.unreachableNodesReaperInterval+" ms\n"+
					"	  periodic-tasks-initial-delay = "+ opts.clustOpts.periodicTasksInitialDelay + " ms\n"+
					"	  failure-detector.heartbeat-interval = "+opts.clustOpts.failureDetectorHeartbeatInterval+" ms\n"+
					"     auto-down-unreachable-after = " +opts.clustOpts.autoDownUnreachableAfter+ " ms\n" + 
					"  }\n" + 
					"}\n";
				
				
				
				
				
			final Config config = ConfigFactory.parseString("akka.remote.netty.tcp.port=" + (opts.clustOpts.id + 61000)).
				      withFallback(ConfigFactory.parseString(configuration));
				      
			ActorSystem system = ActorSystem.create("ClusterSystem", config);
			ExecutorService execService = Executors.newSingleThreadExecutor(new SequentiallyNamedThreadFactory("Observer Inbox Monitor", true));
			
			
			
			try {
				
			
			
			
			 			//ActorRef singleton = system.actorOf(ClusterSingletonManager.defaultProps(Props.create(TAEWorkerCoordinator.class), "coordinator", "END", null),"singleton");
			ActorRef singletonProxyManager = system.actorOf(ClusterSingletonManager.defaultProps(Props.create(TAEWorkerCoordinator.class), "coordinator", "END", null),"singleton");
			
			Inbox workerThread = Inbox.create(system);
			final Inbox observerThread = Inbox.create(system);
			
			
		
			 
			
			ActorRef clusterNode = system.actorOf(Props.create(ClusterManagerActor.class), "clusterManager");
			
			ActorRef singleton = system.actorOf(ClusterSingletonProxy.defaultProps("/user/singleton/coordinator",null),"coordinatoryProxy");
			
			final ActorRef backend = system.actorOf(Props.create(TAEWorkerActor.class, workerThread.getRef(), observerThread.getRef(),singleton), "frontend");
		
			Inbox inbox = Inbox.create(system);
			
			final AtomicReference<AlgorithmRunConfiguration> runsToKill = new AtomicReference<>();
			
			
			execService.execute(new Runnable(){

				@Override
				public void run() {
					
					
					while(true)
					{
						Object o = observerThread.receive(new FiniteDuration(30, TimeUnit.DAYS));
						
						if(o instanceof KillLocalRun)
						{
							runsToKill.set(((KillLocalRun) o).getAlgorithmRunConfiguration());
						}

					}
					
				}
				
			});
			
			while(true)
			{
				
				Object t = workerThread.receive(new FiniteDuration(30, TimeUnit.DAYS));
				
				if(t instanceof RequestRunConfigurationUpdate)
				{
					
					final RequestRunConfigurationUpdate rrcu = (RequestRunConfigurationUpdate) t;
					final AlgorithmRunConfiguration rc = rrcu.getAlgorithmRunConfiguration();
					
					
					runsToKill.set(null);
					TargetAlgorithmEvaluatorRunObserver runObserver = new TargetAlgorithmEvaluatorRunObserver()
					{

						@Override
						public void currentStatus(List<? extends AlgorithmRunResult> runs) {
							
							
							AlgorithmRunResult run = runs.get(0);
							
							
							if(run.getAlgorithmRunConfiguration().equals(runsToKill.get()))
							{	
								
								log.warn("kill() called");
								run.kill();
							}
							
							if(!run.isRunCompleted())
							{
								//Only send uncompleted runs, as we can't distinguish between
								//completion 
								observerThread.send(backend, new AlgorithmRunStatus(run,rrcu.getUUID()));
							}
						}
					};
					
					try 
					{
						//log.debug("Starting processing of run");
						AlgorithmRunResult result = tae.evaluateRun(Collections.singletonList(rc), runObserver).get(0);
						//log.debug("Processing of run completed");
						observerThread.send(backend, new AlgorithmRunStatus(result,rrcu.getUUID()));	
					} catch(TargetAlgorithmAbortException e)
					{
						
						AlgorithmRunResult result = new ExistingAlgorithmRunResult((AlgorithmRunConfiguration) rc, RunStatus.ABORT, 0, 0, 0, 0, "Aborted on Worker: " + ManagementFactory.getRuntimeMXBean().getName() +"; " + e.getMessage());
						observerThread.send(backend, new AlgorithmRunStatus(result, rrcu.getUUID()));
					} catch(RuntimeException e)
					{
						

						ByteArrayOutputStream bout = new ByteArrayOutputStream();
						
						try (PrintWriter pWriter = new PrintWriter(bout))
						{
							e.printStackTrace(pWriter);
						}
									
						
						String addlRunData;
						try {
							addlRunData = AkkaTargetAlgorithmEvaluator.ADDITIONAL_RUN_DATA_ENCODED_EXCEPTION_PREFIX + bout.toString("UTF-8").replaceAll("[\\n]", " ; ");
							
						} catch (UnsupportedEncodingException e1) {
							addlRunData = "Unsupported Encoding Exception Occurred while writing Exception in " + AkkaWorkerExecutor.class.getCanonicalName() + " nested exception was:" + e.getClass().getSimpleName();
							e1.printStackTrace();
						}
						
						
						
						AlgorithmRunResult result = new ExistingAlgorithmRunResult((AlgorithmRunConfiguration) rc, RunStatus.ABORT, 0, 0, 0, 0, addlRunData);
						observerThread.send(backend, new AlgorithmRunStatus(result, rrcu.getUUID()));
					}
				} else
				{
					throw new IllegalStateException("Recieved unknown message from worker actor");
					
				}
			
				
			}
			
			} finally
			{
				system.shutdown();
				execService.shutdownNow();
			}
			
			
		} catch(ParameterException e)
		{
			log.error("Error occurred ", e);
		}
	}
}
