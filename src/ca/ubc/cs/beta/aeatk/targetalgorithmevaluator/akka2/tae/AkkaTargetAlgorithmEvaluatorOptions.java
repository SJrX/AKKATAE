package ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka2.tae;

import java.io.File;
import java.nio.file.Files;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParametersDelegate;

import ca.ubc.cs.beta.aeatk.misc.jcommander.converter.WritableDirectoryConverter;
import ca.ubc.cs.beta.aeatk.misc.options.UsageTextField;
import ca.ubc.cs.beta.aeatk.options.AbstractOptions;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka2.options.AkkaClusterOptions;

@UsageTextField(title="AKKA Target Algorithm Evaluator", description="Options for the AKKA Target Algorithm Evaluator")
public class AkkaTargetAlgorithmEvaluatorOptions extends AbstractOptions {

	@ParametersDelegate 
	public AkkaClusterOptions akkaClusterOptions = new AkkaClusterOptions();
	
	
	@Parameter(names="--akka-tae-observer-frequency", description="How often (in milliseconds) to request an update from an observer")
	public int observerFrequency = 500;
	
	@Parameter(names={"--akka-tae-directory"}, description="Directory to use when auto-negiotating ids", converter=WritableDirectoryConverter.class)
	public File dir = new File(System.getProperty("user.dir"));
	
	
}
