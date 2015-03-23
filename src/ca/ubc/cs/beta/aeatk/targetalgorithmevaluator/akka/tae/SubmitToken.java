package ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.tae;

import java.io.Serializable;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;


public class SubmitToken implements Serializable {

	private transient static AtomicInteger tokens = new AtomicInteger(0);
	
	private final int myToken;

	
	public SubmitToken()
	{
		myToken = tokens.incrementAndGet();		
	}
		
	@Override
	public boolean equals(Object o)
	{
		if (o instanceof SubmitToken)
		{
			return ((SubmitToken) o).myToken == myToken;
		} else
		{
			return false;
		}
	}
	
	@Override
	public int hashCode()
	{
		return myToken;
	}
	
	public String toString()
	{
		return "Token:<<" + myToken + ">>";
	}
	
	
}
