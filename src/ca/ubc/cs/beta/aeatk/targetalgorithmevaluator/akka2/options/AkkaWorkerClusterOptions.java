package ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka2.options;

import com.beust.jcommander.Parameter;

import ca.ubc.cs.beta.aeatk.misc.jcommander.validator.FixedPositiveInteger;
import ca.ubc.cs.beta.aeatk.misc.options.OptionLevel;
import ca.ubc.cs.beta.aeatk.misc.options.UsageTextField;
import ca.ubc.cs.beta.aeatk.options.AbstractOptions;


@UsageTextField(hiddenSection=true)
public class AkkaWorkerClusterOptions extends AbstractOptions{

	@Parameter(names="--akka-worker-id", description="ID of the akka worker", required=false)
	public Integer id;
	
	@UsageTextField(level = OptionLevel.DEVELOPER)
	@Parameter(names="--akka-worker-jmx-enabled", description="Whether JMX should be enabled")
	public boolean jmxEnabled = false;
	
	/**
	 * Most of these options come from https://groups.google.com/forum/#!topic/akka-user/e5E52F6Ykfs
	 */
	@UsageTextField(level = OptionLevel.DEVELOPER)
	@Parameter(names="--akka-worker-gossip-interval", description="Gossip protocol interval (ms)", validateWith=FixedPositiveInteger.class)
	public int gossipInterval = 200;
	
	@UsageTextField(level = OptionLevel.DEVELOPER)
	@Parameter(names="--akka-worker-leader-actions-interval", description="Leader actions interval (ms)", validateWith=FixedPositiveInteger.class)
	public int leaderActionInterval = 200;
	
	@UsageTextField(level = OptionLevel.DEVELOPER)
	@Parameter(names="--akka-worker-unreachable-nodes-reaper-interval", description="Unreachable Nodes Reaper Interval (ms)", validateWith=FixedPositiveInteger.class)
	public int unreachableNodesReaperInterval = 500;
	
	@UsageTextField(level = OptionLevel.DEVELOPER)
	@Parameter(names="--akka-worker-periodic-tasks-initial-delay", description="Periodic Tasks Initial Delay (ms)", validateWith=FixedPositiveInteger.class)
	public int  periodicTasksInitialDelay = 300;
	
	@UsageTextField(level = OptionLevel.DEVELOPER)
	@Parameter(names="--akka-worker-failure-detector-heartbeat-interval", description="Failure Detector heartbeat interval (ms)", validateWith=FixedPositiveInteger.class)
	public int  failureDetectorHeartbeatInterval = 500;

	@Parameter(names="--akka-worker-auto-down-unreachable-after", description="Auto down unreachable after (ms)", validateWith=FixedPositiveInteger.class)
	public int autoDownUnreachableAfter = 10000;
	
	
	
}
