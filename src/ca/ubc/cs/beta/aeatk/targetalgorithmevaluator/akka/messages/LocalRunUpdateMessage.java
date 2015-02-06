package ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.messages;

import java.io.Serializable;

import ca.ubc.cs.beta.aeatk.algorithmrunresult.AlgorithmRunResult;

public class LocalRunUpdateMessage implements Serializable{
	
	
	private AlgorithmRunResult run;

	public LocalRunUpdateMessage(AlgorithmRunResult run)
	{
		this.run = run;
		
	}
	
	public AlgorithmRunResult getAlgorithmRunResult()
	{
		return run;
	}

}
