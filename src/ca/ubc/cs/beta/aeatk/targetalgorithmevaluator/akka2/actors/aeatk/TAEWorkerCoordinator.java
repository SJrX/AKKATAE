package ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka2.actors.aeatk;

import java.lang.management.ManagementFactory;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka2.messages.RequestWorkers;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka2.messages.WhereAreYou;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka2.messages.WorkerAvailable;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka2.messages.WorkerPermit;
import akka.actor.ActorRef;
import akka.actor.UntypedActor;

public class TAEWorkerCoordinator extends UntypedActor {

	ArrayDeque<ActorRef> freeWorkers = new ArrayDeque<>();
	
	
	
	PriorityQueue<RequestWorkers> pQue = new PriorityQueue<RequestWorkers>();
	
	ConcurrentMap<RequestWorkers, AtomicInteger> workerRequests = new ConcurrentHashMap<>();
	
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
			//log.warn("Worker Available");
			WorkerAvailable wa = (WorkerAvailable) msg;
			
			freeWorkers.add(wa.getWorkerActorRef());
			assignRunsIfPossible();
		} else if (msg instanceof RequestWorkers)
		{
			//log.warn("Recieved request for worker");
			RequestWorkers rw = (RequestWorkers) msg;
			
			AtomicInteger oldValue = workerRequests.putIfAbsent(rw, new AtomicInteger(rw.getRequestCount()));
			
			if(oldValue == null)
			{
				//log.info("Adding pQue");
				pQue.add(rw);
			}else {
				//log.info("Old Value: {}", oldValue );
			}
			
			int oldRequestCount = workerRequests.get(rw).get();
			
			int currentRequestCount = rw.getRequestCount();
			
			//We set it to the MAX of the current request or the previous request. 
			
			//This may lead us to assign more workers than necessary.
			
			
			workerRequests.get(rw).set(Math.max(oldRequestCount, currentRequestCount));
			//log.info("Request for worker recieved current workers needed: {}", workerRequests.get(rw).get());
			
			
			assignRunsIfPossible();
		} else if(msg instanceof WhereAreYou ) 
		{
			//System.out.println("Hello");
			
			getSender().tell(ManagementFactory.getRuntimeMXBean().getName(), getSelf());
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
					
					ActorRef worker = freeWorkers.poll();
					
					int numberLeft = workerRequests.get(rw).decrementAndGet();
					
					WorkerPermit wp = new WorkerPermit(worker);
					
					if(numberLeft <= 0)
					{
						pQue.poll();
						workerRequests.remove(rw);
					}
					
					//log.warn("Sending worker permit");
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
