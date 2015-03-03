package ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka2.messages;

import java.io.Serializable;
import java.util.UUID;

import ca.ubc.cs.beta.aeatk.algorithmrunresult.AlgorithmRunResult;

public class AlgorithmRunStatus implements Serializable {

	private final AlgorithmRunResult result;
	private final UUID uuid; 
	
	public AlgorithmRunStatus(AlgorithmRunResult result, UUID uuid)
	{
		this.result = result;
		this.uuid = uuid;
		if(uuid == null)
		{
			throw new IllegalArgumentException("UUID cannot be null");
		}
		if(result == null)
		{
			throw new IllegalArgumentException("Run cannot be null");
		}
	}
	
	public AlgorithmRunResult getAlgorithmRunResult() {
		return result;
	}
	
	public UUID getUUID()
	{
		return uuid;
	}
}
