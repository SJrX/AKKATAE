package ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.messages;

import java.io.Serializable;

public class ContactMaster implements Serializable {

	private final String host;
	private final String port;

	public ContactMaster(String host, String port)
	{
		this.host = host;
		this.port = port;
	}
	
	public String getHost()
	{
		return this.host;
	}
	
	public String getPort()
	{
		return this.port;
	}
}
