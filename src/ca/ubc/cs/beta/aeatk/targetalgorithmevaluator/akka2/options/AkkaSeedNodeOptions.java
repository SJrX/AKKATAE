package ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka2.options;

import java.io.File;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParametersDelegate;

import ca.ubc.cs.beta.aeatk.help.HelpOptions;
import ca.ubc.cs.beta.aeatk.logging.ConsoleOnlyLoggingOptions;
import ca.ubc.cs.beta.aeatk.logging.LoggingOptions;
import ca.ubc.cs.beta.aeatk.misc.jcommander.converter.WritableDirectoryConverter;
import ca.ubc.cs.beta.aeatk.misc.options.UsageTextField;
import ca.ubc.cs.beta.aeatk.options.AbstractOptions;

@UsageTextField(title="Akka Seed Node", noarg=SeedNodeNoArg.class)
public class AkkaSeedNodeOptions extends AbstractOptions {

	@ParametersDelegate
	public
	AkkaClusterOptions clusterOpts = new AkkaClusterOptions();
	
	@Parameter(names={"--akka-seed-dir"}, description="Directory to use when auto-negiotating ids", converter=WritableDirectoryConverter.class)
	public File dir = new File(System.getProperty("user.dir"));
	
	@ParametersDelegate
	public LoggingOptions logOpts = new ConsoleOnlyLoggingOptions();
	
	@ParametersDelegate
	public HelpOptions helpOpts = new HelpOptions();
	
	
}
