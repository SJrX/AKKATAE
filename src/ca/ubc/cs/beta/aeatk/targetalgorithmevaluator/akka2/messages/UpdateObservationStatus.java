package ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka2.messages;

import java.io.Serializable;
import java.util.UUID;


public class UpdateObservationStatus implements Serializable{

	private final UUID uuid;
	public UpdateObservationStatus(UUID uuid)
	{
		this.uuid = uuid;
	}
	
	public UUID getUUID()
	{
		return uuid;
	}
}
