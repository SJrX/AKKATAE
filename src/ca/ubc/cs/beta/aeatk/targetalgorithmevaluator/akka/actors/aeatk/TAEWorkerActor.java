package ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.actors.aeatk;

import java.io.Serializable;
import java.lang.management.ManagementFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.collections.map.LRUMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import scala.concurrent.duration.FiniteDuration;
import ca.ubc.cs.beta.aeatk.algorithmrunresult.AlgorithmRunResult;
import ca.ubc.cs.beta.aeatk.algorithmrunresult.RunningAlgorithmRunResult;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.messages.AlgorithmRunStatus;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.messages.RejectedExecution;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.messages.RequestRunConfigurationUpdate;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.messages.ShutdownMessage;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.messages.WorkerAvailable;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.messages.worker.KillLocalRun;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.messages.worker.SynchronousWorkerAvailable;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.messages.worker.SynchronousWorkerUnavailable;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.options.AkkaWorkerOptions;
import akka.actor.ActorRef;
import akka.actor.UntypedActor;

public class TAEWorkerActor extends UntypedActor {

	
	
	

	//private AlgorithmRunConfiguration currentRunConfiguration;
	
	private RequestRunConfigurationUpdate currentRequest;
	
	private final ActorRef workerThreadInbox;

	private final ActorRef observerThreadInbox;
	
	private AlgorithmRunResult latestStatus;
	
	//Stores the last 25 runs
	private final LRUMap completedRequests = new LRUMap(25);
	
	private ActorRef watchingActor = null;
	
	//private final AtomicBoolean doingWork = new AtomicBoolean(false);

	private ActorRef coordinator;

	private final Logger log = LoggerFactory.getLogger(getClass());
	
	private final int NUMBER_OF_ADDITIONAL_NOTIFICATIONS; //Maybe make this an option one day lol, I don't that will happen.
	//
	/**
	 * We will notify the owner an additional number of times if this is set.
	 */
	private int numberOfAdditionalNotificationsLeft = 0;

	private AkkaWorkerOptions opts;
	
	private boolean workerAvailable = true;
	
	
	private final WorkerAvailable waMessage ;
	
	public TAEWorkerActor(ActorRef workerThreadInbox, ActorRef observerThreadInbox, ActorRef coordinator, AkkaWorkerOptions opts)
	{
		this.workerThreadInbox = workerThreadInbox;
		this.observerThreadInbox = observerThreadInbox;
		this.coordinator = coordinator;
		this.opts = opts;
		this.NUMBER_OF_ADDITIONAL_NOTIFICATIONS = opts.additionalNotifications;
		this.waMessage = new WorkerAvailable(this.getSelf(), ManagementFactory.getRuntimeMXBean().getName());
	}
	
	@Override
	public void preStart()
	{
		
		
		this.context().system().scheduler().schedule(new FiniteDuration(0, TimeUnit.SECONDS), new FiniteDuration(opts.workerPollAvailability, TimeUnit.SECONDS), new PollCoordinatorWithFreeWorker( getSelf()), this.context().system().dispatcher());
	}
	
