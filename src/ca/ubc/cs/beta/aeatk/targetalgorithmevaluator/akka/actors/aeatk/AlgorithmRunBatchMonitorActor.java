package ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.actors.aeatk;

import java.io.Serializable;
import java.lang.management.ManagementFactory;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import scala.concurrent.duration.Duration;
import ca.ubc.cs.beta.aeatk.algorithmrunconfiguration.AlgorithmRunConfiguration;
import ca.ubc.cs.beta.aeatk.algorithmrunresult.AlgorithmRunResult;
import ca.ubc.cs.beta.aeatk.algorithmrunresult.ExistingAlgorithmRunResult;
import ca.ubc.cs.beta.aeatk.algorithmrunresult.RunStatus;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.messages.AlgorithmRunBatchCompleted;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.messages.AlgorithmRunProcessingFailed;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.messages.AlgorithmRunStatus;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.messages.AllAlgorithmRunsDispatched;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.messages.RequestRunBatch;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.messages.RequestRunConfigurationUpdate;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.messages.RequestWorkers;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.messages.ShutdownMessage;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.messages.WorkerAvailable;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.messages.WorkerPermit;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.messages.tae.DumpDebugInformation;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.messages.tae.UpdateObservationStatus;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Cancellable;
import akka.actor.Props;
import akka.actor.UntypedActor;
import akka.japi.Creator;

/**
 * Monitors a specific batch of runs
 * @author Steve Ramage <seramage@cs.ubc.ca>
 *
 */
public class AlgorithmRunBatchMonitorActor extends UntypedActor {

	private final ArrayDeque<AlgorithmRunConfiguration> runsToDo;
	
	private final Set<AlgorithmRunConfiguration> unstartedRunsToKill = new HashSet<AlgorithmRunConfiguration>();
	
	private final ActorRef coordinator;
	
	
	private Cancellable scheduledTask;
	//private final RequestWorkers request;

	private final Map<AlgorithmRunConfiguration, ActorRef> rcToChildActor = new HashMap<>(); 
	
	private final RequestRunBatch runRequests;
	
	private final Map<AlgorithmRunConfiguration, AlgorithmRunResult> completedRuns = new HashMap<>();
	

	private double priority;
	
	private final UUID uuid;
	
	private static final Logger log = LoggerFactory.getLogger(AlgorithmRunBatchMonitorActor.class);
	
	//private long lastTime = 0;
	public static Props props(final RequestRunBatch runRequests, final double priority, final ActorRef coordinator)
	{
		return Props.create(new Creator<AlgorithmRunBatchMonitorActor>(){

			@Override
			public AlgorithmRunBatchMonitorActor create() throws Exception {

				return new AlgorithmRunBatchMonitorActor(runRequests, priority, coordinator);
				
				
			}
			
		});
	}
	
	
	//private final Set<AlgorithmRunConfiguration> completedSuccessfully = new HashSet<AlgorithmRunConfiguration>();
	public AlgorithmRunBatchMonitorActor(RequestRunBatch runRequests, double priority, ActorRef coordinator)
	{
		this.runsToDo = new ArrayDeque<AlgorithmRunConfiguration>(runRequests.getAlgorithmRunConfigurations());
		this.runRequests = runRequests;
		this.coordinator = coordinator;
		//this.request =
		
		this.priority = priority;
		this.uuid = runRequests.getUUID();
		
	}
	
	
	public void preStart()
	{
		final Poll msg = new Poll();
		Runnable pollCoordinator = new Runnable()
		{

			@Override
			public void run() {
				getSelf().tell(msg, getSelf());
			}
			
		};
		scheduledTask = this.context().system().scheduler().schedule(Duration.create(0, TimeUnit.MILLISECONDS), Duration.create(15000, TimeUnit.MILLISECONDS),pollCoordinator, this.context().system().dispatcher());
	}
	
