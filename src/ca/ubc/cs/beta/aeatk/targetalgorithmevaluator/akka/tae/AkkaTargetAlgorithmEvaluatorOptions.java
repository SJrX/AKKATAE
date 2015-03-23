package ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.tae;

import java.io.File;
import java.nio.file.Files;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParametersDelegate;

import ca.ubc.cs.beta.aeatk.logging.LogLevel;
import ca.ubc.cs.beta.aeatk.misc.jcommander.converter.WritableDirectoryConverter;
import ca.ubc.cs.beta.aeatk.misc.jcommander.validator.NonNegativeInteger;
import ca.ubc.cs.beta.aeatk.misc.options.UsageTextField;
import ca.ubc.cs.beta.aeatk.options.AbstractOptions;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.options.AkkaClusterOptions;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.base.cli.CommandLineTargetAlgorithmEvaluatorFactory;

@UsageTextField(title="AKKA Target Algorithm Evaluator", description="Options for the AKKA Target Algorithm Evaluator")
public class AkkaTargetAlgorithmEvaluatorOptions extends AbstractOptions {

	@ParametersDelegate 
	public AkkaClusterOptions akkaClusterOptions = new AkkaClusterOptions();
	
	
	@Parameter(names="--akka-tae-observer-frequency", description="How often (in milliseconds) to request an update from an observer")
	public int observerFrequency = 500;
	
	@Parameter(names={"--akka-tae-directory"}, description="Directory to use when auto-negiotating ids", converter=WritableDirectoryConverter.class)
	public File dir = new File(System.getProperty("user.dir"));
	
	@Parameter(names={"--akka-tae-sync-worker","--akka-tae-synchronous-worker"}, description="If true, we will start a worker locally. It will only process jobs while the TAE is waiting for work")
	public boolean syncWorker;
	
	@Parameter(names={"--akka-tae-sync-worker-tae"}, description="Target Algorithm Evaluator to use for the synchronous worker (cannot be AKKA TAE due to paradox that would ensue)")
	public String tae = CommandLineTargetAlgorithmEvaluatorFactory.NAME;
		
	@Parameter(names={"--akka-tae-print-status-frequency"}, description="If > 0 we will print the current status this often in milli seconds", validateWith=NonNegativeInteger.class)
	public int printStatusFrequency = 0;
	
	@Parameter(names={"--akka-tae-log-level"}, description="Log level for AKKA Target Algorithm Evaluator events")
	public LogLevel logLevel = LogLevel.WARN;
	
	
	
}
