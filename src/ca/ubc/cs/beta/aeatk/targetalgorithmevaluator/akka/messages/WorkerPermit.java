package ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.messages;

import java.io.Serializable;
import java.util.UUID;

import akka.actor.ActorRef;

/**
 * Sent by coordinator to requestor of work granting a worker
 * @author Steve Ramage <seramage@cs.ubc.ca>
 *
 */
public class WorkerPermit implements Serializable {
	
	private final ActorRef worker;
	
	private final UUID uuid;
	
	private final String name;
	public WorkerPermit(ActorRef worker, UUID uuid, String name)
	{
		this.worker = worker;
		this.uuid = uuid;
		this.name = name;
	}

	public ActorRef getWorker() {
		return worker;
	}

	public UUID getUUID()
	{
		return uuid;
	}

	public String getWorkerName() {
		return name;
	}
}
