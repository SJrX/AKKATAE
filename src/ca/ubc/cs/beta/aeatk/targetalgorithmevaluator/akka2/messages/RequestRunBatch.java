package ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka2.messages;

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
	
	
	public RequestRunBatch(ActorRef observerRef, ActorRef completionRef, List<AlgorithmRunConfiguration> runsToRequest, UUID uuid)
	{
		this.observerRef = observerRef;
		this.completionRef = completionRef; 
		this.runConfigurations = Collections.unmodifiableList(runsToRequest);
		this.uuid = uuid;
	
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
	
}
