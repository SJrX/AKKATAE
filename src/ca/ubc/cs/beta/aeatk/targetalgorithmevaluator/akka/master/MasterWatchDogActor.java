package ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.master;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.Set;

import ca.ubc.cs.beta.aeatk.algorithmrunresult.AlgorithmRunResult;
import ca.ubc.cs.beta.aeatk.algorithmrunresult.ExistingAlgorithmRunResult;
import ca.ubc.cs.beta.aeatk.algorithmrunresult.RunStatus;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.ProcessRunMessage;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.SubmitToken;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.messages.WorkerAvailableMessage;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.messages.ObserverUpdateResponse;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.messages.ProcessRunCompletedMessage;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.messages.RequestObserverUpdateMessage;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.messages.ShutdownMessage;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.messages.UpdateObservationStatus;
import akka.actor.ActorRef;
import akka.actor.UntypedActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;

public class MasterWatchDogActor extends UntypedActor {

	LoggingAdapter log = Logging.getLogger(getContext().system(), this);
	
	
	private final Queue<ProcessRunMessage> queue = new ArrayDeque<ProcessRunMessage>();
	
	private final Queue<ActorRef> freeWorkerActorRefsQueue = new ArrayDeque<ActorRef>();
	
	private final Map<ActorRef, ProcessRunMessage> workerToRunMap = new HashMap<>();
	private final Map<ProcessRunMessage, ActorRef> runToWorkerMap = new HashMap<>();
	
	
	private final Map<ProcessRunMessage, ActorRef> callerReferences = new HashMap<>();
	
	private final Set<ActorRef> allWorkers = new HashSet<ActorRef>();
	
	private ActorRef observerRequestInbox;
	
	@Override
	public void onReceive(Object message) throws Exception {
		
		if(message instanceof ProcessRunMessage)
		{
			/**
			 * Arrives from TAE requesting that we do this run
			 * 
			 * 1)Records the sender of the message.
			 * 2)Puts all run in queue.
			 * 3)Assigns a run if possible 
			 */
			callerReferences.put((ProcessRunMessage) message, getSender());
			queue.add((ProcessRunMessage) message);
			while(assignRunIfPossible());
			
		} else if (message instanceof ProcessRunCompletedMessage)
		{
			
			freeWorkerActorRefsQueue.add(this.getSender());
			workerToRunMap.remove(getSender());
			runToWorkerMap.remove(((ProcessRunCompletedMessage) message).getProcessRun());
			
			while(assignRunIfPossible());
			ActorRef caller = callerReferences.remove(((ProcessRunCompletedMessage) message).getProcessRun());
			
			//log.info("Done run: {} ", caller);
			if(caller != null)
			{
				//log.info("Notifying caller: {} that {}",caller,  message);
				caller.tell(message, getSelf());
			} else
			{
				
				log.warning("No caller: {}", message);
			}
		} else if (message instanceof WorkerAvailableMessage)
		{
			//log.info("New worker detected:" + this.getSender());
			
			freeWorkerActorRefsQueue.add(this.getSender());
			allWorkers.add(getSender());
			while(assignRunIfPossible());
		} else if (message instanceof ShutdownMessage)
		{
			for(ActorRef ref : allWorkers)
			{
				ref.tell(message,getSelf());
			}
			getSender().tell("OKAY", getSelf());
		} else if(message instanceof UpdateObservationStatus)
		{
			observerRequestInbox = getSender();
			for(Entry<ActorRef, ProcessRunMessage> ent : workerToRunMap.entrySet())
			{
				ent.getKey().tell(new RequestObserverUpdateMessage(ent.getValue(), false), getSelf());
			}
			
		} else if( message instanceof RequestObserverUpdateMessage)
		{
			
			RequestObserverUpdateMessage rou = ((RequestObserverUpdateMessage) message);
			ProcessRunMessage prun = rou.getProcessRun();
			ActorRef assignedActor = runToWorkerMap.get(prun);
			
			if(assignedActor != null)
			{
				assignedActor.tell(message, getSelf());
			} else
			{ //Not currently assigned 
				System.err.println("No assigned worker");
				if (rou.kill())
				{ //Is Killed

					System.err.println("Run is killed and we don't have an actor");
					if(queue.remove(prun))
					{ //Removed from queue successfully
						
					
						ActorRef caller = callerReferences.remove(prun);
						
						//log.info("Done run: {} ", caller);
						if(caller != null)
						{
							//log.info("Notifying caller: {} that {}",caller,  message);
							
							
							AlgorithmRunResult runResult = new ExistingAlgorithmRunResult(prun.getAlgorithmRunConfiguration(), RunStatus.KILLED, 0,0,0,prun.getAlgorithmRunConfiguration().getProblemInstanceSeedPair().getSeed(), "Killed in AKKA TAE Outgoing Queue", 0);
							ProcessRunCompletedMessage prc = new ProcessRunCompletedMessage(prun, runResult);
							caller.tell(prc, getSelf());
						}
					}
					
				}
			}
		} else if (message instanceof ObserverUpdateResponse)
		{
			log.info("Telling observer inbox");
			ObserverUpdateResponse our = (ObserverUpdateResponse) message;
			observerRequestInbox.tell(our, getSelf());
		} else
		{ 
			log.warning("Unhandled message type: {}", message);
			unhandled(message);
		}
		
	}

	private boolean assignRunIfPossible() 
	{
		/**
		 * If there is an element in the queue, and there is a free worker
		 * 
		 */
		if (queue.peek() != null && freeWorkerActorRefsQueue.peek() != null)
		{
			ProcessRunMessage prun = queue.poll();
			ActorRef aref = freeWorkerActorRefsQueue.poll();
			
			
			aref.tell(prun, getSelf());
			
			workerToRunMap.put(aref, prun);
			runToWorkerMap.put(prun, aref);
			return true;
		} else
		{
			return false;
		}
	}

}
