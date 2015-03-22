package ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka2.options;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParametersDelegate;

import ca.ubc.cs.beta.aeatk.logging.ConsoleOnlyLoggingOptions;
import ca.ubc.cs.beta.aeatk.logging.LoggingOptions;
import ca.ubc.cs.beta.aeatk.misc.jcommander.validator.NonNegativeInteger;
import ca.ubc.cs.beta.aeatk.misc.options.OptionLevel;
import ca.ubc.cs.beta.aeatk.misc.options.UsageTextField;
import ca.ubc.cs.beta.aeatk.options.AbstractOptions;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.TargetAlgorithmEvaluatorOptions;

@UsageTextField(description="Options for controlling an Akka based worker", title="AKKA Worker Options")
public class AkkaWorkerOptions extends AbstractOptions{

	@ParametersDelegate
	public AkkaWorkerClusterOptions clustOpts = new AkkaWorkerClusterOptions();
	
	@ParametersDelegate
	public TargetAlgorithmEvaluatorOptions taeOptions = new TargetAlgorithmEvaluatorOptions();
	
	@ParametersDelegate
	public LoggingOptions log = new ConsoleOnlyLoggingOptions();
	
	@UsageTextField(level=OptionLevel.ADVANCED)
	@Parameter(names={"--akka-notify-available-frequency"}, description="How often should a worker notify the coordinator that it is available in seconds (provided messages aren't lost this should have almost no effect)", validateWith=NonNegativeInteger.class)
	public int workerPollAvailability = 30;

	@UsageTextField(level=OptionLevel.ADVANCED)
	@Parameter(names={"--akka-additional-notification"}, description="How many additional notifications should be given for every request for an update we receive", validateWith=NonNegativeInteger.class)
	public int additionalNotifications = 3;
	
}
