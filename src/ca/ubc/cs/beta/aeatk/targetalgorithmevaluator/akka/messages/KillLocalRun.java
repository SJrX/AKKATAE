package ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.messages;

import java.io.Serializable;

import ca.ubc.cs.beta.aeatk.algorithmrunconfiguration.AlgorithmRunConfiguration;

public class KillLocalRun implements Serializable 
{

	private AlgorithmRunConfiguration rc;

	public KillLocalRun(AlgorithmRunConfiguration rc)
	{
		this.rc = rc;
	}
	
	public AlgorithmRunConfiguration getAlgorithmRunConfiguration()
	{
		return rc;
	}
}
