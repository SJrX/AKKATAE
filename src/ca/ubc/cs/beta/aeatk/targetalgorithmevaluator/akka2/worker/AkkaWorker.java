package ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka2.worker;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.lang.management.ManagementFactory;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import scala.concurrent.duration.FiniteDuration;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Inbox;
import akka.actor.Props;
import akka.contrib.pattern.ClusterSingletonProxy;
import ca.ubc.cs.beta.aeatk.algorithmrunconfiguration.AlgorithmRunConfiguration;
import ca.ubc.cs.beta.aeatk.algorithmrunresult.AlgorithmRunResult;
import ca.ubc.cs.beta.aeatk.algorithmrunresult.ExistingAlgorithmRunResult;
import ca.ubc.cs.beta.aeatk.algorithmrunresult.RunStatus;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.TargetAlgorithmEvaluator;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.TargetAlgorithmEvaluatorRunObserver;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka2.actors.aeatk.TAEWorkerActor;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka2.executors.AkkaWorkerExecutor;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka2.messages.AlgorithmRunStatus;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka2.messages.KillLocalRun;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka2.messages.RequestRunConfigurationUpdate;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka2.options.AkkaWorkerOptions;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka2.tae.AkkaTargetAlgorithmEvaluator;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.exceptions.TargetAlgorithmAbortException;

public class AkkaWorker {

	
	private Logger log = LoggerFactory.getLogger(getClass());
	
	/**
	 * @param opts
	 * @param tae
	 * @param system
	 * @param execService
	 */
	public void executeWorker(AkkaWorkerOptions opts, TargetAlgorithmEvaluator tae, ActorSystem system,	ExecutorService execService, ActorRef singleton) {
		try
		{






		Inbox workerThread = Inbox.create(system);
		final Inbox observerThread = Inbox.create(system);
		
		
		
		 


		
		
		final ActorRef backend = system.actorOf(Props.create(TAEWorkerActor.class, workerThread.getRef(), observerThread.getRef(),singleton, opts), "frontend");

		
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
		
		log.info("Worker awaiting work");
		
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
			execService.shutdownNow();
		}
	}
}