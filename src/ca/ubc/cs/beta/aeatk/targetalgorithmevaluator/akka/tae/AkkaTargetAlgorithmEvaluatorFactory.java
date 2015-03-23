package ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.tae;

import org.mangosdk.spi.ProviderFor;

import ca.ubc.cs.beta.aeatk.options.AbstractOptions;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.AbstractTargetAlgorithmEvaluatorFactory;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.TargetAlgorithmEvaluator;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.TargetAlgorithmEvaluatorFactory;

@ProviderFor(TargetAlgorithmEvaluatorFactory.class)
public class AkkaTargetAlgorithmEvaluatorFactory extends
		AbstractTargetAlgorithmEvaluatorFactory implements
		TargetAlgorithmEvaluatorFactory {

	private static final String NAME = "AKKA";
	@Override
	public String getName() {
		return NAME;
	}

	public static String getTAEName()
	{
		return NAME;
	}
	
	@Override
	public AkkaTargetAlgorithmEvaluator getTargetAlgorithmEvaluator(
			AbstractOptions options) {

		return new AkkaTargetAlgorithmEvaluator((AkkaTargetAlgorithmEvaluatorOptions) options);
	}

	@Override
	public AkkaTargetAlgorithmEvaluatorOptions getOptionObject() {

		return new AkkaTargetAlgorithmEvaluatorOptions();
	}

}