	@Override
	public void onReceive(Object arg0) throws Exception {
		
		//log.debug("{} Recieved message: {} ", uuid, arg0);
		if(arg0 instanceof WorkerPermit)
		{
			WorkerPermit wp = (WorkerPermit) arg0;
			
			log.debug("UUID {} got worker permit: {} ", uuid, wp.getWorkerName());
			if(runsToDo.size() > 0)
			{
				AlgorithmRunConfiguration rc = runsToDo.poll();
				
				
				while( rc != null && unstartedRunsToKill.contains(rc))
				{
					unstartedRunsToKill.remove(rc);
					
					AlgorithmRunResult result = new ExistingAlgorithmRunResult(rc, RunStatus.KILLED, 0, 0, 0, 0,"Killed premptively by " + getClass().getSimpleName(), 0);
					this.completedRuns.put(rc, result);
					AlgorithmRunStatus rs = new AlgorithmRunStatus(result, uuid);
					runRequests.getObserverRef().tell(rs, getSelf());
					runRequests.getCompletionRef().tell(rs, getSelf());
					
					rc = runsToDo.poll();
				}
				
				if(runsToDo.size() == 0)
				{
					log.debug("All runs dispatched for UUID : {}" , uuid);
					context().parent().tell(new AllAlgorithmRunsDispatched(this.runRequests.getUUID()), getSelf());
				}
				
				
				if(rc != null)
				{	
					ActorRef child = this.context().actorOf(Props.create(AlgorithmRunMonitorActor.class,rc , wp.getWorker(), uuid));
					this.rcToChildActor.put(rc, child);
					
					log.debug("UUID {} Started processing seed: {}", uuid, rc.getProblemInstanceSeedPair().getSeed());
					
					
				} else
				{
					

					shutdownActorIfDone();
					
					if(wp.getUUID().equals(uuid))
					{
						//Permit was for us and we don't need it
						log.debug("UUID: {} , Sending worker permit {} back to {} ", uuid, wp.getWorkerName(), this.coordinator);
						this.coordinator.tell(new WorkerAvailable(wp.getWorker(), wp.getWorkerName()), getSelf());
						requestWorkers(true);
					} else
					{
						log.debug("UUID: {} , We don't need it and worker isn't for us: {} ", uuid, wp.getWorkerName());
					}
					
					
					
					
				}
				


				
				//log.info("Assignment run configuration to children: {}", runsToDo.size());
			} else
			{
				//log.info("Don't need worker permit: {}", runsToDo.size());
				//We don't need it, the worker is available
				if(wp.getUUID().equals(uuid))
				{
					//Permit was for us and we don't need it
					log.debug("UUID: {} , Sending worker permit {} back to {} ", uuid, wp.getWorkerName(), this.coordinator);
					this.coordinator.tell(new WorkerAvailable(wp.getWorker(), wp.getWorkerName()), getSelf());
					requestWorkers(true);
				} else
				{
					log.debug("UUID: {} , We don't need it and worker isn't for us: {} ", uuid, wp.getWorkerName());
				}
				
				
			}
			
			
		} else if(arg0 instanceof AlgorithmRunProcessingFailed)
		{
		
			AlgorithmRunProcessingFailed arpf = (AlgorithmRunProcessingFailed) arg0;
			
			this.runsToDo.push(arpf.getAlgorithmRunConfiguration());
			
			rcToChildActor.remove(arpf.getAlgorithmRunConfiguration());
			/**
			 * Request worker
			 */
			
			requestWorkers(false);
			
			context().stop(getSender());
			
		} else if(arg0 instanceof AlgorithmRunStatus)
		{
			AlgorithmRunStatus status = (AlgorithmRunStatus) arg0;
			
			
			runRequests.getObserverRef().tell(arg0, getSelf());
			
			//log.warn("Notifying about: " + status.getAlgorithmRunResult().getResultLine()); 
			if(status.getAlgorithmRunResult().getRunStatus().equals(RunStatus.ABORT))
			{
				log.warn("The AKKA Target Algorithm Evaluator detected an ABORT but does not currently short circuit the evaluation of runs, the rest will continue executing");
				
			} 
			
			
			if(status.getAlgorithmRunResult().isRunCompleted())
			{
				
				this.completedRuns.put(status.getAlgorithmRunResult().getAlgorithmRunConfiguration(),status.getAlgorithmRunResult());
				
				rcToChildActor.remove(status.getAlgorithmRunResult().getAlgorithmRunConfiguration());
				context().stop(getSender());
				
			
				runRequests.getCompletionRef().tell(arg0, getSelf());

				
				shutdownActorIfDone();
				
				requestWorkers(true);
			}
		
			
		} else if(arg0 instanceof RequestRunConfigurationUpdate)
		{
			
			
			RequestRunConfigurationUpdate rrcu = (RequestRunConfigurationUpdate) arg0;
			
			
			if(this.completedRuns.containsKey(rrcu.getAlgorithmRunConfiguration()))
			{
				AlgorithmRunStatus rs = new AlgorithmRunStatus(this.completedRuns.get(rrcu.getAlgorithmRunConfiguration()), uuid);
				
				this.runRequests.getObserverRef().tell(rs, getSelf());
				this.runRequests.getCompletionRef().tell(rs, getSelf());
			} else
			{
				ActorRef child = this.rcToChildActor.get(((RequestRunConfigurationUpdate) arg0).getAlgorithmRunConfiguration());
	
				
				if(child != null)
				{
					child.tell(arg0, getSelf());
				}
				
				//Save this kill request in case the run is restarted
				if(rrcu.getKillStatus())
				{
					
					if(unstartedRunsToKill.add(rrcu.getAlgorithmRunConfiguration()))
					{
						log.debug("Unstarted run to kill for UUID: " + uuid);
						
						
					}
					
					
				}

			}
		}  else if (arg0 instanceof UpdateObservationStatus)
		{
			//log.info("Sending observation status");
			for(Entry<AlgorithmRunConfiguration, ActorRef> ent : this.rcToChildActor.entrySet())
			{
				ent.getValue().tell(new RequestRunConfigurationUpdate(ent.getKey(), false, uuid), getSelf());
			}
			
			for(AlgorithmRunResult result : this.completedRuns.values())
			{
				AlgorithmRunStatus rs = new AlgorithmRunStatus(result, uuid);
				
				this.runRequests.getObserverRef().tell(rs, getSelf());
				this.runRequests.getCompletionRef().tell(rs, getSelf());
			}
		} else if (arg0 instanceof DumpDebugInformation)
		{
			dumpDebugInformation();
			
		} else if (arg0 instanceof Poll)
		{
			requestWorkers(false);
		} else if(arg0 instanceof ShutdownMessage)
		{
			for(ActorRef ref : this.rcToChildActor.values())
			{
				ref.tell(arg0,getSelf());
			}
			
			runsToDo.clear();
			requestWorkers(true);
			
			context().stop(getSelf());
			
		} else
		{
			unhandled(arg0);
		}
		
	}


