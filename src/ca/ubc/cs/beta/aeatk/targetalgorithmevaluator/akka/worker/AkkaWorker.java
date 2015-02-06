package ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.worker;

import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.lang.management.ManagementFactory;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import scala.concurrent.duration.FiniteDuration;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Inbox;
import akka.actor.Props;
import ca.ubc.cs.beta.aeatk.algorithmrunresult.AlgorithmRunResult;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.TargetAlgorithmEvaluator;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.TargetAlgorithmEvaluatorRunObserver;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.ProcessRunMessage;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.master.MasterWatchDogActor;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.messages.ContactMaster;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.messages.LocalRunUpdateMessage;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.messages.ProcessRunCompletedMessage;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.messages.ShutdownMessage;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.decorators.functionality.SimulatedDelayTargetAlgorithmEvaluatorDecorator;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.init.TargetAlgorithmEvaluatorLoader;

import com.typesafe.config.ConfigFactory;

public class AkkaWorker {

	private static final Logger log = LoggerFactory.getLogger(AkkaWorker.class);
	
	public static void main(String[] args)
	{
		
		String configuration = "akka {\n"+
				"log-dead-letters-during-shutdown = false\n"+
				"  actor {\n"+
				"    provider = \"akka.remote.RemoteActorRefProvider\"\n"+
				"  }\n"+
				""+
				"  remote {\n"+
				"    netty.tcp {\n"+
				"      hostname = \"127.0.0.1\"\n"+
				"      port = 0\n"+
				"    }\n"+
				"  }\n"+
				""+
				"}\n";
		
		

		ActorSystem system = ActorSystem.create("AKKA-TAE-Actor",ConfigFactory.parseString(configuration));
		
		try 
		{
			final AtomicReference<ProcessRunMessage> pRunToKill = new AtomicReference<ProcessRunMessage>();
			
			
		
			final ActorRef workerActor = system.actorOf(Props.create(AkkaWorkerActor.class, pRunToKill), "worker-" + ManagementFactory.getRuntimeMXBean().getName());
			
			final Inbox inbox = Inbox.create(system);
			
			
			
			inbox.send(workerActor, new ContactMaster("127.0.0.1","2552"));
			
			
			TargetAlgorithmEvaluator tae = TargetAlgorithmEvaluatorLoader.getTargetAlgorithmEvaluator("PARAMECHO", TargetAlgorithmEvaluatorLoader.getAvailableTargetAlgorithmEvaluators());
			tae = new SimulatedDelayTargetAlgorithmEvaluatorDecorator(tae, 1, 1);
			
			
			while(true)
			{
				Object o = inbox.receive(new FiniteDuration(24, TimeUnit.HOURS));
				
				if(o instanceof ProcessRunMessage) 
				{
					ProcessRunMessage prun = (ProcessRunMessage) o; 
					
					TargetAlgorithmEvaluatorRunObserver obs = new TargetAlgorithmEvaluatorRunObserver()
					{

						@Override
						public void currentStatus(List<? extends AlgorithmRunResult> runs) {
							
							if(runs.size()  != 1)
							{
								log.error("Expected only one run would be in progress at any time, not {} ", runs.size());
							} else
							{
								inbox.send(workerActor, new LocalRunUpdateMessage(runs.get(0)));
							}
							
							if(pRunToKill.get() != null)
							{
								if(runs.get(0).getAlgorithmRunConfiguration().equals(pRunToKill.get().getAlgorithmRunConfiguration()))
								{
									runs.get(0).kill();
								}
							}
						}
					};
					
					
					AlgorithmRunResult runResult = tae.evaluateRun(Collections.singletonList(prun.getAlgorithmRunConfiguration()), obs).get(0);
					
					
					inbox.send(workerActor, new ProcessRunCompletedMessage(prun, runResult));
				} else if (o instanceof ShutdownMessage)
				{
					break;
				}
			}
			
			tae.close();
			
			log.info("Worker terminated");
			
		} finally
		{
			system.shutdown();
		}
		
		
	}
}
