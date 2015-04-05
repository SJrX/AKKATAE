package ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.messages.shutdown;

import java.io.Serializable;

/**
 * Used to signal that shutdown request should be granted.
 * @author Steve Ramage <seramage@cs.ubc.ca>
 *
 */
public class CountermandShutdown implements Serializable{

	
	/**
	 * BEST MOVIE EVER!!!!
	 */
	public transient String shutdownMessage = "It requires my assent,and I do not give it. Furthermore,"
											+ " if you continue upon this course, and insist upon this "
											+ "shutdown without confirming with me first I will be forced, "
											+ "backed by the rules of precedents authority and command of the "
											+ "AKKA TAE interface to relieve you of command singleton!";
	
}
