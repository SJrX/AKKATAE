package ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka2.actors.aeatk;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.collections.map.LRUMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import scala.concurrent.duration.FiniteDuration;
import ca.ubc.cs.beta.aeatk.algorithmrunconfiguration.AlgorithmRunConfiguration;
import ca.ubc.cs.beta.aeatk.algorithmrunresult.AlgorithmRunResult;
import ca.ubc.cs.beta.aeatk.algorithmrunresult.RunningAlgorithmRunResult;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka2.messages.AlgorithmRunStatus;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka2.messages.KillLocalRun;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka2.messages.RejectedExecution;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka2.messages.RequestRunConfigurationUpdate;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka2.messages.WorkerAvailable;
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
	
	private final AtomicBoolean doingWork = new AtomicBoolean(false);

	private ActorRef coordinator;
	
	private Runnable pollCoordinator;
	
	private final Logger log = LoggerFactory.getLogger(getClass());
	
	
	private final int NUMBER_OF_ADDITIONAL_NOTIFICATIONS = 3; //Maybe make this an option one day lol, I don't that will happen.
	//
	/**
	 * We will notify the owner an additional number of times if this is set.
	 */
	private int numberOfAdditionalNotificationsLeft = 0;
	
	public TAEWorkerActor(ActorRef workerThreadInbox, ActorRef observerThreadInbox, ActorRef coordinator)
	{
		this.workerThreadInbox = workerThreadInbox;
		this.observerThreadInbox = observerThreadInbox;
		this.coordinator = coordinator;
	}
	
	@Override
	public void preStart()
	{
		
		pollCoordinator = new PollCoordinatorWithFreeWorker(doingWork, getSelf(), coordinator);
		this.context().system().scheduler().schedule(new FiniteDuration(0, TimeUnit.SECONDS), new FiniteDuration(15, TimeUnit.SECONDS), pollCoordinator, this.context().system().dispatcher());
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
			} else if(currentRequest == null)
			{
				currentRequest = rrcu;
			
				workerThreadInbox.tell(rrcu, getSelf());
				//log.warn("Starting run for {}, seed: {} ", rrcu.getUUID() , rrcu.getAlgorithmRunConfiguration().getProblemInstanceSeedPair().getSeed());
				watchingActor = getSender();
				latestStatus = new RunningAlgorithmRunResult(rrcu.getAlgorithmRunConfiguration(), 0, 0, 0, (long) 0, 0, null);
				doingWork.set(true);
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
				//log.warn("Rejected execution for {}, seed: {}",((RequestRunConfigurationUpdate) arg0).getUUID(), ((RequestRunConfigurationUpdate) arg0).getAlgorithmRunConfiguration().getProblemInstanceSeedPair().getSeed());
				getSender().tell(new RejectedExecution(((RequestRunConfigurationUpdate) arg0).getAlgorithmRunConfiguration()), getSelf());
			}
		} else if (arg0 instanceof AlgorithmRunStatus)
		{
			
		
			doingWork.set(false);
			latestStatus = ((AlgorithmRunStatus) arg0).getAlgorithmRunResult();
			
			
			if(latestStatus.isRunCompleted())
			{
				pollCoordinator.run();
				completedRequests.put(currentRequest, latestStatus);
				currentRequest = null;
				//log.warn("Worker done request");
			}
			
			if(numberOfAdditionalNotificationsLeft-- > 0)
			{
				watchingActor.tell(arg0, getSelf());
			}
			
			
		} else
		{
			unhandled(arg0);
		}
		
	}

	private static final class PollCoordinatorWithFreeWorker implements Runnable
	{
		
		private final AtomicBoolean doingWork;
		private final ActorRef sender;
		private final ActorRef coordinator;
		private final WorkerAvailable wa;
		
		public PollCoordinatorWithFreeWorker(AtomicBoolean doingWork, ActorRef sender, ActorRef coordinator)
		{
			this.doingWork = doingWork;
			this.sender = sender;
			this.coordinator = coordinator;
			this.wa = new WorkerAvailable(sender);
		}
		
		@Override
		public void run()
		{
			if(!this.doingWork.get())
			{
				this.coordinator.tell(wa, sender);
			}
		}
	}
}