	/**
	 * 
	 */
	public void shutdownActorIfDone() {
		if(rcToChildActor.isEmpty() && runsToDo.size() == 0)
		{
			log.debug("We are done processing runs for {}", uuid);
			context().stop(getSelf());
			context().parent().tell(new AlgorithmRunBatchCompleted(uuid), getSelf());
		}
	}
	
	private void requestWorkers(boolean force)
	{
		if(runsToDo.size() > 0 || force)
		{
			log.debug("Requesting {} workers for UUID: {}", runsToDo.size(), uuid);
			RequestWorkers rw = new RequestWorkers(priority, runsToDo.size() , uuid, getContext().parent());
			this.coordinator.tell(rw, getContext().parent()); 
		}
	}
	
	private void dumpDebugInformation() {
		StringBuilder sb = new StringBuilder();
		sb.append("\t========[" + getClass().getSimpleName() + "]========\n");
		sb.append("\t UUID: " + this.runRequests.getUUID() + "\n");
		
		sb.append("\t Queued Runs: " + this.runsToDo.size() + ", Started Actor Runs:" + this.rcToChildActor.size() + ", Total Runs Needed: " + this.runRequests.getAlgorithmRunConfigurations().size() + "\n");
		sb.append("\t Completed Runs: " + this.completedRuns.size() + ",Priority:" + priority + ", Unstarted Runs To Kill: " + this.unstartedRunsToKill.size() + "\n");
		
		boolean halt = false;
		if(this.runsToDo.size() + this.rcToChildActor.size() + this.completedRuns.size() != this.runRequests.getAlgorithmRunConfigurations().size())
		{
			sb.append("\t WARNING: Data structure corruption detected\n");
			halt = true;
		}
		
		int i=0;

		for(ActorRef ent : this.getContext().getChildren())
		{
			i++;
		}

		sb.append("\t Number of Children: " + i);
		
		
		//System.out.println(sb);
		
		log.info("Current Status:\n{}",sb);
		
		if(halt)
		{
			Runtime.getRuntime().halt(25);
		}
	}


	public void postStop()
	{
		scheduledTask.cancel();
	}


	
	private class Poll implements Serializable
	{
		
	}

}
