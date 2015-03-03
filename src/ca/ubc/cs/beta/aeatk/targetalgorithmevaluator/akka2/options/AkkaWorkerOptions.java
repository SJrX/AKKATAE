package ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka2.options;

import com.beust.jcommander.ParametersDelegate;

import ca.ubc.cs.beta.aeatk.logging.ConsoleOnlyLoggingOptions;
import ca.ubc.cs.beta.aeatk.logging.LoggingOptions;
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
	
	
}
