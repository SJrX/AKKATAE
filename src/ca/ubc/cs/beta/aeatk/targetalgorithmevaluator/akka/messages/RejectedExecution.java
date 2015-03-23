package ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.messages;

import java.io.Serializable;

import ca.ubc.cs.beta.aeatk.algorithmrunconfiguration.AlgorithmRunConfiguration;

public class RejectedExecution implements Serializable {

	private final AlgorithmRunConfiguration rc;

	public RejectedExecution(AlgorithmRunConfiguration rc)
	{
		this.rc = rc;
	}
}
