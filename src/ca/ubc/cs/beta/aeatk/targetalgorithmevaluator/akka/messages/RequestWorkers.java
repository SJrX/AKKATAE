package ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.messages;

import java.io.Serializable;
import java.util.UUID;

import akka.actor.ActorRef;

public class RequestWorkers implements Serializable, Comparable<RequestWorkers>{

	
	private final double priority;
	private final int count;
	private final UUID uuid;
	private final ActorRef requestor;
	
	public RequestWorkers(double priority, int count, UUID uuid, ActorRef requestor)
	{
		this.priority = priority;
		this.count = count;
		if(uuid == null) throw new IllegalArgumentException("Must supply UUID");
		this.uuid = uuid;
		
		this.requestor = requestor;
	}
	
	@Override
	public int hashCode()
	{
		return uuid.hashCode();
	}
	
	@Override
	public boolean equals(Object o)
	{
		if(o == null) return false;
		if(o == this) return true;
		
		if(o instanceof RequestWorkers)
		{
			
			return ((RequestWorkers) o).uuid.equals(uuid);
		} else
		{
			return false;
		}
		
	}

	@Override
	public int compareTo(RequestWorkers o) {
		
		if(o.priority > priority)
		{
			return 1;
		} else if (o.priority < priority)
		{
			return -1;
		} else
		{
			return o.uuid.compareTo(uuid);
		}
		
	}

	public int getRequestCount() {
		return count;
	}
	
	public ActorRef getRequestor()
	{
		return requestor;
	}
	
	public UUID getUUID()
	{
		return uuid;
	}
}