	@Override
	public void onReceive(Object arg0) throws Exception {
		
		if(arg0 instanceof RequestRunConfigurationUpdate)
		{
			numberOfAdditionalNotificationsLeft = NUMBER_OF_ADDITIONAL_NOTIFICATIONS;

			RequestRunConfigurationUpdate rrcu = (RequestRunConfigurationUpdate) arg0;
			if(completedRequests.get(rrcu) != null)
			{
				getSender().tell(new AlgorithmRunStatus((AlgorithmRunResult) completedRequests.get(rrcu), rrcu.getUUID()), getSelf());
			} else if(currentRequest == null && workerAvailable)
			{
				currentRequest = rrcu;
			
				workerThreadInbox.tell(rrcu, getSelf());
				log.debug("Starting run for {}, seed: {} ", rrcu.getUUID() , rrcu.getAlgorithmRunConfiguration().getProblemInstanceSeedPair().getSeed());
				watchingActor = getSender();
				latestStatus = new RunningAlgorithmRunResult(rrcu.getAlgorithmRunConfiguration(), 0, 0, 0, (long) 0, 0, null);
			} else if(currentRequest.equals(rrcu))
			{
				
				getSender().tell(new AlgorithmRunStatus(latestStatus, currentRequest.getUUID()), getSelf());
				
				
				if(rrcu.getKillStatus())
				{
					
					//log.warn("Killing the run");
					try {
						//Technically the AEATK API discourages calling kill() outside currentStatus()
						//But oh well this might improve performance
						
						//The other thing that might happen is that this request might be ignored so we will tell the observer thread anyway.
						latestStatus.kill();
					} catch(RuntimeException e)
					{
						//Who cares if this happens.
					}
				
					observerThreadInbox.tell(new KillLocalRun(rrcu.getAlgorithmRunConfiguration()),getSender());
				}
				
				
			} else
			{
				log.debug("Rejected execution for:  {} ", ((RequestRunConfigurationUpdate) arg0).getUUID());
				//log.warn("Rejected execution for {}, seed: {}",((RequestRunConfigurationUpdate) arg0).getUUID(), ((RequestRunConfigurationUpdate) arg0).getAlgorithmRunConfiguration().getProblemInstanceSeedPair().getSeed());
				getSender().tell(new RejectedExecution(((RequestRunConfigurationUpdate) arg0).getAlgorithmRunConfiguration()), getSelf());
			}
		} else if (arg0 instanceof AlgorithmRunStatus)
		{
			
		
		
			latestStatus = ((AlgorithmRunStatus) arg0).getAlgorithmRunResult();
			
			
			if(numberOfAdditionalNotificationsLeft-- > 0 || latestStatus.isRunCompleted())
			{
				watchingActor.tell(arg0, getSelf());
			}
			
			if(latestStatus.isRunCompleted())
			{
				
				this.coordinator.tell(waMessage, getSelf());
				completedRequests.put(currentRequest, latestStatus);
				currentRequest = null;
				watchingActor = null;
				log.trace("Worker done request, marking available");
			}
			
			
			
			
		} else if(arg0 instanceof SynchronousWorkerAvailable)
		{
			workerAvailable = true;
		} else if(arg0 instanceof SynchronousWorkerUnavailable)
		{
			workerAvailable = false;
			
			if(currentRequest != null)
			{
				log.debug("Worker unavailable rejecting execution for:  {} ", currentRequest.getUUID());
				
				
				watchingActor.tell(new RejectedExecution(currentRequest.getAlgorithmRunConfiguration()), getSelf());
				
				currentRequest = null;
				watchingActor = null;
			}
		} else if(arg0 instanceof Poll)
		{
			if(currentRequest == null)
			{
				this.coordinator.tell(waMessage, getSelf());
				log.debug("Notifying that worker is available: {}", waMessage.getWorkerName());
			}
		} else if(arg0 instanceof ShutdownMessage)
		{
		 workerThreadInbox.tell(arg0, getSelf());
		 
		 if(currentRequest != null)
		 {
			 observerThreadInbox.tell(new KillLocalRun(currentRequest.getAlgorithmRunConfiguration()),getSender());
		 }
		
		 context().stop(getSelf());
		}else
		{
			unhandled(arg0);
		}
		
	}

	private static final class PollCoordinatorWithFreeWorker implements Runnable
	{
		
		private final ActorRef sender;
		
		private final Poll poll = new Poll();
		public PollCoordinatorWithFreeWorker( ActorRef sender)
		{
			this.sender = sender;
		}
		
		@Override
		public void run()
		{
			sender.tell(poll, sender);
		}
	}
	
	private static class Poll implements Serializable
	{
		
	}
}
