package ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.messages;

import java.io.Serializable;

import ca.ubc.cs.beta.aeatk.algorithmrunresult.AlgorithmRunResult;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.ProcessRunMessage;

public class ObserverUpdateResponse implements Serializable {
	
	private final AlgorithmRunResult runResult;
	private final ProcessRunMessage prun;

	public ObserverUpdateResponse(ProcessRunMessage prun, AlgorithmRunResult runResult)
	{
		if(!runResult.getAlgorithmRunConfiguration().equals(prun.getAlgorithmRunConfiguration()))
		{
			throw new IllegalArgumentException("The ProcessRun and runResult should point to equivalent AlgorithmRunConfiguration objects not:" + prun.getAlgorithmRunConfiguration() + " vs. " + runResult.getAlgorithmRunConfiguration());
		}
		this.prun = prun;
		this.runResult = runResult;
	}
	
	public ProcessRunMessage getProcessRun()
	{
		return prun;
	}
	
	public AlgorithmRunResult getAlgorithmRunResult()
	{
		return runResult;
	}
	
}
