package ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.messages;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import ca.ubc.cs.beta.aeatk.algorithmrunconfiguration.AlgorithmRunConfiguration;
import akka.actor.ActorRef;

public class RequestRunBatch implements Serializable {

	private final ActorRef observerRef;
	
	private final ActorRef completionRef;
	
	private final List<AlgorithmRunConfiguration> runConfigurations;

	private final UUID uuid;
	
	private final double priority;
	
	public RequestRunBatch(ActorRef observerRef, ActorRef completionRef, List<AlgorithmRunConfiguration> runsToRequest, UUID uuid, double priority)
	{
		this.observerRef = observerRef;
		this.completionRef = completionRef; 
		this.runConfigurations = Collections.unmodifiableList(runsToRequest);
		this.uuid = uuid;
		this.priority = priority;
	
	}


	public List<AlgorithmRunConfiguration> getAlgorithmRunConfigurations() {

		return runConfigurations;
	}


	public UUID getUUID() {
		return uuid;
	}


	public ActorRef getObserverRef() {
		return observerRef;
	}
	
	public ActorRef getCompletionRef()
	{
		return completionRef; 
	}
	
	public double getPriority()
	{
		return priority;
	}
}
