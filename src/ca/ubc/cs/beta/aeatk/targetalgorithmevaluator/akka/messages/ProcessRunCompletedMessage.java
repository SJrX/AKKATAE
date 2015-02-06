package ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.messages;

import java.io.Serializable;

import ca.ubc.cs.beta.aeatk.algorithmrunresult.AlgorithmRunResult;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.ProcessRunMessage;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.SubmitToken;

public class ProcessRunCompletedMessage implements Serializable{

	private final AlgorithmRunResult runResult;
	private final ProcessRunMessage processRun;

	public ProcessRunCompletedMessage(ProcessRunMessage prun, AlgorithmRunResult runResult)
	{
		this.processRun = prun;
		this.runResult = runResult;
		
	}
	
	public ProcessRunMessage getProcessRun()
	{
		return processRun;
	}
	public AlgorithmRunResult getAlgorithmRunResult()
	{
		return runResult;
	}
	
	
	public int hashCode()
	{
		return processRun.hashCode();
	}
	
	public boolean equals( Object o )
	{
		if(o == null)
		{
			return false;
		}
		if (o instanceof ProcessRunCompletedMessage)
		{
			ProcessRunCompletedMessage prc = (ProcessRunCompletedMessage) o;
			return prc.processRun.equals(processRun);
			
		} else
		{
			return false;
		}
		
	}
	 
	public String toString()
	{
		return getClass().getSimpleName() + ":" + processRun.getSubmitToken() + ":" + runResult;
	}
}
