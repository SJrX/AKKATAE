package ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.messages;

import java.io.Serializable;
import java.util.UUID;

public class AllAlgorithmRunsDispatched implements Serializable{

	private final UUID uuid;
	
	public AllAlgorithmRunsDispatched(UUID uuid)
	{
		this.uuid = uuid;
	}
	
	public UUID getUUID()
	{
		return uuid;
	}
	
	
}
