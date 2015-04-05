package ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.messages.shutdown;

import java.io.Serializable;

/**
 * Sent by a TAE to a Coordinator, who sends it to all TAEs and asks for permission
 * to shutdown. If no one objects within 5 minutes, or all agree. A ShutdownPermissionGranted
 * message is sent.
 * 
 * @author Steve Ramage <seramage@cs.ubc.ca>
 *
 */
public class RequestPermissionToShutdown implements Serializable {

	/**
	 * We miss you Gene Hackman
	 */
	public static transient String requestMessage = "To the Singleton! Need of resources no longer required. "
			+ "Dissidents threaten termination of this job. notifyShutdown(),"
			+ "immediately terminate all runs and shutdown... They're preparing to kill jobs, we don't have time to #$!? around! ";
	
}
