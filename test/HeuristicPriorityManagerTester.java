import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import org.junit.Test;

import ca.ubc.cs.beta.aeatk.algorithmexecutionconfiguration.AlgorithmExecutionConfiguration;
import ca.ubc.cs.beta.aeatk.algorithmrunconfiguration.AlgorithmRunConfiguration;
import ca.ubc.cs.beta.aeatk.parameterconfigurationspace.ParamFileHelper;
import ca.ubc.cs.beta.aeatk.parameterconfigurationspace.ParameterConfiguration;
import ca.ubc.cs.beta.aeatk.parameterconfigurationspace.ParameterConfigurationSpace;
import ca.ubc.cs.beta.aeatk.probleminstance.ProblemInstance;
import ca.ubc.cs.beta.aeatk.probleminstance.ProblemInstanceSeedPair;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.priority.HeuristicPriorityManager;
import ec.util.MersenneTwister;


public class HeuristicPriorityManagerTester {

	
	
	
	
	
	public static AlgorithmRunConfiguration getRC(ParameterConfiguration config, MersenneTwister twist)
	{
	
		
		AlgorithmExecutionConfiguration execConfig = new AlgorithmExecutionConfiguration("foo", "bar", config.getParameterConfigurationSpace(), false, true, 5);
		ProblemInstanceSeedPair pisp = new ProblemInstanceSeedPair(new ProblemInstance("pi"),twist.nextInt(1024*1024) + 1024);
		
		return new AlgorithmRunConfiguration(pisp, 5, config,execConfig);
		
		
	}
	
	@Test
	public void testHeuristicPriorityManager()
	{
		MersenneTwister twist = new MersenneTwister();
		ParameterConfigurationSpace configSpace = ParamFileHelper.getParamFileFromString("x real [0,1] [0.5]\n y real [0,1] [0.5]\n");
		
		HeuristicPriorityManager hpm = new HeuristicPriorityManager();
		
		
		ParameterConfiguration defaultConfiguration = configSpace.getDefaultConfiguration();
		
		List<ParameterConfiguration> configs = new ArrayList<>();
		
		for(int i=0; i < 10; i++)
		{
			configs.add(configSpace.getRandomParameterConfiguration(twist));
		}
		
		
		double last = -Double.MAX_VALUE;
		
		for(int i=0; i < 10; i++)
		{
			List<AlgorithmRunConfiguration> rcs = new ArrayList<>();
			for(int j=0; j <= Math. pow(2, i); j++)
			{
				rcs.add(getRC(defaultConfiguration, twist));
			}
			double priority = hpm.getPriority(rcs);
			
			System.out.println(last + " < " + priority + "?");
			assertTrue(last < priority);
			
			last = priority;
			
		}
		
		
		last =0;
	
		for(int i=0; i < 10; i++)
		{
			List<AlgorithmRunConfiguration> rcs = new ArrayList<>();
			for(int j=0; j <= Math.pow(2, i); j++)
			{
				rcs.add(getRC(configs.get(0), twist));
			}
			double priority = hpm.getPriority(rcs);
			
			System.out.println(last + " < " + priority + "?");
			assertTrue(last < priority);
			assertTrue(priority < 2);
			last = priority;
			
		}
		
		last =0;
		
		for(int i=0; i < 10; i++)
		{
			List<AlgorithmRunConfiguration> rcs = new ArrayList<>();
			for(int j=0; j <= Math.pow(2, i); j++)
			{
				rcs.add(getRC(configs.get(1), twist));
			}
			double priority = hpm.getPriority(rcs);
			
			System.out.println(last + " < " + priority + "?");
			assertTrue(last < priority);
			assertTrue(priority < 2);
			last = priority;
			
		}
		
		
		
		for(int i=0; i < 10; i++)
		{
			List<AlgorithmRunConfiguration> rcs = new ArrayList<>();
			for(int j=0; j <= Math.pow(2, i); j++)
			{
				rcs.add(getRC(configs.get(0), twist));
			}
			double priority = hpm.getPriority(rcs);
			
			System.out.println(last + " < " + priority + "?");
			assertTrue(last < priority);
			assertTrue(priority > 2);
			last = priority;
			
		}
		
		last = 0;
		for(int i=0; i < 10; i++)
		{
			List<AlgorithmRunConfiguration> rcs = new ArrayList<>();
			for(int j=0; j <= Math.pow(2, i); j++)
			{
				rcs.add(getRC(configs.get(1), twist));
			}
			double priority = hpm.getPriority(rcs);
			
			System.out.println(last + " < " + priority + "?");
			assertTrue(last < priority);
			assertTrue(priority > 2);
			last = priority;
		}
		last = 0;
		for(int i=0; i < 10; i++)
		{
			List<AlgorithmRunConfiguration> rcs = new ArrayList<>();
			for(int j=0; j <= Math.pow(2, i); j++)
			{
				rcs.add(getRC(configs.get(1), twist));
			}
			double priority = hpm.getPriority(rcs);
			
			System.out.println(last + " < " + priority + "?");
			assertTrue(last < priority);
			assertTrue(priority > 3);
			last = priority;
			
		}
		
		last = 0;
		for(int i=0; i < 10; i++)
		{
			List<AlgorithmRunConfiguration> rcs = new ArrayList<>();
			for(int j=0; j <= Math.pow(2, i); j++)
			{
				rcs.add(getRC(configs.get(2), twist));
			}
			double priority = hpm.getPriority(rcs);
			
			System.out.println(last + " < " + priority + "?");
			assertTrue(last < priority);
			assertTrue(priority > 3);
			last = priority;
			
		}
		
		last = 0;
		for(int i=0; i < 10; i++)
		{
			List<AlgorithmRunConfiguration> rcs = new ArrayList<>();
			for(int j=0; j <= Math.pow(2, i); j++)
			{
				rcs.add(getRC(configs.get(2), twist));
			}
			double priority = hpm.getPriority(rcs);
			
			System.out.println(last + " < " + priority + "?");
			assertTrue(last < priority);
			assertTrue(priority > 3);
			last = priority;
			
		}
		
		last = 0;
		for(int i=0; i < 10; i++)
		{
			List<AlgorithmRunConfiguration> rcs = new ArrayList<>();
			for(int j=0; j <= Math.pow(2, i); j++)
			{
				rcs.add(getRC(configs.get(2), twist));
			}
			double priority = hpm.getPriority(rcs);
			
			System.out.println(last + " < " + priority + "?");
			assertTrue(last < priority);
			assertTrue(priority > 3);
			last = priority;
			
		}
		
		last = 0;
		for(int i=0; i < 10; i++)
		{
			List<AlgorithmRunConfiguration> rcs = new ArrayList<>();
			for(int j=0; j <= Math.pow(2, i); j++)
			{
				rcs.add(getRC(configs.get(2), twist));
			}
			double priority = hpm.getPriority(rcs);
			
			System.out.println(last + " < " + priority + "?");
			assertTrue(last < priority);
			assertTrue(priority > 4);
			last = priority;
			
		}
		
		
		
		
		
		
		
		
		
	}
}
