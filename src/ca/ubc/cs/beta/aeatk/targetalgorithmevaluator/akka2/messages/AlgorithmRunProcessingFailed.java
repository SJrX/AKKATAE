package ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka2.messages;

import java.io.Serializable;

import ca.ubc.cs.beta.aeatk.algorithmrunconfiguration.AlgorithmRunConfiguration;

public class AlgorithmRunProcessingFailed implements Serializable {

	private final AlgorithmRunConfiguration rc;

	public AlgorithmRunProcessingFailed(AlgorithmRunConfiguration rc) {
		this.rc = rc;
		if(this.rc == null)
		{
			throw new IllegalArgumentException("Invalid run configuration");
		}
	}

	public AlgorithmRunConfiguration getAlgorithmRunConfiguration()
	{
		return rc;
	}
}
