package ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.messages;

import java.io.Serializable;

import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.ProcessRunMessage;

public class RequestObserverUpdateMessage implements Serializable 
{

	private final ProcessRunMessage run;

	private final boolean kill;
	public RequestObserverUpdateMessage(ProcessRunMessage run, boolean kill)
	{
		this.run = run;
		this.kill = kill;
	}
	
	public ProcessRunMessage getProcessRun()
	{
		return run;
	}
	
	public boolean kill()
	{
		return kill;
	}
}
