package ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka2.messages;

import java.io.Serializable;
import java.util.UUID;

public class AlgorithmRunBatchCompleted implements Serializable {

	private UUID uuid;
	
	public AlgorithmRunBatchCompleted(UUID uuid)
	{
		this.uuid = uuid;
	}
	
	public UUID getUUID() {
		return uuid;
	}

}
