package ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka2.actors.aeatk;

import java.io.Serializable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import scala.concurrent.duration.FiniteDuration;
import ca.ubc.cs.beta.aeatk.algorithmrunconfiguration.AlgorithmRunConfiguration;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka2.messages.AllAlgorithmRunsDispatched;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka2.messages.RequestRunConfigurationUpdate;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka2.messages.UpdateObservationStatus;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka2.messages.AlgorithmRunBatchCompleted;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka2.messages.RequestRunBatch;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka2.messages.RequestWorkers;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedActor;
import akka.japi.Creator;

/**
 * Actor which the TAE talks to and processes runs
 * 
 *  Note: This TAE keeps the number of child actors to a managable amount to prevent flooding the singleton.
 *  
 *  
 * @author Steve Ramage <seramage@cs.ubc.ca>
 *
 */
public class TAEBridgeActor extends UntypedActor {

	/**
	 * Outstanding requests
	 */
	public ArrayDeque<RequestRunBatch> nonStartedRequests = new ArrayDeque<>();
	
	public int activeAndWaitingJobs = 0; 
	public int permittedActiveAndWaiting = 1; 
	
	private final ActorRef coordinator;
	
	private final Map<UUID, ActorRef> requestsToManagingActorMap = new HashMap<>();
	
	private final Logger log = LoggerFactory.getLogger(getClass());
	
	private long observerFrequencyInMS;
	
	private final Map<UUID, Map<AlgorithmRunConfiguration,RequestRunConfigurationUpdate>> runsToKill = new HashMap<>();
	public TAEBridgeActor(ActorRef coordinator, int observerFrequencyInMS) {
		this.coordinator = coordinator;
		this.observerFrequencyInMS = observerFrequencyInMS;
		
		if(observerFrequencyInMS <= 0)
		{
			throw new IllegalStateException("Observer frequency must be greater than zero");
		}
	}
	
	@Override
	public void preStart()
	{
		
		this.context().system().scheduler().schedule(FiniteDuration.Zero(), new FiniteDuration(observerFrequencyInMS, TimeUnit.MILLISECONDS), new RequestUpdateObserver(), this.context().system().dispatcher());
	}



	public static Props props(final ActorRef coordinator, final int observerFrequency)
	{
		return Props.create(new Creator<TAEBridgeActor>(){

			@Override
			public TAEBridgeActor create() throws Exception
			{
				return new TAEBridgeActor(coordinator, observerFrequency);
			}
			
		});
	}
	
	

	@Override
	public void onReceive(Object arg0) throws Exception {
		
		if(arg0 instanceof RequestRunBatch)
		{ 
			/**
			 * Queue the request and potentially start it
			 * (Sent from TAE)
			 */
			
			RequestRunBatch rbo = (RequestRunBatch) arg0;
			nonStartedRequests.add(rbo);
			startProcessingRunBatch(); 
		} else if(arg0 instanceof AlgorithmRunBatchCompleted)
		{
			/**
			 * Clean up data structures
			 * (Sent from Managing Actor)
			 */
			AlgorithmRunBatchCompleted arbc = (AlgorithmRunBatchCompleted) arg0;
			
			this.requestsToManagingActorMap.remove(arbc.getUUID());
			this.runsToKill.remove(arbc.getUUID());
		} else if(arg0 instanceof RequestRunConfigurationUpdate)
		{
			/**
			 * Forward the request to the correct managing actor
			 * If it is a kill request then we will queue it for when the actor starts up.
			 * 
			 * A further operation could be that if we detect every run killed then we start it right away.
			 * (Sent from TAE)
			 */
			RequestRunConfigurationUpdate rrcu = (RequestRunConfigurationUpdate) arg0;
			
			ActorRef ar = this.requestsToManagingActorMap.get(rrcu.getUUID());
			
			if(ar != null)
			{
				ar.tell(rrcu, getSelf());
			} else if(rrcu.getKillStatus())
			{
				//Store the killStatus locally and replay it later.
				Map<AlgorithmRunConfiguration, RequestRunConfigurationUpdate> map = runsToKill.get(rrcu.getUUID());
				
				if(map == null)
				{
					runsToKill.put(rrcu.getUUID(), new HashMap<AlgorithmRunConfiguration, RequestRunConfigurationUpdate>());
					map = runsToKill.get(rrcu.getUUID());	
				} 
				
				map.put(rrcu.getAlgorithmRunConfiguration(), rrcu);
			}
		
		} else if(arg0 instanceof UpdateObserverStatus)
		{
			/**
			 * Triggers a request to all managing actors to get us an update
			 * (Sent from periodic task locally)
			 */
			for(Entry<UUID, ActorRef> ent : this.requestsToManagingActorMap.entrySet())
			{
				 ent.getValue().tell(new UpdateObservationStatus(ent.getKey()), getSelf());
			}
		} else if(arg0 instanceof AllAlgorithmRunsDispatched)
		{
			/**
			 * Tells us that we can potentially start the next batch, as a current batch no longer needs any workers
			 * (Sent from managing actor) 
			 */
			activeAndWaitingJobs--;
			startProcessingRunBatch(); 
		} else 
		{
			unhandled(arg0);
		}
		
		
	}



	/**
	 * 
	 */
	public void startProcessingRunBatch() {
		if(activeAndWaitingJobs < permittedActiveAndWaiting && nonStartedRequests.size() >= 1)
		{
			activeAndWaitingJobs++;
			RequestRunBatch rrb = nonStartedRequests.pop();
			
			log.debug("Starting execution of batch : {}", rrb.getUUID());
			
			ActorRef delegate = this.context().actorOf(Props.create(AlgorithmRunBatchMonitorActor.class, rrb, 1.0, coordinator));
			
			this.requestsToManagingActorMap.put(rrb.getUUID(), delegate);
			
			if(this.runsToKill.get(rrb.getUUID()) != null)
			{
				log.debug("Sending previous kill messages");
				for(RequestRunConfigurationUpdate rrcu : this.runsToKill.get(rrb.getUUID()).values())
				{
					delegate.tell(rrcu, getSelf());
				}
				
				this.runsToKill.remove(rrb.getUUID());
			}
			
			
			
		}
	}

	
	
	private class RequestUpdateObserver implements Runnable
	{
		
		private final UpdateObserverStatus message = new UpdateObserverStatus();

		@Override
		public void run() {
			TAEBridgeActor.this.getSelf().tell(message, getSelf());
		}
		
	}
	
	private class UpdateObserverStatus implements Serializable
	{
		
	}
	
	
	
	
}
