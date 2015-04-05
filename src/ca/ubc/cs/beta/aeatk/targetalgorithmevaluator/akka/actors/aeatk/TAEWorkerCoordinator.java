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
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.messages.shutdown.CountermandShutdown;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.messages.shutdown.KeepMeInTheShutdownLoop;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.messages.shutdown.RequestPermissionToShutdown;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.messages.shutdown.ShutdownPermissionGranted;
import akka.actor.ActorRef;
import akka.actor.UntypedActor;

public class TAEWorkerCoordinator extends UntypedActor {

	private final ArrayDeque<WorkerAvailable> freeWorkers = new ArrayDeque<>();
	
	private final PriorityQueue<RequestWorkers> pQue = new PriorityQueue<RequestWorkers>();
	
	private final ConcurrentMap<RequestWorkers, AtomicInteger> workerRequests = new ConcurrentHashMap<>();
	
	private final ConcurrentMap<RequestWorkers, AtomicInteger> workerAssignments = new ConcurrentHashMap<>();
	
	private final Set<ActorRef> allWorkersToShutdown = new HashSet<>();
	
	
	private final Logger log = LoggerFactory.getLogger(getClass());
	
	private final Set<ActorRef> allTAEs = new HashSet<>();
	private final Set<ActorRef> taesThatWantToShutdown = new HashSet<>();
	
	
	/**
	 * Last time we asked all remaining TAEs whether they'd like us to stay alive.
	 */
	private long lastShutdownBroadcast = 0;
	
	/**
	 * Last time we received a direct order to stay alive.
	 */
	private long lastCountermand = 0; 
	
	public TAEWorkerCoordinator()
	{
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
			log.debug("Recieved request for worker from: " + rw.getUUID() + " needing: " + rw.getRequestCount() + " priority: " + rw.getPriority());
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
		} else if(msg instanceof KeepMeInTheShutdownLoop)
		{
			allTAEs.add(getSender());
			
		} else if(msg instanceof RequestPermissionToShutdown)
		{
			taesThatWantToShutdown.add(getSender());
			
			if(lastCountermand + 86_400_000 < System.currentTimeMillis())
			{
				//It is initalized to but when we get our first request 
				//we need a way of ensuring everyone gets a chance to deny it.
				//So we will assume someone just countermanded it.
				//Assuming people keep requesting then in the window between
				//15 minutes and a day then everyone can shutdown.
				lastCountermand = System.currentTimeMillis();
			}
			
			if(allTAEs.equals(taesThatWantToShutdown))
			{
				log.info("All Target Algorithm Evaluators have signalled shutdown request, oblidging");
				for(ActorRef ref : allTAEs)
				{
					ref.tell(new ShutdownPermissionGranted(), getSelf());
				}
				
			} else if (( lastCountermand + 900_000)  < System.currentTimeMillis())
			{
				log.info("No countermand in the past 15 minutes, shutting down");
				for(ActorRef ref : allTAEs)
				{
					ref.tell(new ShutdownPermissionGranted(), getSelf());
				}
			} else if( lastShutdownBroadcast + 60_000 < System.currentTimeMillis() )
			{
				lastShutdownBroadcast = System.currentTimeMillis();
				Set<ActorRef> remainingTaes = new HashSet<>(allTAEs);
				remainingTaes.removeAll(taesThatWantToShutdown);
				
				for(ActorRef ref : remainingTaes)
				{
					ref.tell(msg, getSelf());
				}	
				
			}
		} else if (msg instanceof CountermandShutdown)
		{
			lastCountermand = System.currentTimeMillis();
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
			/*
			PriorityQueue<RequestWorkers> pQueue2 = new PriorityQueue<>(pQue);
			RequestWorkers o = pQueue2.poll(); 
			StringBuilder sb = new StringBuilder();
			while(o != null)
			{
				sb.append("[ size: " + o.getRequestCount() + ", priority: " + o.getPriority() +  ", UUID: " + o.getUUID() + "],");
				o = pQueue2.poll();
			}
			log.info("Assigned runs order: " + sb);
			*/
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
