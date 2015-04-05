package ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.actors.aeatk;

import java.lang.management.ManagementFactory;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import scala.concurrent.duration.FiniteDuration;
import ca.ubc.cs.beta.aeatk.algorithmrunconfiguration.AlgorithmRunConfiguration;
import ca.ubc.cs.beta.aeatk.algorithmrunresult.AlgorithmRunResult;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.messages.AlgorithmRunProcessingFailed;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.messages.AlgorithmRunStatus;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.messages.RejectedExecution;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.messages.RequestRunConfigurationUpdate;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.messages.ShutdownMessage;
import akka.actor.ActorRef;
import akka.actor.Cancellable;
import akka.actor.Props;
import akka.actor.Terminated;
import akka.actor.UntypedActor;
import akka.japi.Creator;

/**
 * Given a RunConfiguration and a worker this Actor monitors it
 * @author Steve Ramage <seramage@cs.ubc.ca>
 *
 */
public class AlgorithmRunMonitorActor extends UntypedActor {
	
	private final AlgorithmRunConfiguration rc;
	private final ActorRef worker;
	private final AtomicBoolean kill = new AtomicBoolean(false);
	private final UUID uuid ;
	
	private boolean successful;

	
	private final Logger log = LoggerFactory.getLogger(getClass());
	//private final int observerFrequency;
	
	public static Props props(final AlgorithmRunConfiguration rc, final ActorRef worker,final UUID uuid)
	{
		return Props.create(new Creator<AlgorithmRunMonitorActor>(){

			@Override
			public AlgorithmRunMonitorActor create() throws Exception {

				return new AlgorithmRunMonitorActor(rc, worker,uuid);
			}
			
		});
	}
	
	public AlgorithmRunMonitorActor(AlgorithmRunConfiguration rc, ActorRef worker, UUID uuid)
	{
		this.rc = rc;
		this.worker = worker;
		this.uuid = uuid;
		//this.observerFrequency = observerFrequency;
	}
	
	@Override
	public void preStart()
	{
		log.debug("Notifying worker to start processing run for {} and seed : {}  " ,uuid, rc.getProblemInstanceSeedPair().getSeed());
		worker.tell(new RequestRunConfigurationUpdate(rc,false, uuid), getSelf());
		context().watch(worker);
		
	}
	
	
	public void postStop()
	{
		context().unwatch(worker);
	}
	@Override
	public void onReceive(Object arg0) throws Exception {
		
		/**
		 * Let the parent actor stop this child
		 */
		if(arg0 instanceof Terminated)
		{
			if(!successful)
			{
				
				
				//log.warn("Worker Rejected Execution, aborted");
				context().parent().tell(new AlgorithmRunProcessingFailed(rc), getSelf());
			}
			//this.context().stop(getSelf());
		} else if(arg0 instanceof RejectedExecution )
		{
			//log.warn("Worker Rejected Execution, aborted");
			if(!successful)
			{
				context().parent().tell(new AlgorithmRunProcessingFailed(rc), getSelf());
			}
			//this.context().stop(getSelf());
		}  else if(arg0 instanceof RequestRunConfigurationUpdate)
		{
			log.debug("Request Run Configuration Update for UUID {} and seed {}", uuid, rc.getProblemInstanceSeedPair().getSeed());
			
			//No race condition here because this is single threaded
			kill.set(((RequestRunConfigurationUpdate) arg0).getKillStatus() || kill.get());
			
			
			
			worker.tell(new RequestRunConfigurationUpdate(rc, kill.get(), uuid), getSelf());
		} else if(arg0 instanceof AlgorithmRunStatus)
		{
			AlgorithmRunStatus status = (AlgorithmRunStatus) arg0;
			
			
			
			context().parent().tell(status, getSelf());
			
			
			if(status.getAlgorithmRunResult().isRunCompleted())
			{
				successful = true;
			}
			
		} else if(arg0 instanceof ShutdownMessage)
		{
			worker.tell(new RequestRunConfigurationUpdate(rc, true, uuid), getSelf());
			context().stop(getSelf());
		} else
		{
			unhandled(arg0);
		}
		
	}
}
