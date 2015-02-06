import static org.junit.Assert.assertEquals;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import ca.ubc.cs.beta.TestHelper;
import ca.ubc.cs.beta.aeatk.algorithmexecutionconfiguration.AlgorithmExecutionConfiguration;
import ca.ubc.cs.beta.aeatk.algorithmrunconfiguration.AlgorithmRunConfiguration;
import ca.ubc.cs.beta.aeatk.algorithmrunresult.AlgorithmRunResult;
import ca.ubc.cs.beta.aeatk.algorithmrunresult.RunStatus;
import ca.ubc.cs.beta.aeatk.options.MySQLOptions;
import ca.ubc.cs.beta.aeatk.parameterconfigurationspace.ParameterConfiguration;
import ca.ubc.cs.beta.aeatk.parameterconfigurationspace.ParameterConfigurationSpace;
import ca.ubc.cs.beta.aeatk.probleminstance.ProblemInstance;
import ca.ubc.cs.beta.aeatk.probleminstance.ProblemInstanceSeedPair;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.TargetAlgorithmEvaluator;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.TargetAlgorithmEvaluatorRunObserver;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.AkkaTargetAlgorithmEvaluatorFactory;
import ec.util.MersenneTwister;


public class AkkaTargetAlgorithmEvaluatorTester {

	
	private  Process proc;
	
	private AlgorithmExecutionConfiguration execConfig;

	private  ParameterConfigurationSpace configSpace;
	
	
	
	
	private Random rand;
	
	private Thread t;
	
	
	@Before
	public void beforeTest()
	{
	
		try {
			StringBuilder b = new StringBuilder();
			
			List<String> args = new ArrayList<>();
			
			args.add("java");
			args.add("-cp");
			
			args.add(System.getProperty("java.class.path"));
			
			args.add("ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.worker.AkkaWorker");
			
			
			
			
			ProcessBuilder pb = new ProcessBuilder();
			pb.redirectErrorStream(true);
			
			
			pb.command(args);
			proc =pb.start();
			
			t = new Thread(new Runnable()
			{

				@Override
				public void run() {
					
					BufferedReader reader = new BufferedReader(new InputStreamReader( proc.getInputStream()));
					String line = null;
					try {
						while((line = reader.readLine()) != null)
						{
							if(Thread.interrupted())
							{
								reader.close();
								return;
							}
							System.out.println("PROC>" + line);
						}
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
						return;
					}
					
					
				}
				
			});
			
			t.start();
			
			
			
			
		} catch (IOException e) {
			e.printStackTrace();
		}
		File paramFile = TestHelper.getTestFile("paramFiles/paramEchoParamFileWithKilled.txt");
		configSpace = new ParameterConfigurationSpace(paramFile);
		execConfig = new AlgorithmExecutionConfiguration("ignore", System.getProperty("user.dir"), configSpace, false, false, 500);
		rand = new MersenneTwister();

		
	}
	
	@Test
	public void testAkka()
	{
	
		List<AlgorithmRunConfiguration> runConfigs = new ArrayList<AlgorithmRunConfiguration>(5);
		for(int i=0; i < 20; i++)
		{
			ParameterConfiguration config = configSpace.getDefaultConfiguration();
		
			config.put("seed", String.valueOf(i));
			config.put("runtime", String.valueOf(2*i+1));
			AlgorithmRunConfiguration rc = new AlgorithmRunConfiguration(new ProblemInstanceSeedPair(new ProblemInstance("RunPartition"), Long.valueOf(config.get("seed"))), 1001, config,execConfig);
			runConfigs.add(rc);
			
		}
		
		runConfigs = runConfigs.subList(0, 1);
		System.out.println("Performing " + runConfigs.size() + " runs");
		
		AkkaTargetAlgorithmEvaluatorFactory taeF = new AkkaTargetAlgorithmEvaluatorFactory();
		
		TargetAlgorithmEvaluator tae = taeF.getTargetAlgorithmEvaluator();
		
		TargetAlgorithmEvaluatorRunObserver taeRunObserver = new TargetAlgorithmEvaluatorRunObserver()
		{

			@Override
			public void currentStatus(List<? extends AlgorithmRunResult> runs) {
				
				double sum = 0;
				
				for(AlgorithmRunResult result:runs)
				{
					System.out.println(result);
					sum+= result.getRuntime();
				}
				
				if(sum > 25)
				{
					System.err.println("Killing");
					for(AlgorithmRunResult result: runs)
					{
						
						result.kill();
					}
				}
			}
			
		};
		List<AlgorithmRunResult> runs = tae.evaluateRun(runConfigs, taeRunObserver);
		
		
		for(AlgorithmRunResult run : runs)
		{
			ParameterConfiguration config  = run.getAlgorithmRunConfiguration().getParameterConfiguration();
			
			if(!run.getRunStatus().equals(RunStatus.KILLED))
			{
				assertDEquals(config.get("runtime"), run.getRuntime(), 0.1);
				assertDEquals(config.get("runlength"), run.getRunLength(), 0.1);
				assertDEquals(config.get("quality"), run.getQuality(), 0.1);
				assertDEquals(config.get("seed"), run.getResultSeed(), 0.1);
				assertEquals(config.get("solved"), run.getRunStatus().name());
				//This executor should not have any additional run data
				assertEquals("",run.getAdditionalRunData());
			}
		}
			
		
		System.out.println("Shutting Down");
		tae.notifyShutdown();
		
		
	}
	@After
	public void afterTest()
	{
		if(t != null)
		{
			t.interrupt();
		}
		if(proc != null)
		{
			proc.destroy();
			try {
				proc.waitFor();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		
	}
	
	public void assertDEquals(String d1, double d2, double delta)
	{
		assertDEquals(Double.valueOf(d1), d2, delta);
	}
	public void assertDEquals(String d1, String d2, double delta)
	{
		assertDEquals(Double.valueOf(d1), Double.valueOf(d2), delta);
	}
	
	
	public void assertDEquals(double d1, double d2, double delta)
	{
		if(d1 - d2 > delta) throw new AssertionError("Expected "  + (d1 - d2)+ " < " + delta);
		if(d2 - d1 > delta) throw new AssertionError("Expected "  + (d1 - d2)+ " < " + delta);
		
	}
}
