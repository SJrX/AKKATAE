package ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka2.messages;

import java.io.Serializable;

import akka.actor.ActorRef;

/**
 * Message sent by worker to Coordinator to signify the worker is available.
 * 
 * @author Steve Ramage <seramage@cs.ubc.ca>
 */
public class WorkerAvailable implements Serializable {
	
	private ActorRef worker;

	public WorkerAvailable(ActorRef worker)
	{
		this.worker = worker;
	}

	public ActorRef getWorkerActorRef() {
		return worker;
		
	}

}
