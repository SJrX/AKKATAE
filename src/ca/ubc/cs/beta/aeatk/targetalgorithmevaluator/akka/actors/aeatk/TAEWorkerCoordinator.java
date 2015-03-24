package ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.actors.aeatk;

import java.lang.management.ManagementFactory;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.messages.RequestWorkers;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.messages.ShutdownMessage;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.messages.WhereAreYou;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.messages.WorkerAvailable;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.messages.WorkerPermit;
import akka.actor.ActorRef;
import akka.actor.UntypedActor;

public class TAEWorkerCoordinator extends UntypedActor {

	private final ArrayDeque<WorkerAvailable> freeWorkers = new ArrayDeque<>();
	
	private final PriorityQueue<RequestWorkers> pQue = new PriorityQueue<RequestWorkers>();
	
	private final ConcurrentMap<RequestWorkers, AtomicInteger> workerRequests = new ConcurrentHashMap<>();
	
	private final ConcurrentMap<RequestWorkers, AtomicInteger> workerAssignments = new ConcurrentHashMap<>();
	
	private final Set<ActorRef> allWorkersToShutdown = new HashSet<>();
	
	
	private final Logger log = LoggerFactory.getLogger(getClass());
	
	
	public TAEWorkerCoordinator()
	{
		//System.out.println("Starting up");
		
	}
	
	@Override 
	public void preStart()
	{
		//System.out.println(getSelf());
	}
	@Override
	public void onReceive(Object msg) throws Exception {
	
		if(msg instanceof WorkerAvailable)
		{
			
			WorkerAvailable wa = (WorkerAvailable) msg;
			
			log.debug("Worker Available: " + wa.getWorkerName());
			allWorkersToShutdown.add(wa.getWorkerActorRef());
			freeWorkers.add(wa);
			assignRunsIfPossible();
		} else if (msg instanceof RequestWorkers)
		{
			
			RequestWorkers rw = (RequestWorkers) msg;
			log.debug("Recieved request for worker from: " + rw.getUUID() + " needing: " + rw.getRequestCount());
			AtomicInteger oldValue = workerRequests.putIfAbsent(rw, new AtomicInteger(rw.getRequestCount()));
			workerAssignments.putIfAbsent(rw,new AtomicInteger(0));
			if(oldValue == null)
			{
				//log.info("Adding pQue");
				pQue.add(rw);
			}else {
				//log.info("Old Value: {}", oldValue );
			}
			
			
			int currentRequestCount = rw.getRequestCount();
			workerRequests.get(rw).set(currentRequestCount);
			
			assignRunsIfPossible();
		} else if(msg instanceof WhereAreYou ) 
		{
			getSender().tell(ManagementFactory.getRuntimeMXBean().getName(), getSelf());
		} else if(msg instanceof ShutdownMessage)
		{
			
			for(ActorRef worker: allWorkersToShutdown)
			{
				worker.tell(msg, getSelf());
			}
		} else
		{
			unhandled(msg);
		}
	}

	
	private void assignRunsIfPossible()
	{
		//log.info("Trying to assign runs");
		while(true)
		{
			if(freeWorkers.peek() != null && pQue.peek() != null)
			{
				
				//log.info("Trying to assign runs " + 2);
				RequestWorkers rw = pQue.peek();
				
				//System.out.println(workerRequests.get(rw).get());
				if(workerRequests.get(rw).get() <= 0)
				{
					//Try this again as the latest request doesn't have anything in it.
					pQue.poll();
					workerRequests.remove(rw);
					continue;
				} else
				{
					
					WorkerAvailable wa = freeWorkers.poll();
					
					
					int numberLeft = workerRequests.get(rw).decrementAndGet();
					
					WorkerPermit wp = new WorkerPermit(wa.getWorkerActorRef(), rw.getUUID(), wa.getWorkerName());
					
					if(numberLeft <= 0)
					{
						pQue.poll();
						workerRequests.remove(rw);
					}
					
					//log.warn("Sending worker permit");
					log.debug("Assigning worker {} to: {}, thus far: {} ", wa.getWorkerName(), rw.getUUID(), workerAssignments.get(rw).incrementAndGet());
					rw.getRequestor().tell(wp, this.getSender());
					
				}
			} else
			{
				//log.info("Free Workers: {} , pQue: {} ", freeWorkers.size(), pQue.size() );
				break;
			}
		}
	}
}
