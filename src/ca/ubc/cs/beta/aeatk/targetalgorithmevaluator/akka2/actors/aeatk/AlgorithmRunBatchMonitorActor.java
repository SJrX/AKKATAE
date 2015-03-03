package ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka2.actors.aeatk;

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
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka2.messages.AlgorithmRunBatchCompleted;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka2.messages.AlgorithmRunProcessingFailed;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka2.messages.AlgorithmRunStatus;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka2.messages.AllAlgorithmRunsDispatched;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka2.messages.RequestRunBatch;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka2.messages.RequestRunConfigurationUpdate;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka2.messages.RequestWorkers;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka2.messages.UpdateObservationStatus;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka2.messages.WorkerAvailable;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka2.messages.WorkerPermit;
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

	private final AtomicInteger outstandingRequests;

	private double priority;
	
	private final UUID uuid;
	
	private static final Logger log = LoggerFactory.getLogger(AlgorithmRunBatchMonitorActor.class);
	
	private Runnable pollCoordinator;
	
	private long lastTime = 0;
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
		this.outstandingRequests = new AtomicInteger(runsToDo.size());
	}
	
	
	public void preStart()
	{
		pollCoordinator = new PollCoordinator(this.getSelf(), coordinator, outstandingRequests, priority, uuid);
		scheduledTask = this.context().system().scheduler().schedule(Duration.create(0, TimeUnit.MILLISECONDS), Duration.create(15000, TimeUnit.MILLISECONDS),pollCoordinator, this.context().system().dispatcher());
	}
	
	@Override
	public void onReceive(Object arg0) throws Exception {
		
		//log.debug("Recieved message: {} ", arg0);
		if(arg0 instanceof WorkerPermit)
		{
			WorkerPermit wp = (WorkerPermit) arg0;
			
			
			if(runsToDo.size() > 0)
			{
				AlgorithmRunConfiguration rc = runsToDo.poll();
				
				
				
				
				while( rc != null && unstartedRunsToKill.contains(rc))
				{
					unstartedRunsToKill.remove(rc);
					
					
					AlgorithmRunStatus rs = new AlgorithmRunStatus(new ExistingAlgorithmRunResult(rc, RunStatus.KILLED, 0, 0, 0, 0,"Killed premptively by " + getClass().getSimpleName(), 0), uuid);
					runRequests.getObserverRef().tell(rs, getSelf());
					runRequests.getCompletionRef().tell(rs, getSelf());
					
					rc = runsToDo.poll();
				}
				
				
				if(rc != null)
				{	
					ActorRef child = this.context().actorOf(Props.create(AlgorithmRunMonitorActor.class,rc , wp.getWorker(), uuid));
					outstandingRequests.decrementAndGet();
					this.rcToChildActor.put(rc, child);
					
					if(runsToDo.size() == 0)
					{
						context().parent().tell(new AllAlgorithmRunsDispatched(), getSelf());
					}
				} else
				{
					getSender().tell(new WorkerAvailable(wp.getWorker()), getSelf());
					
					
				}
				//log.info("Assignment run configuration to children: {}", runsToDo.size());
			} else
			{
				//log.info("Don't need worker permit: {}", runsToDo.size());
				//We don't need it, the worker is available
				getSender().tell(new WorkerAvailable(wp.getWorker()), getSelf());
			}
			
			
		} else if(arg0 instanceof AlgorithmRunProcessingFailed)
		{
			
			this.runsToDo.push(((AlgorithmRunProcessingFailed) arg0).getAlgorithmRunConfiguration());
			
			//((AlgorithmRunProcessingFailed) arg0).getAlgorithmRunConfiguration().getProblemInstanceSeedPair()
			//log.info("Runs to do :  {}" ,  this.runsToDo.size() );
			outstandingRequests.addAndGet(1);
			
			pollCoordinator.run();
			
			context().stop(getSender());
			
		} else if(arg0 instanceof AlgorithmRunStatus)
		{
			AlgorithmRunStatus status = (AlgorithmRunStatus) arg0;
			
			
			runRequests.getObserverRef().tell(arg0, getSelf());
			
			//log.warn("Notifying about: " + status.getAlgorithmRunResult().getResultLine()); 
			if(status.getAlgorithmRunResult().getRunStatus().equals(RunStatus.ABORT))
			{
				/*
				outstandingRequests.addAndGet(-runsToDo.size());
				
				for(AlgorithmRunConfiguration rc : runsToDo)
				{
					
					AlgorithmRunResult result = new ExistingAlgorithmRunResult(rc, RunStatus.ABORT, 0,0,0,0,"Abort detected by other run, this run was terminated in AKKA Target Algorithm Evaluator before being dispatched");
					runRequests.getCompletionRef().tell(new AlgorithmRunStatus(result), getSelf());
				}
				runsToDo.clear();
				
				//log.warn("ABORT detected shutting everything down");
				for(Entry<AlgorithmRunConfiguration,ActorRef> ent : this.rcToChildActor.entrySet())
				{
					if(!ent.getValue().equals(getSender()))
					{
						//Tell everyone else to die
						ent.getValue().tell(new RequestRunConfigurationUpdate(ent.getKey(), true, uuid), getSelf());
					}
					
					
				}
				
				*/
				
			} 
			
			
			if(status.getAlgorithmRunResult().isRunCompleted())
			{
				
				rcToChildActor.remove(status.getAlgorithmRunResult().getAlgorithmRunConfiguration());
				context().stop(getSender());
				
				if(rcToChildActor.isEmpty() && runsToDo.size() == 0)
				{
					//We are done
					//log.debug("We are done all runs and shutting down");
					context().stop(getSelf());
					context().parent().tell(new AlgorithmRunBatchCompleted(uuid), getSelf());
					
					
				}
				runRequests.getCompletionRef().tell(arg0, getSelf());

				long currentTime = System.currentTimeMillis();
				
				if(lastTime != 0)
				{
					//log.debug("Run completed, remaining outstanding runs: {}, remaining queued runs: {} , time since last completed: {} seconds", rcToChildActor.size(), runsToDo.size(), (currentTime - lastTime) / 1000.0 );
					
				} else
				{
					//log.debug("Run completed, remaining outstanding runs: {}, remaining queued runs: {} ", rcToChildActor.size(), runsToDo.size());
				}
				lastTime = currentTime;
				
				pollCoordinator.run();
					
			}
		
			
		} else if(arg0 instanceof RequestRunConfigurationUpdate)
		{
			ActorRef child = this.rcToChildActor.get(((RequestRunConfigurationUpdate) arg0).getAlgorithmRunConfiguration());
			if(child != null)
			{
				child.tell(arg0, getSelf());
			} else
			{
				if(((RequestRunConfigurationUpdate) arg0).getKillStatus())
				{
					unstartedRunsToKill.add(((RequestRunConfigurationUpdate) arg0).getAlgorithmRunConfiguration());
				}
			}
		}  else if (arg0 instanceof UpdateObservationStatus)
		{
			//log.info("Sending observation status");
			for(Entry<AlgorithmRunConfiguration, ActorRef> ent : this.rcToChildActor.entrySet())
			{
				ent.getValue().tell(new RequestRunConfigurationUpdate(ent.getKey(), false, uuid), getSelf());
			}
		}else
		{
			unhandled(arg0);
		}
		
	}
	
	public void postStop()
	{
		scheduledTask.cancel();
	}

	private static final class PollCoordinator implements Runnable
	{

		private final ActorRef sender;
		private final ActorRef coordinator;
		//private final RequestWorkers msg;
		
		private final AtomicInteger runsNeeded;
		//private final ActorSystem system;
		private final UUID uuid;
		private double priority;
		
		private static final Logger log = LoggerFactory.getLogger(PollCoordinator.class);
		
		
		public PollCoordinator(ActorRef sender, ActorRef coordinator,AtomicInteger runsNeeded, double priority, UUID uuid)
		{
			this.sender = sender;
			this.coordinator = coordinator;
			//this.msg = msg;
			this.runsNeeded = runsNeeded;
			this.uuid = uuid;
			this.priority = priority;
			//this.system = system;
		}
		@Override
		public void run() {
			
			int runsNeeded = this.runsNeeded.get();
			
			if(runsNeeded > 0)
			{
				//log.debug("Requesting worker for UUID: {}", uuid);
				RequestWorkers rw = new RequestWorkers(priority, runsNeeded , uuid, sender);
				this.coordinator.tell(rw, sender); 
			}
		}
		
	}

}
