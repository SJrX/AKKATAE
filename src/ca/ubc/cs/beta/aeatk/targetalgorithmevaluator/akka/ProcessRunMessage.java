package ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka;

import java.io.Serializable;

import ca.ubc.cs.beta.aeatk.algorithmrunconfiguration.AlgorithmRunConfiguration;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.messages.ProcessRunCompletedMessage;

public class ProcessRunMessage implements Serializable {

	private final AlgorithmRunConfiguration rc;
	private final SubmitToken token;

	public ProcessRunMessage(SubmitToken token, AlgorithmRunConfiguration rc2) {
		this.token = token;
		this.rc = rc2;
	}
	
	public String toString()
	{
		return token + ":" + rc;
	}
	
	public int hashCode()
	{
		return token.hashCode() ^ rc.hashCode();  
	}
	
	public boolean equals( Object o )
	{
		if ( o == null)
		{
			return false;
		}
		if (o instanceof ProcessRunMessage)
		{
			ProcessRunMessage pr = (ProcessRunMessage) o;
			
			return pr.token.equals(token) && pr.rc.equals(rc);
		} else
		{
			return false;
		}
		
	}
	
	public AlgorithmRunConfiguration getAlgorithmRunConfiguration()
	{
		return rc;
	}
	
	public SubmitToken getSubmitToken()
	{
		return token;
	}
}
