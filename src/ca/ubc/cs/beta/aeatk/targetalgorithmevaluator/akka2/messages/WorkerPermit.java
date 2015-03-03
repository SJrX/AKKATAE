package ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka2.messages;

import java.io.Serializable;

import akka.actor.ActorRef;

/**
 * Sent by coordinator to requestor of work granting a worker
 * @author Steve Ramage <seramage@cs.ubc.ca>
 *
 */
public class WorkerPermit implements Serializable {
	
	private final ActorRef worker;
	
	public WorkerPermit(ActorRef worker)
	{
		this.worker = worker;
	}

	public ActorRef getWorker() {
		return worker;
	}

}
