package ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka2.actors.aeatk;

import java.io.Serializable;
import java.lang.management.ManagementFactory;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import scala.concurrent.duration.FiniteDuration;
import ca.ubc.cs.beta.aeatk.algorithmrunconfiguration.AlgorithmRunConfiguration;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka2.messages.AllAlgorithmRunsDispatched;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka2.messages.DumpDebugInformation;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka2.messages.RequestRunConfigurationUpdate;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka2.messages.UpdateObservationStatus;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka2.messages.AlgorithmRunBatchCompleted;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka2.messages.RequestRunBatch;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka2.messages.RequestWorkers;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka2.messages.WorkerAvailable;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka2.messages.WorkerPermit;
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
	
	//public int activeAndWaitingJobs = 0; 
	public int permittedActiveAndWaiting = 1; 
	//public int active = 0;
	
	
	public Set<UUID> activeUUIDs = new TreeSet<UUID>();
	public Set<UUID> activeAndWaitingUUIDs = new TreeSet<UUID>();
	
	private final ActorRef coordinator;
	
	private final Map<UUID, ActorRef> requestsToManagingActorMap = new HashMap<>();
	
	private final Logger log = LoggerFactory.getLogger(getClass());
	
	private long observerFrequencyInMS;
	
	private final Map<UUID, Map<AlgorithmRunConfiguration,RequestRunConfigurationUpdate>> runsToKill = new HashMap<>();

	private int debugPrintStatusFrequencyInMS;
	public TAEBridgeActor(ActorRef coordinator, int observerFrequencyInMS, int debugPrintStatusFrequencyInMS) {
		this.coordinator = coordinator;
		this.observerFrequencyInMS = observerFrequencyInMS;
		
		this.debugPrintStatusFrequencyInMS = debugPrintStatusFrequencyInMS;
		
		if(observerFrequencyInMS <= 0)
		{
			throw new IllegalStateException("Observer frequency must be greater than zero");
		}
	}
	
	@Override
	public void preStart()
	{
		
		this.context().system().scheduler().schedule(FiniteDuration.Zero(), new FiniteDuration(observerFrequencyInMS, TimeUnit.MILLISECONDS), new RequestUpdateObserver(), this.context().system().dispatcher());
		
		if(debugPrintStatusFrequencyInMS > 0)
		{
			this.context().system().scheduler().schedule(FiniteDuration.Zero(), new FiniteDuration(debugPrintStatusFrequencyInMS, TimeUnit.MILLISECONDS), new RequestDebugInfo(), this.context().system().dispatcher());
		}
		
		
	}



	public static Props props(final ActorRef coordinator, final int observerFrequency, final int debugPrintStatusFrequencyInMS)
	{
		return Props.create(new Creator<TAEBridgeActor>(){

			@Override
			public TAEBridgeActor create() throws Exception
			{
				return new TAEBridgeActor(coordinator, observerFrequency, debugPrintStatusFrequencyInMS);
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
			
			activeUUIDs.remove(arbc.getUUID());
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
			activeAndWaitingUUIDs.remove(((AllAlgorithmRunsDispatched) arg0).getUUID());
			startProcessingRunBatch(); 
		} else if(arg0 instanceof WorkerPermit)
		{
		
			
			WorkerPermit wp = (WorkerPermit) arg0;
			
			
			
			
			ActorRef child = requestsToManagingActorMap.get(wp.getUUID());
			
			if(child != null)
			{
				//Always tell the child if it's there
				log.info("Recieved worker permit for {} and sending to child " , wp.getUUID());
				child.tell(wp, getSender());
			}
			
			
			if(!activeAndWaitingUUIDs.contains(wp.getUUID()))
			{
				log.info("Child no longer active, assigning to someone else who may want it");
				
				if(activeAndWaitingUUIDs.size() > 0)
				{
					for(UUID uuid : activeAndWaitingUUIDs)
					{
						ActorRef waitingChild = requestsToManagingActorMap.get(uuid);
						
						log.info("Recieved worker permit for {} and telling {} " , wp.getUUID(), uuid);
						waitingChild.tell(wp, getSender());
					}
				} else
				{
					log.info("No children need work, sending back to coordinator");
					coordinator.tell(new WorkerAvailable(wp.getWorker(), wp.getWorkerName()), getSelf());
				}
			}
			
			
			
			
			
			
			
			
			
		}else if(arg0 instanceof DumpDebugInformation)
		{
			dumpDebugInformation();	
			
			Set<ActorRef> children = new HashSet<ActorRef>();
			
			for(ActorRef ar : getContext().getChildren())
			{
				children.add(ar);
			}
			
			children.addAll(this.requestsToManagingActorMap.values());
			
			
			for(ActorRef child: children)
			{
				child.tell(arg0, getSelf());
			}
				
		} else 
		{
			unhandled(arg0);
		}
		
		
	}



	private void dumpDebugInformation() {
		StringBuilder sb = new StringBuilder();
		
		sb.append("========[" + getClass().getSimpleName() + "]========\n");
		sb.append(" Hostname:" + ManagementFactory.getRuntimeMXBean().getName() + "\n");
		sb.append(" Active And Waiting: " + activeAndWaitingUUIDs.size() + ", Permitted Active And Waiting: " + permittedActiveAndWaiting + ", Active: " + activeUUIDs.size() + "\n");
		sb.append(" Non Started Requests: " + nonStartedRequests.size() + "\n");
		int i=0;
		

		
		
		
		for(ActorRef ent : this.getContext().getChildren())
		{
			i++;
		}
		
		sb.append(" Requests to Managing Actor Map Size: " + requestsToManagingActorMap.size() + ", Number of children: "+ i + "\n" );
		
		
		for(UUID uuid : activeUUIDs)
		{
			sb.append(" UUID: " + uuid + " [Waiting: " + (activeAndWaitingUUIDs.contains(uuid) ? "Yes" : "No") + "]\n");
		}
		
		if(!activeUUIDs.containsAll(activeAndWaitingUUIDs))
		sb.append(" WARNING: datastructure corruption detected " + activeAndWaitingUUIDs + " should be a subset of " + activeUUIDs);
		System.out.println(sb);
		
	}

	/**
	 * 
	 */
	public void startProcessingRunBatch() {
		if(activeAndWaitingUUIDs.size() < permittedActiveAndWaiting && nonStartedRequests.size() >= 1)
		{
			
			RequestRunBatch rrb = nonStartedRequests.pop();
			
			log.debug("Starting execution of batch : {}", rrb.getUUID());
			
			ActorRef delegate = this.context().actorOf(Props.create(AlgorithmRunBatchMonitorActor.class, rrb, 1.0, coordinator));
			
			this.requestsToManagingActorMap.put(rrb.getUUID(), delegate);
			
			this.activeUUIDs.add(rrb.getUUID());
			this.activeAndWaitingUUIDs.add(rrb.getUUID());
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

		
		private int i=0; 
		@Override
		public void run() {
			TAEBridgeActor.this.getSelf().tell(message, getSelf());
			
			
		}
		
	}
	
	private class RequestDebugInfo implements Runnable
	{
		
		
		private final DumpDebugInformation debugInfo = new DumpDebugInformation();
		
		
		
		private int i=0; 
		@Override
		public void run() {
			TAEBridgeActor.this.getSelf().tell(debugInfo, getSelf());
		}
		
	}
	
	
	private class UpdateObserverStatus implements Serializable
	{
		
	}
	
	
	
	
}
