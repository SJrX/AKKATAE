package ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.options;

import com.beust.jcommander.Parameter;

import ca.ubc.cs.beta.aeatk.misc.jcommander.validator.FixedPositiveInteger;
import ca.ubc.cs.beta.aeatk.misc.options.OptionLevel;
import ca.ubc.cs.beta.aeatk.misc.options.UsageTextField;
import ca.ubc.cs.beta.aeatk.options.AbstractOptions;


@UsageTextField(hiddenSection=true)
public class AkkaClusterOptions extends AbstractOptions{

	@Parameter(names="--akka-id", description="ID of the akka worker, if not set we will auto negiotate this with other workers. If you explicitly set this make sure there are no collisions", required=false)
	public Integer id;
	
	@Parameter(names="--akka-network", description="Comma seperated list of networks to prefer (e.g. 24.85., 10.52.213, 127.0.). Networks are matched by string prefix matching. ")
	public String networks = "127.0.0.1";
	
	@UsageTextField(level = OptionLevel.DEVELOPER)
	@Parameter(names="--akka-tae-jmx-enabled", description="Whether JMX should be enabled")
	public boolean jmxEnabled = false;
	
	/**
	 * Most of these options come from https://groups.google.com/forum/#!topic/akka-user/e5E52F6Ykfs
	 */
	@UsageTextField(level = OptionLevel.DEVELOPER)
	@Parameter(names="--akka-tae-gossip-interval", description="Gossip protocol interval in milliSeconds", validateWith=FixedPositiveInteger.class)
	public int gossipInterval = 1000;
	
	@UsageTextField(level = OptionLevel.DEVELOPER)
	@Parameter(names="--akka-tae-leader-actions-interval", description="Leader actions interval", validateWith=FixedPositiveInteger.class)
	public int leaderActionInterval = 1000;
	
	@UsageTextField(level = OptionLevel.DEVELOPER)
	@Parameter(names="--akka-tae-unreachable-nodes-reaper-interval", description="Unreachable Nodes Reaper Interval", validateWith=FixedPositiveInteger.class)
	public int unreachableNodesReaperInterval = 1000;
	
	@UsageTextField(level = OptionLevel.DEVELOPER)
	@Parameter(names="--akka-tae-periodic-tasks-initial-delay", description="Periodic Tasks Initial Delay", validateWith=FixedPositiveInteger.class)
	public int  periodicTasksInitialDelay = 300;
	
	@UsageTextField(level = OptionLevel.DEVELOPER)
	@Parameter(names="--akka-tae-failure-detector-heartbeat-interval", description="How often to send a heart beat message", validateWith=FixedPositiveInteger.class)
	public int  failureDetectorHeartbeatInterval = 5000;
	
	@Parameter(names="--akka-tae-auto-down-unreachable-after", description="Auto down unreachable after (ms)", validateWith=FixedPositiveInteger.class)
	public int autoDownUnreachableAfter = 10000;

	@Parameter(names="--akka-tae-failure-detector-acceptable-heartbeat-pause", description="Minimum amount of time a node should be unresponsive before we consider it down", validateWith=FixedPositiveInteger.class)
	public int failureDetectorAcceptablePause = 15000;
	
	
	/* gossip-interval = 200 ms
			  leader-actions-interval = 200 ms
			  unreachable-nodes-reaper-interval = 500 ms
			  periodic-tasks-initial-delay = 300 ms
			  failure-detector.heartbeat-interval = 500 ms
	*/	  
	
}
