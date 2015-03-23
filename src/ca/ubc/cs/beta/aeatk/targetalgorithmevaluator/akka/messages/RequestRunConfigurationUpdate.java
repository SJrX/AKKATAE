package ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.messages;

import java.io.Serializable;
import java.util.UUID;

import ca.ubc.cs.beta.aeatk.algorithmrunconfiguration.AlgorithmRunConfiguration;

/**
 * Sent by Actor to request an update to a worker for Run Configuration
 * @author Steve Ramage <seramage@cs.ubc.ca>
 *
 */
public class RequestRunConfigurationUpdate implements Serializable {

	private final AlgorithmRunConfiguration rc;
	private final boolean kill;
	private final UUID uuid;
	
	public RequestRunConfigurationUpdate(AlgorithmRunConfiguration rc, boolean kill, UUID uuid)
	{
		this.rc = rc;
		this.kill =kill;
		this.uuid = uuid;
	}

	public AlgorithmRunConfiguration getAlgorithmRunConfiguration()
	{
		return rc;
	}
	
	public boolean getKillStatus() {
		return kill;
	}
	
	@Override
	public boolean equals(Object o)
	{
		if(o == null)
		{
			return false;
		}
		if(o == this)
		{
			return true;
		}
		
		if(o instanceof RequestRunConfigurationUpdate)
		{
			RequestRunConfigurationUpdate rrcu = (RequestRunConfigurationUpdate) o;
			return (rrcu.uuid.equals(uuid) && rrcu.rc.equals(rc));
		} else
		{
			return false;
		}
	}
	
	public UUID getUUID()
	{
		return uuid;
	}
	
	@Override
	public int hashCode()
	{
		return uuid.hashCode() ^ rc.hashCode(); 
	}
	
	
	
	
}
