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

	public String name;
	
	public WorkerAvailable(ActorRef worker, String workerName)
	{
		this.worker = worker;
		this.name = workerName;
	}

	public ActorRef getWorkerActorRef() {
		return worker;
	}

	public String getWorkerName()
	{
		return name;
	}
}
