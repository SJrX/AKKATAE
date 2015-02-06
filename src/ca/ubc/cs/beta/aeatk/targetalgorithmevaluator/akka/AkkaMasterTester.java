package ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka;

import ca.ubc.cs.beta.aeatk.algorithmexecutionconfiguration.AlgorithmExecutionConfiguration;
import ca.ubc.cs.beta.aeatk.algorithmrunconfiguration.AlgorithmRunConfiguration;
import ca.ubc.cs.beta.aeatk.parameterconfigurationspace.ParameterConfigurationSpace;
import ca.ubc.cs.beta.aeatk.probleminstance.ProblemInstance;
import ca.ubc.cs.beta.aeatk.probleminstance.ProblemInstanceSeedPair;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.TargetAlgorithmEvaluator;

public class AkkaMasterTester {
	
	public static void main(String[] args)
	{
		AkkaTargetAlgorithmEvaluatorFactory taeF = new AkkaTargetAlgorithmEvaluatorFactory();
		
		TargetAlgorithmEvaluator tae = taeF.getTargetAlgorithmEvaluator();
		
		ProblemInstance pi = new ProblemInstance("testInstance");
		
		ProblemInstanceSeedPair pisp = new ProblemInstanceSeedPair(pi, 1);
		
		ParameterConfigurationSpace configSpace = ParameterConfigurationSpace.getSingletonConfigurationSpace();
		
		AlgorithmExecutionConfiguration execConfig = new AlgorithmExecutionConfiguration("test", "Test", configSpace, false, false, 5);
		
		AlgorithmRunConfiguration runConfig = new AlgorithmRunConfiguration(pisp, 25, configSpace.getDefaultConfiguration(), execConfig);

		System.out.println("Waiting for method");
		tae.evaluateRun(runConfig);
	}
}
