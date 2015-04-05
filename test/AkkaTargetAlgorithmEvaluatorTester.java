import static org.junit.Assert.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.ProcessBuilder.Redirect;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.*;

import com.google.common.io.Files;
import com.google.common.util.concurrent.AtomicDouble;

import ca.ubc.cs.beta.TestHelper;
import ca.ubc.cs.beta.aeatk.algorithmexecutionconfiguration.AlgorithmExecutionConfiguration;
import ca.ubc.cs.beta.aeatk.algorithmrunconfiguration.AlgorithmRunConfiguration;
import ca.ubc.cs.beta.aeatk.algorithmrunresult.AlgorithmRunResult;
import ca.ubc.cs.beta.aeatk.concurrent.threadfactory.SequentiallyNamedThreadFactory;
import ca.ubc.cs.beta.aeatk.misc.debug.DebugUtil;
import ca.ubc.cs.beta.aeatk.misc.watch.AutoStartStopWatch;
import ca.ubc.cs.beta.aeatk.misc.watch.StopWatch;
import ca.ubc.cs.beta.aeatk.parameterconfigurationspace.ParameterConfiguration;
import ca.ubc.cs.beta.aeatk.parameterconfigurationspace.ParameterConfigurationSpace;
import ca.ubc.cs.beta.aeatk.probleminstance.ProblemInstance;
import ca.ubc.cs.beta.aeatk.probleminstance.ProblemInstanceSeedPair;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.TargetAlgorithmEvaluator;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.TargetAlgorithmEvaluatorCallback;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.TargetAlgorithmEvaluatorFactory;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.TargetAlgorithmEvaluatorRunObserver;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.executors.AkkaWorkerExecutor;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.tae.AkkaTargetAlgorithmEvaluatorFactory;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.tae.AkkaTargetAlgorithmEvaluatorOptions;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.base.cli.CommandLineAlgorithmRun;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.decorators.functionality.OutstandingEvaluationsTargetAlgorithmEvaluatorDecorator;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.init.TargetAlgorithmEvaluatorBuilder;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.init.TargetAlgorithmEvaluatorLoader;
import ca.ubc.cs.beta.targetalgorithmevaluator.EchoTargetAlgorithmEvaluatorFactory;
import ca.ubc.cs.beta.targetalgorithmevaluator.EchoTargetAlgorithmEvaluatorOptions;
import ca.ubc.cs.beta.targetalgorithmevaluator.SleepyParamEchoExecutor;
import ec.util.MersenneTwister;


public class AkkaTargetAlgorithmEvaluatorTester {

	List<Process> processes = new ArrayList<>();
	private ParameterConfigurationSpace configSpace;
	private AlgorithmExecutionConfiguration execConfig;
	private MersenneTwister rand;
	
	
	public Process startWorker(int id)
	{
		return startWorker(id, "");
	}
	public Process startWorker(final int id, String args)
	{
		
		//" --akka-worker-id " + id
		String fullWorkerCall = "java -cp " +  System.getProperty("java.class.path") + " " + AkkaWorkerExecutor.class.getCanonicalName()  + " " + args;
		

		ProcessBuilder pb = new ProcessBuilder();
		
		
		//pb.redirectErrorStream(true);
		//pb.redirectOutput(Redirect.INHERIT);
		pb.command(fullWorkerCall.split("\\s+"));
		
		Process p = null;
		try {
			p = pb.start();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		final Process proc = p;
		Runnable run = new Runnable()
		{

			@Override
			public void run() {
				try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream())))
				{
					String line = null;
					while( (line = reader.readLine()) != null)
					{
						System.out.println(id + ">" + line);
					}
				} catch (IOException e) {
					//e.printStackTrace();
				}
			}
			
			
		};
		Thread t = new Thread(run);
		t.setDaemon(true);
		t.start();
		
		run = new Runnable()
		{

			@Override
			public void run() {
				try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getErrorStream())))
				{
					String line = null;
					while( (line = reader.readLine()) != null)
					{
						System.err.println(id + ">" + line);
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			
			
		};
		t = new Thread(run);
		t.setDaemon(true);
		t.start();
		
		
		
		processes.add(p);
		

		/**pb.command(fullWorkerCall2.split("\\s+"));
		
		processes.add(pb.start());
		/
		*/
		return p;
		
		
	}
	
	@Before
	public void beforeTest()
	{
		File paramFile = TestHelper.getTestFile("paramFiles/paramEchoParamFileWithKilled.txt");
		configSpace = new ParameterConfigurationSpace(paramFile);
		execConfig = new AlgorithmExecutionConfiguration("ignore", System.getProperty("user.dir"), configSpace, false, false, 500);
		rand = new MersenneTwister();
		
		
		URL url = this.getClass().getClassLoader().getResource("restart.sh");
		try {
			Process p = Runtime.getRuntime().exec(url.getFile());
			p.waitFor();
			
			p.destroy();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	@Test
	public void testExecutionSingleTAESingleWorker()
	{
		for(int i=0; i < 1; i++)
		{
			startWorker(i," --tae PARAMECHO --paramecho-simulate-cores 1");
		//startWorker(2," --tae PARAMECHO");
		}
		//AkkaTargetAlgorithmEvaluatorFactory taeFactory = 
		AkkaTargetAlgorithmEvaluatorOptions taeOptions = (AkkaTargetAlgorithmEvaluatorOptions) TargetAlgorithmEvaluatorLoader.getAvailableTargetAlgorithmEvaluators().get("AKKA");
		
		taeOptions.akkaClusterOptions.id = 1;
		
		TargetAlgorithmEvaluatorFactory akkaFactory = new AkkaTargetAlgorithmEvaluatorFactory();
		TargetAlgorithmEvaluator tae = akkaFactory.getTargetAlgorithmEvaluator(taeOptions);
		
		/*
		akkaFactory = new EchoTargetAlgorithmEvaluatorFactory();
		EchoTargetAlgorithmEvaluatorOptions opts = (EchoTargetAlgorithmEvaluatorOptions) akkaFactory.getOptionObject();
		
		opts.cores = 1;
		
		tae = akkaFactory.getTargetAlgorithmEvaluator(opts);
		*/
		//System.out.println(tae.toString());
		List<AlgorithmRunConfiguration> rcs = new ArrayList<>();
		
		for(int i=0; i < 10; i++)
		{
			ParameterConfiguration config = configSpace.getRandomParameterConfiguration(rand);
			config.put("solved", "SAT");
			config.put("runtime",String.valueOf(0.25)); //i/2+2
			
			AlgorithmRunConfiguration rc = new AlgorithmRunConfiguration(new ProblemInstanceSeedPair(new ProblemInstance("test"),i), 2000, config, execConfig);
			
			rcs.add(rc);
		}
		
		Collections.shuffle(rcs, new Random(25));
		double runtime = 0;
		StopWatch watch = new AutoStartStopWatch();
		
		List<AlgorithmRunResult> results = tae.evaluateRun(rcs);
		watch.stop();
		for(AlgorithmRunResult run : results)
		{
			
			ParameterConfiguration config = run.getAlgorithmRunConfiguration().getParameterConfiguration();
			
			System.out.println(config.getFormattedParameterString() + "=>" + run.getResultLine());
			
			
			assertDEquals(config.get("runtime"), run.getRuntime(), 0.1);
			assertDEquals(config.get("runlength"), run.getRunLength(), 0.1);
			assertDEquals(config.get("quality"), run.getQuality(), 0.1);
			
			assertEquals(config.get("solved"), run.getRunStatus().name());
			
			runtime += run.getRuntime();
		}
		
		System.out.println("Runtime: " + runtime + " seconds" + " actual time: " + watch.time() / 1000.0 + " seconds" + " expected:" + runtime  + " seconds") ;
		
	}
	
	
	@Test
	public void testExecutionSingleTAESingleWorkerObserver()
	{
		for(int i=0; i < 1; i++)
		{
			startWorker(i," --tae PARAMECHO --paramecho-simulate-cores 1");
		//startWorker(2," --tae PARAMECHO");
		}
		//AkkaTargetAlgorithmEvaluatorFactory taeFactory = 
		AkkaTargetAlgorithmEvaluatorOptions taeOptions = (AkkaTargetAlgorithmEvaluatorOptions) TargetAlgorithmEvaluatorLoader.getAvailableTargetAlgorithmEvaluators().get("AKKA");
		
		taeOptions.akkaClusterOptions.id = 1;
		
		TargetAlgorithmEvaluatorFactory akkaFactory = new AkkaTargetAlgorithmEvaluatorFactory();
		TargetAlgorithmEvaluator tae = akkaFactory.getTargetAlgorithmEvaluator(taeOptions);
		
		/*
		akkaFactory = new EchoTargetAlgorithmEvaluatorFactory();
		EchoTargetAlgorithmEvaluatorOptions opts = (EchoTargetAlgorithmEvaluatorOptions) akkaFactory.getOptionObject();
		
		opts.cores = 1;
		
		tae = akkaFactory.getTargetAlgorithmEvaluator(opts);
		*/
		//System.out.println(tae.toString());
		List<AlgorithmRunConfiguration> rcs = new ArrayList<>();
		
		for(int i=0; i < 40; i++)
		{
			ParameterConfiguration config = configSpace.getRandomParameterConfiguration(rand);
			config.put("solved", "SAT");
			config.put("runtime",String.valueOf(60)); //i/2+2
			
			AlgorithmRunConfiguration rc = new AlgorithmRunConfiguration(new ProblemInstanceSeedPair(new ProblemInstance("test"),i), 2000, config, execConfig);
			
			rcs.add(rc);
		}
		
		Collections.shuffle(rcs, new Random(25));
		double runtime = 0;
		StopWatch watch = new AutoStartStopWatch();
		
		TargetAlgorithmEvaluatorRunObserver obs = new TargetAlgorithmEvaluatorRunObserver()
		{

			@Override
			public void currentStatus(List<? extends AlgorithmRunResult> runs) {
				System.out.println("Runs:" + runs.size() );
				boolean killAll = false;
				for(AlgorithmRunResult run : runs)
				{
					if(run.getRuntime() > 5)
					{
						killAll = true;
					}
				}
				
				if(killAll)
				{
					for(AlgorithmRunResult run : runs)
					{
						run.kill();
					}
				}
			}
			
		};
		List<AlgorithmRunResult> results = tae.evaluateRun(rcs,obs);
		watch.stop();
		for(AlgorithmRunResult run : results)
		{
			
			ParameterConfiguration config = run.getAlgorithmRunConfiguration().getParameterConfiguration();
			
			System.out.println(config.getFormattedParameterString() + "=>" + run.getResultLine());
			
			
			/*assertDEquals(config.get("runtime"), run.getRuntime(), 0.1);
			assertDEquals(config.get("runlength"), run.getRunLength(), 0.1);
			assertDEquals(config.get("quality"), run.getQuality(), 0.1);
			
			assertEquals(config.get("solved"), run.getRunStatus().name());
			*/
			runtime += run.getRuntime();
		}
		
		System.out.println("Runtime: " + runtime + " seconds" + " actual time: " + watch.time() / 1000.0 + " seconds" + " expected:" + runtime  + " seconds") ;
		
	}
	
	
	@Test
	/**
	 * This tests that runs are processed in FIFO order. 
	 */
	public void testFIFOBatchProcessing()
	{
		
		
		
		File tmpDir = Files.createTempDir();
		
		ScheduledExecutorService execService = Executors.newScheduledThreadPool(1);
		
		execService.scheduleAtFixedRate(new Runnable(){

			@Override
			public void run() {
				System.err.print(".");
				System.err.flush();
				
			}
			
		}, 0, 250, TimeUnit.MILLISECONDS);
		for(int i=0; i < 4; i++)
		{
			startWorker(i," --tae PARAMECHO --paramecho-simulate-cores 1 " + " --akka-worker-dir " + tmpDir.getAbsolutePath());
		}
		//AkkaTargetAlgorithmEvaluatorFactory taeFactory = 
		AkkaTargetAlgorithmEvaluatorOptions taeOptions = (AkkaTargetAlgorithmEvaluatorOptions) TargetAlgorithmEvaluatorLoader.getAvailableTargetAlgorithmEvaluators().get("AKKA");
		
		taeOptions.dir = tmpDir;
		taeOptions.observerFrequency = 2500;
		taeOptions.printStatusFrequency = 15000;
		TargetAlgorithmEvaluatorFactory akkaFactory = new AkkaTargetAlgorithmEvaluatorFactory();
		TargetAlgorithmEvaluator tae = akkaFactory.getTargetAlgorithmEvaluator(taeOptions);
		
		tae = new OutstandingEvaluationsTargetAlgorithmEvaluatorDecorator(tae);
		/*
		akkaFactory = new EchoTargetAlgorithmEvaluatorFactory();
		EchoTargetAlgorithmEvaluatorOptions opts = (EchoTargetAlgorithmEvaluatorOptions) akkaFactory.getOptionObject();
		
		opts.cores = 1;
		
		tae = akkaFactory.getTargetAlgorithmEvaluator(opts);
		*/
		//System.out.println(tae.toString());
		List<AlgorithmRunConfiguration> rcs = new ArrayList<>();
		
		for(int i=0; i < 100; i++)
		{
			ParameterConfiguration config = configSpace.getRandomParameterConfiguration(rand);
			config.put("solved", "SAT");
			config.put("seed",""+i);
			//config.put("runtime",String.valueOf((i%4)/2+1)); 
			//config.put("runtime",String.valueOf(2*i + ""));
			config.put("runtime",String.valueOf(0.05+(i%5)));
			AlgorithmRunConfiguration rc = new AlgorithmRunConfiguration(new ProblemInstanceSeedPair(new ProblemInstance("test"),i), 2000, config, execConfig);
			
			rcs.add(rc);
		}
		
		//Collections.shuffle(rcs, new Random(25));

	
		
		TargetAlgorithmEvaluatorRunObserver obs = new TargetAlgorithmEvaluatorRunObserver()
		{

			@Override
			public synchronized void currentStatus(List<? extends AlgorithmRunResult> runs) {
				//System.out.println("Observation:");
				for(AlgorithmRunResult run : runs)
				{
					ParameterConfiguration config = run.getAlgorithmRunConfiguration().getParameterConfiguration();
					
					//System.out.println(config.getFormattedParameterString() + "=>" + run.getResultLine());
					
					run.kill();
				}

			}
			
		};
		
		 final AtomicDouble runtime = new AtomicDouble();
		
		
		TargetAlgorithmEvaluatorCallback cb = new TargetAlgorithmEvaluatorCallback()
		{

			@Override
			public synchronized void onSuccess(List<AlgorithmRunResult> runs) {
				System.out.println("Results:");
				for(AlgorithmRunResult run : runs)
				{
					ParameterConfiguration config = run.getAlgorithmRunConfiguration().getParameterConfiguration();
					
					System.out.println(config.getFormattedParameterString() + "=>" + run.getResultLine());
					runtime.addAndGet(run.getRuntime());
				}
				
			}

			@Override
			public void onFailure(RuntimeException e) {
				e.printStackTrace();
				
			}
			
		};
		
		//Was 15
		for(int j=0; j < 15; j++)
		{
			
			tae.evaluateRunsAsync(rcs.subList(4*j, 4*j+4), cb,obs);
			
		}
		StopWatch watch = new AutoStartStopWatch();
		tae.waitForOutstandingEvaluations();
		watch.stop();
		
		System.out.println("Processing all runs took: " + watch.time()/1000.0 + " seconds, reported: " + runtime.get() + " seconds");
		
		try 
		{
			tae.notifyShutdown();
			Thread.sleep(2500);
		} catch(RuntimeException e)
		{
			e.printStackTrace();
		} catch (InterruptedException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		
	}
	
	@Test
	/**
	 * This tests that runs are processed in FIFO order. 
	 */
	public void testWorkerIdleTimeLimit()
	{
		
		
		
		File tmpDir = Files.createTempDir();
		
	
		Process p = startWorker(1," --tae PARAMECHO --paramecho-simulate-cores 1 " + "--idle-limit 5 --akka-worker-dir " + tmpDir.getAbsolutePath());
		
		StopWatch watch = new AutoStartStopWatch();
		while(true)
		{
			try
			{
				p.exitValue();
				break;
			} catch(IllegalThreadStateException e)
			{
				
				if(watch.time() > 7500)
				{
					fail("Expected that the process would have stopped by now: " + watch.time()  + " ms");
				}
				try {
					Thread.sleep(250);
					
				} catch (InterruptedException e1) {
					Thread.currentThread().interrupt();
					return;
				}
			}
		
		
		}
		
	}
	
	@Test
	/**
	 * This tests that runs are processed in FIFO order. 
	 */
	public void testWorkerTimeLimit()
	{
		
		
		
		File tmpDir = Files.createTempDir();
		
	
		Process p = startWorker(1," --tae PARAMECHO --paramecho-simulate-cores 1 " + "--time-limit 5 --akka-worker-dir " + tmpDir.getAbsolutePath());
		
		StopWatch watch = new AutoStartStopWatch();
		while(true)
		{
			try
			{
				p.exitValue();
				break;
			} catch(IllegalThreadStateException e)
			{
				
				if(watch.time() > 7500)
				{
					fail("Expected that the process would have stopped by now: " + watch.time()  + " ms");
				}
				try {
					Thread.sleep(250);
					
				} catch (InterruptedException e1) {
					Thread.currentThread().interrupt();
					return;
				}
			}
		
		
		}
		
	}
	
	
	@Test
	/**
	 * This tests that runs are processed in FIFO order. 
	 */
	public void testStrictOrder()
	{
		
		
		ScheduledExecutorService execService = Executors.newScheduledThreadPool(1);
		
		execService.scheduleAtFixedRate(new Runnable(){

			@Override
			public void run() {
				System.err.print(".");
				System.err.flush();
				
			}
			
		}, 0, 250, TimeUnit.MILLISECONDS);
		
		File tmpDir = Files.createTempDir();
		
		for(int i=0; i < 4; i++)
		{
			startWorker(i," --tae PARAMECHO --paramecho-simulate-cores 1 --akka-id " + i + " --akka-worker-dir " + tmpDir.getAbsolutePath());
		//startWorker(2," --tae PARAMECHO");
		}
		
		try {
			Thread.sleep(1000);
		} catch (InterruptedException e1) {
			Thread.currentThread().interrupt();
		}
		
		
		//AkkaTargetAlgorithmEvaluatorFactory taeFactory = 
		AkkaTargetAlgorithmEvaluatorOptions taeOptions = (AkkaTargetAlgorithmEvaluatorOptions) TargetAlgorithmEvaluatorLoader.getAvailableTargetAlgorithmEvaluators().get("AKKA");
		
		
		taeOptions.akkaClusterOptions.id = 100;
		taeOptions.observerFrequency = 2500;
		taeOptions.dir = tmpDir;
		TargetAlgorithmEvaluatorFactory akkaFactory = new AkkaTargetAlgorithmEvaluatorFactory();
		TargetAlgorithmEvaluator tae = akkaFactory.getTargetAlgorithmEvaluator(taeOptions);
		
		tae = new OutstandingEvaluationsTargetAlgorithmEvaluatorDecorator(tae);
		/*
		akkaFactory = new EchoTargetAlgorithmEvaluatorFactory();
		EchoTargetAlgorithmEvaluatorOptions opts = (EchoTargetAlgorithmEvaluatorOptions) akkaFactory.getOptionObject();
		
		opts.cores = 1;
		
		tae = akkaFactory.getTargetAlgorithmEvaluator(opts);
		*/
		//System.out.println(tae.toString());
		List<AlgorithmRunConfiguration> rcs = new ArrayList<>();
		
		for(int i=0; i < 100; i++)
		{
			ParameterConfiguration config = configSpace.getRandomParameterConfiguration(rand);
			config.put("solved", "SAT");
			config.put("seed",""+i);
			//config.put("runtime",String.valueOf((i%4)/2+1)); 
			//config.put("runtime",String.valueOf(2*i + ""));
			config.put("runtime",String.valueOf(0.05+(i%5)));
			AlgorithmRunConfiguration rc = new AlgorithmRunConfiguration(new ProblemInstanceSeedPair(new ProblemInstance("test"),i), 2000, config, execConfig);
			
			rcs.add(rc);
		}
		
		//Collections.shuffle(rcs, new Random(25));

	
		
		TargetAlgorithmEvaluatorRunObserver obs = new TargetAlgorithmEvaluatorRunObserver()
		{

			@Override
			public synchronized void currentStatus(List<? extends AlgorithmRunResult> runs) {
				//System.out.println("Observation:");
				for(AlgorithmRunResult run : runs)
				{
					ParameterConfiguration config = run.getAlgorithmRunConfiguration().getParameterConfiguration();
					
					//System.out.println(config.getFormattedParameterString() + "=>" + run.getResultLine());
					
					run.kill();
				}

			}
			
		};
		
		 final AtomicDouble runtime = new AtomicDouble();
		
		
		TargetAlgorithmEvaluatorCallback cb = new TargetAlgorithmEvaluatorCallback()
		{

			@Override
			public synchronized void onSuccess(List<AlgorithmRunResult> runs) {
				System.out.println("Results:");
				for(AlgorithmRunResult run : runs)
				{
					ParameterConfiguration config = run.getAlgorithmRunConfiguration().getParameterConfiguration();
					
					System.out.println(config.getFormattedParameterString() + "=>" + run.getResultLine());
					runtime.addAndGet(run.getRuntime());
				}
				
			}

			@Override
			public void onFailure(RuntimeException e) {
				e.printStackTrace();
				
			}
			
		};
		for(int j=0; j < 15; j++)
		{
			
			tae.evaluateRunsAsync(rcs.subList(4*j, 4*j+4), cb,obs);
			
		}
		StopWatch watch = new AutoStartStopWatch();
		tae.waitForOutstandingEvaluations();
		watch.stop();
		
		System.out.println("Processing all runs took: " + watch.time()/1000.0 + " seconds, reported: " + runtime.get() + " seconds");
		
	}
	
	@Test
	/**
	 * This tests that runs are processed in FIFO order. 
	 */
	public void testExplicitSeedNode()
	{
		
		
		ScheduledExecutorService execService = Executors.newScheduledThreadPool(1);
		
		execService.scheduleAtFixedRate(new Runnable(){

			@Override
			public void run() {
				System.err.print(".");
				System.err.flush();
				
			}
			
		}, 0, 250, TimeUnit.MILLISECONDS);
		
		File tmpDir = Files.createTempDir();
		
		for(int i=0; i < 4; i++)
		{
			startWorker(i," --tae PARAMECHO --paramecho-simulate-cores 1 --akka-id " + i + " --akka-worker-dir " + tmpDir.getAbsolutePath());
		//startWorker(2," --tae PARAMECHO");
		}
		
		try {
			Thread.sleep(1000);
		} catch (InterruptedException e1) {
			Thread.currentThread().interrupt();
		}
		
		
		//AkkaTargetAlgorithmEvaluatorFactory taeFactory = 
		AkkaTargetAlgorithmEvaluatorOptions taeOptions = (AkkaTargetAlgorithmEvaluatorOptions) TargetAlgorithmEvaluatorLoader.getAvailableTargetAlgorithmEvaluators().get("AKKA");
		
		
		taeOptions.akkaClusterOptions.id = 100;
		taeOptions.observerFrequency = 2500;
		taeOptions.dir = tmpDir;
		TargetAlgorithmEvaluatorFactory akkaFactory = new AkkaTargetAlgorithmEvaluatorFactory();
		TargetAlgorithmEvaluator tae = akkaFactory.getTargetAlgorithmEvaluator(taeOptions);
		
		tae = new OutstandingEvaluationsTargetAlgorithmEvaluatorDecorator(tae);
		/*
		akkaFactory = new EchoTargetAlgorithmEvaluatorFactory();
		EchoTargetAlgorithmEvaluatorOptions opts = (EchoTargetAlgorithmEvaluatorOptions) akkaFactory.getOptionObject();
		
		opts.cores = 1;
		
		tae = akkaFactory.getTargetAlgorithmEvaluator(opts);
		*/
		//System.out.println(tae.toString());
		List<AlgorithmRunConfiguration> rcs = new ArrayList<>();
		
		for(int i=0; i < 100; i++)
		{
			ParameterConfiguration config = configSpace.getRandomParameterConfiguration(rand);
			config.put("solved", "SAT");
			config.put("seed",""+i);
			//config.put("runtime",String.valueOf((i%4)/2+1)); 
			//config.put("runtime",String.valueOf(2*i + ""));
			config.put("runtime",String.valueOf(0.05+(i%5)));
			AlgorithmRunConfiguration rc = new AlgorithmRunConfiguration(new ProblemInstanceSeedPair(new ProblemInstance("test"),i), 2000, config, execConfig);
			
			rcs.add(rc);
		}
		
		//Collections.shuffle(rcs, new Random(25));

	
		
		TargetAlgorithmEvaluatorRunObserver obs = new TargetAlgorithmEvaluatorRunObserver()
		{

			@Override
			public synchronized void currentStatus(List<? extends AlgorithmRunResult> runs) {
				//System.out.println("Observation:");
				for(AlgorithmRunResult run : runs)
				{
					ParameterConfiguration config = run.getAlgorithmRunConfiguration().getParameterConfiguration();
					
					//System.out.println(config.getFormattedParameterString() + "=>" + run.getResultLine());
					
					run.kill();
				}

			}
			
		};
		
		 final AtomicDouble runtime = new AtomicDouble();
		
		
		TargetAlgorithmEvaluatorCallback cb = new TargetAlgorithmEvaluatorCallback()
		{

			@Override
			public synchronized void onSuccess(List<AlgorithmRunResult> runs) {
				System.out.println("Results:");
				for(AlgorithmRunResult run : runs)
				{
					ParameterConfiguration config = run.getAlgorithmRunConfiguration().getParameterConfiguration();
					
					System.out.println(config.getFormattedParameterString() + "=>" + run.getResultLine());
					runtime.addAndGet(run.getRuntime());
				}
				
			}

			@Override
			public void onFailure(RuntimeException e) {
				e.printStackTrace();
				
			}
			
		};
		for(int j=0; j < 15; j++)
		{
			
			tae.evaluateRunsAsync(rcs.subList(4*j, 4*j+4), cb,obs);
			
		}
		StopWatch watch = new AutoStartStopWatch();
		tae.waitForOutstandingEvaluations();
		watch.stop();
		
		System.out.println("Processing all runs took: " + watch.time()/1000.0 + " seconds, reported: " + runtime.get() + " seconds");
		
	}
	
	
	
	
	@Test
	public void testExecutionSingleTAE()
	{
		for(int i=0; i < 4; i++)
		{
			startWorker(i," --tae PARAMECHO --paramecho-simulate-cores 1");
		//startWorker(2," --tae PARAMECHO");
		}
		//AkkaTargetAlgorithmEvaluatorFactory taeFactory = 
		AkkaTargetAlgorithmEvaluatorOptions taeOptions = (AkkaTargetAlgorithmEvaluatorOptions) TargetAlgorithmEvaluatorLoader.getAvailableTargetAlgorithmEvaluators().get("AKKA");
		
		taeOptions.akkaClusterOptions.id = 100;
		
		TargetAlgorithmEvaluatorFactory akkaFactory = new AkkaTargetAlgorithmEvaluatorFactory();
		TargetAlgorithmEvaluator tae = akkaFactory.getTargetAlgorithmEvaluator(taeOptions);
		
		
		
		List<AlgorithmRunConfiguration> rcs = new ArrayList<>();
		
		for(int i=0; i < 25; i++)
		{
			ParameterConfiguration config = configSpace.getRandomParameterConfiguration(rand);
			config.put("solved", "SAT");
			config.put("runtime",String.valueOf(2)); //i/2+2
			
			AlgorithmRunConfiguration rc = new AlgorithmRunConfiguration(new ProblemInstanceSeedPair(new ProblemInstance("test"),0L), 2000, config, execConfig);
			
			rcs.add(rc);
		}
		
		Collections.shuffle(rcs, new Random(25));
		double runtime = 0;
		StopWatch watch = new AutoStartStopWatch();
		
		List<AlgorithmRunResult> results = tae.evaluateRun(rcs);
		watch.stop();
		for(AlgorithmRunResult run : results)
		{
			
			ParameterConfiguration config = run.getAlgorithmRunConfiguration().getParameterConfiguration();
			
			System.out.println(config.getFormattedParameterString() + "=>" + run.getResultLine());
			
			
			assertDEquals(config.get("runtime"), run.getRuntime(), 0.1);
			assertDEquals(config.get("runlength"), run.getRunLength(), 0.1);
			assertDEquals(config.get("quality"), run.getQuality(), 0.1);
			
			assertEquals(config.get("solved"), run.getRunStatus().name());
			
			runtime += run.getRuntime();
		}
		
		System.out.println("Runtime: " + runtime + " seconds" + " actual time: " + watch.time() / 1000.0 + " seconds" + " expected:" + runtime / 4.0 + " seconds") ;
		
	}
	
	@Test
	public void testExecutionTwoTAE()
	{
		for(int i=0; i < 4; i++)
		{
			startWorker(i," --tae PARAMECHO --paramecho-simulate-cores 1");
		//startWorker(2," --tae PARAMECHO");
		}
		//AkkaTargetAlgorithmEvaluatorFactory taeFactory = 
		AkkaTargetAlgorithmEvaluatorOptions taeOptions = (AkkaTargetAlgorithmEvaluatorOptions) TargetAlgorithmEvaluatorLoader.getAvailableTargetAlgorithmEvaluators().get("AKKA");
		AkkaTargetAlgorithmEvaluatorOptions taeOptions2 = (AkkaTargetAlgorithmEvaluatorOptions) TargetAlgorithmEvaluatorLoader.getAvailableTargetAlgorithmEvaluators().get("AKKA");
		taeOptions.akkaClusterOptions.id = 100;
		taeOptions2.akkaClusterOptions.id = 101;
		
		TargetAlgorithmEvaluatorFactory akkaFactory = new AkkaTargetAlgorithmEvaluatorFactory();
		
		StopWatch autoStartStopWatch = new AutoStartStopWatch();
		
		TargetAlgorithmEvaluator tae = new OutstandingEvaluationsTargetAlgorithmEvaluatorDecorator(akkaFactory.getTargetAlgorithmEvaluator(taeOptions));
		System.err.println("Start up time: " + autoStartStopWatch.time() / 1000.0  + " s");
		
		
		autoStartStopWatch = new AutoStartStopWatch();
		TargetAlgorithmEvaluator tae2 = new OutstandingEvaluationsTargetAlgorithmEvaluatorDecorator(akkaFactory.getTargetAlgorithmEvaluator(taeOptions2));
		
		System.err.println("Start up time: " + autoStartStopWatch.time() / 1000.0  + " s");
		
		List<AlgorithmRunConfiguration> rcs = new ArrayList<>();
		
		for(int i=0; i < 25; i++)
		{
			ParameterConfiguration config = configSpace.getRandomParameterConfiguration(rand);
			config.put("solved", "SAT");
			config.put("runtime",String.valueOf(2)); //i/2+2
			
			AlgorithmRunConfiguration rc = new AlgorithmRunConfiguration(new ProblemInstanceSeedPair(new ProblemInstance("test"),0L), 2000, config, execConfig);
			
			rcs.add(rc);
		}
		
		Collections.shuffle(rcs, new Random(25));
		double runtime = 0;
		StopWatch watch = new AutoStartStopWatch();
		
		final AtomicReference<List<AlgorithmRunResult>> results1 = new AtomicReference<>();
		final AtomicReference<List<AlgorithmRunResult>> results2 = new AtomicReference<>();
		
		
		tae.evaluateRunsAsync(rcs.subList(0,12), new TargetAlgorithmEvaluatorCallback(){

			@Override
			public void onSuccess(List<AlgorithmRunResult> runs) {
				results1.set(runs);
				
			}

			@Override
			public void onFailure(RuntimeException e) {
				e.printStackTrace();
			}
			
		});

		tae2.evaluateRunsAsync(rcs.subList(0,12), new TargetAlgorithmEvaluatorCallback(){

			@Override
			public void onSuccess(List<AlgorithmRunResult> runs) {
				results2.set(runs);
				
			}

			@Override
			public void onFailure(RuntimeException e) {
				e.printStackTrace();
			}
			
		});
		
		tae.waitForOutstandingEvaluations();
		System.err.println("First Runs Done");
		tae2.waitForOutstandingEvaluations();
		System.err.println("Second Runs Done");
		
		watch.stop();
		for(AlgorithmRunResult run : results1.get())
		{
			
			ParameterConfiguration config = run.getAlgorithmRunConfiguration().getParameterConfiguration();
			
			System.out.println(config.getFormattedParameterString() + "=>" + run.getResultLine());
			
			
			assertDEquals(config.get("runtime"), run.getRuntime(), 0.1);
			assertDEquals(config.get("runlength"), run.getRunLength(), 0.1);
			assertDEquals(config.get("quality"), run.getQuality(), 0.1);
			
			assertEquals(config.get("solved"), run.getRunStatus().name());
			
			runtime += run.getRuntime();
		}
		runtime = 0;
		for(AlgorithmRunResult run : results2.get())
		{
			
			ParameterConfiguration config = run.getAlgorithmRunConfiguration().getParameterConfiguration();
			
			System.out.println(config.getFormattedParameterString() + "=>" + run.getResultLine());
			
			
			assertDEquals(config.get("runtime"), run.getRuntime(), 0.1);
			assertDEquals(config.get("runlength"), run.getRunLength(), 0.1);
			assertDEquals(config.get("quality"), run.getQuality(), 0.1);
			
			assertEquals(config.get("solved"), run.getRunStatus().name());
			
			runtime += run.getRuntime();
		}
		
		System.out.println("Runtime: " + runtime + " seconds" + " actual time: " + watch.time() / 1000.0 + " seconds" + " expected:" + runtime / 2.0 + " seconds") ;
		
	}
	
	
	@Test
	public void testSyncWorkerTAEShutdown()
	{
		/*
		for(int i=0; i < 4; i++)
		{
			startWorker(i," --tae PARAMECHO --paramecho-simulate-cores 1");
		//startWorker(2," --tae PARAMECHO");
		}*/
		//AkkaTargetAlgorithmEvaluatorFactory taeFactory = 
		AkkaTargetAlgorithmEvaluatorOptions taeOptions = (AkkaTargetAlgorithmEvaluatorOptions) TargetAlgorithmEvaluatorLoader.getAvailableTargetAlgorithmEvaluators().get("AKKA");
	
		
		taeOptions.syncWorker = true;
		
		taeOptions.dir = Files.createTempDir();
		final List<TargetAlgorithmEvaluator> taes = new ArrayList<>();
		for(int i=0; i < 2; i++)
		{
			TargetAlgorithmEvaluatorFactory akkaFactory = new AkkaTargetAlgorithmEvaluatorFactory();
			
			StopWatch autoStartStopWatch = new AutoStartStopWatch();
			
			TargetAlgorithmEvaluator tae = new OutstandingEvaluationsTargetAlgorithmEvaluatorDecorator(akkaFactory.getTargetAlgorithmEvaluator(taeOptions));
			System.err.println("Start up time: " + autoStartStopWatch.time() / 1000.0  + " s");
			
			taes.add(tae);
		}
		
		StringBuilder b = new StringBuilder();
		b.append("java -cp ");
		b.append(System.getProperty("java.class.path"));
		b.append(" ");
		b.append(SleepyParamEchoExecutor.class.getCanonicalName());
		
		File paramFile = TestHelper.getTestFile("paramFiles/paramEchoParamFile.txt");
		ParameterConfigurationSpace configSpace = new ParameterConfigurationSpace(paramFile);
		
		
		execConfig = new AlgorithmExecutionConfiguration(b.toString(), System.getProperty("user.dir"), configSpace, false, false, 0.01);
		
	
		List<AlgorithmRunConfiguration> rcs = new ArrayList<>();
		
		for(int i=0; i < 10; i++)
		{
			ParameterConfiguration config = configSpace.getRandomParameterConfiguration(rand);
			config.put("solved", "SAT");
			config.put("runtime",String.valueOf(2)); //i/2+2
			
			AlgorithmRunConfiguration rc = new AlgorithmRunConfiguration(new ProblemInstanceSeedPair(new ProblemInstance("test"),0L), 2000, config, execConfig);
			
			rcs.add(rc);
		}
		
		
		System.err.println("Starting runs");
		ExecutorService execService = Executors.newCachedThreadPool(new SequentiallyNamedThreadFactory(DebugUtil.getCurrentMethodName() + " tae shutdown"));
		for( int i=1; i < taes.size(); i++)
		{
			final int id = i;
			Runnable run = new Runnable()
			{

				@Override
				public void run() {
					//taes.get(id).notifyShutdown();
				}
				
			};
			execService.execute(run);
		}
		AutoStartStopWatch watch = new AutoStartStopWatch();
		
		taes.get(0).evaluateRun(rcs);
		
		System.err.println("Run Time: " + watch.time() / 1000.0  + " s");
		
		
		
		System.err.println("If stuck they you probably didn't shutdown all TAEs");
		for(int i=0; i < taes.size(); i++)
		{
			taes.get(i).notifyShutdown();
		}
		
		
		/*
		autoStartStopWatch = new AutoStartStopWatch();
		TargetAlgorithmEvaluator tae2 = new OutstandingEvaluationsTargetAlgorithmEvaluatorDecorator(akkaFactory.getTargetAlgorithmEvaluator(taeOptions2));
		
		System.err.println("Start up time: " + autoStartStopWatch.time() / 1000.0  + " s");
		
		
		
		Collections.shuffle(rcs, new Random(25));
		double runtime = 0;
		StopWatch watch = new AutoStartStopWatch();
		
		final AtomicReference<List<AlgorithmRunResult>> results1 = new AtomicReference<>();
		final AtomicReference<List<AlgorithmRunResult>> results2 = new AtomicReference<>();
		
		
		tae.evaluateRunsAsync(rcs.subList(0,12), new TargetAlgorithmEvaluatorCallback(){

			@Override
			public void onSuccess(List<AlgorithmRunResult> runs) {
				results1.set(runs);
				
			}

			@Override
			public void onFailure(RuntimeException e) {
				e.printStackTrace();
			}
			
		});

		tae2.evaluateRunsAsync(rcs.subList(0,12), new TargetAlgorithmEvaluatorCallback(){

			@Override
			public void onSuccess(List<AlgorithmRunResult> runs) {
				results2.set(runs);
				
			}

			@Override
			public void onFailure(RuntimeException e) {
				e.printStackTrace();
			}
			
		});
		
		tae.waitForOutstandingEvaluations();
		System.err.println("First Runs Done");
		tae2.waitForOutstandingEvaluations();
		System.err.println("Second Runs Done");
		
		watch.stop();
		for(AlgorithmRunResult run : results1.get())
		{
			
			ParameterConfiguration config = run.getAlgorithmRunConfiguration().getParameterConfiguration();
			
			System.out.println(config.getFormattedParameterString() + "=>" + run.getResultLine());
			
			
			assertDEquals(config.get("runtime"), run.getRuntime(), 0.1);
			assertDEquals(config.get("runlength"), run.getRunLength(), 0.1);
			assertDEquals(config.get("quality"), run.getQuality(), 0.1);
			
			assertEquals(config.get("solved"), run.getRunStatus().name());
			
			runtime += run.getRuntime();
		}
		runtime = 0;
		for(AlgorithmRunResult run : results2.get())
		{
			
			ParameterConfiguration config = run.getAlgorithmRunConfiguration().getParameterConfiguration();
			
			System.out.println(config.getFormattedParameterString() + "=>" + run.getResultLine());
			
			
			assertDEquals(config.get("runtime"), run.getRuntime(), 0.1);
			assertDEquals(config.get("runlength"), run.getRunLength(), 0.1);
			assertDEquals(config.get("quality"), run.getQuality(), 0.1);
			
			assertEquals(config.get("solved"), run.getRunStatus().name());
			
			runtime += run.getRuntime();
		}
		
		System.out.println("Runtime: " + runtime + " seconds" + " actual time: " + watch.time() / 1000.0 + " seconds" + " expected:" + runtime / 2.0 + " seconds") ;
		*/
	}
	
	
	@Test
	public void testTAEPriorityChecker()
	{
		
		ScheduledExecutorService ses = Executors.newScheduledThreadPool(1);
		
		ses.scheduleAtFixedRate(new Runnable(){

			@Override
			public void run() {
				System.err.print(".");
				System.err.flush();
				
			}
			
		}, 0, 250, TimeUnit.MILLISECONDS);

		
		
		File dir = Files.createTempDir();
		for(int i=0; i < 4; i++)
		{
			startWorker(i," --tae PARAMECHO --paramecho-simulate-cores 1 --akka-worker-dir " + dir + " --akka-notify-available-frequency " + 30);
		//startWorker(2," --tae PARAMECHO");
		}
		//AkkaTargetAlgorithmEvaluatorFactory taeFactory = 
		AkkaTargetAlgorithmEvaluatorOptions taeOptions = (AkkaTargetAlgorithmEvaluatorOptions) TargetAlgorithmEvaluatorLoader.getAvailableTargetAlgorithmEvaluators().get("AKKA");
	
		
		taeOptions.syncWorker = false;
		
		taeOptions.dir = dir;
		final List<TargetAlgorithmEvaluator> taes = new ArrayList<>();
		for(int i=0; i < 2; i++)
		{
			TargetAlgorithmEvaluatorFactory akkaFactory = new AkkaTargetAlgorithmEvaluatorFactory();
			
			StopWatch autoStartStopWatch = new AutoStartStopWatch();
			
			TargetAlgorithmEvaluator tae = new OutstandingEvaluationsTargetAlgorithmEvaluatorDecorator(akkaFactory.getTargetAlgorithmEvaluator(taeOptions));
			System.err.println("Start up time: " + autoStartStopWatch.time() / 1000.0  + " s");
			
			taes.add(tae);
		}
		
		StringBuilder b = new StringBuilder();
		b.append("java -cp ");
		b.append(System.getProperty("java.class.path"));
		b.append(" ");
		b.append(SleepyParamEchoExecutor.class.getCanonicalName());
		
		File paramFile = TestHelper.getTestFile("paramFiles/paramEchoParamFile.txt");
		ParameterConfigurationSpace configSpace = new ParameterConfigurationSpace(paramFile);
		
		
		execConfig = new AlgorithmExecutionConfiguration(b.toString(), System.getProperty("user.dir"), configSpace, false, false, 0.01);
		
	
		final List<List<AlgorithmRunConfiguration>> randomConfigurations = new ArrayList<>();
		final List<List<AlgorithmRunConfiguration>> defaultConfigurations = new ArrayList<>();
		
		ParameterConfiguration defaultConfig = configSpace.getDefaultConfiguration();
		defaultConfig.put("solved", "SAT");
		defaultConfig.put("runtime", "1.0");
		int seed = 1;
		for(int i=0; i < 12; i++)
		{
			List<AlgorithmRunConfiguration> randList = new ArrayList<>();
			List<AlgorithmRunConfiguration> defList = new ArrayList<>();
		
			randomConfigurations.add(randList);
			defaultConfigurations.add(defList);
			ParameterConfiguration config = configSpace.getRandomParameterConfiguration(rand);
			
			for(int j=0; j <=i; j++)
			{
				config.put("solved", "SAT");
				config.put("runtime", "1.0");
				
				AlgorithmRunConfiguration rc = new AlgorithmRunConfiguration(new ProblemInstanceSeedPair(new ProblemInstance("test"),seed++), 2000, config, execConfig);
				
				randList.add(rc);
				
				AlgorithmRunConfiguration rc2 = new AlgorithmRunConfiguration(new ProblemInstanceSeedPair(new ProblemInstance("test"),seed++), 2000, defaultConfig, execConfig);
				defList.add(rc2);	
			}
			
		}
		
		
		System.err.println("Starting runs");
		ExecutorService execService = Executors.newFixedThreadPool(5, new SequentiallyNamedThreadFactory(DebugUtil.getCurrentMethodName() + " tae shutdown"));
		
	
		taes.get(1).evaluateRun(randomConfigurations.get(0));
		taes.get(1).evaluateRun(randomConfigurations.get(1));
		
		
		final TargetAlgorithmEvaluatorCallback cb = new TargetAlgorithmEvaluatorCallback()
		{

			@Override
			public void onSuccess(List<AlgorithmRunResult> runs) {
				System.out.println("YAY");
				for(AlgorithmRunResult res : runs)
				{
					//System.out.println(CommandLineAlgorithmRun.getTargetAlgorithmExecutionCommandAsString(res.getAlgorithmRunConfiguration()));
					//System.out.println(res);
				}
			}

			@Override
			public void onFailure(RuntimeException e) {
				e.printStackTrace();
				
			}
			
		};
		
	
		Runnable run = new Runnable()
		{

			@Override
			public void run() {
				TargetAlgorithmEvaluator tae = taes.get(0);
				
				
				System.err.print("Starting to submit default runs");
				for(List<AlgorithmRunConfiguration> rcs : defaultConfigurations)
				{
					tae.evaluateRunsAsync(rcs, cb);
				}
				System.err.println("All default runs submitted");
				tae.waitForOutstandingEvaluations();
				System.err.println("Default Runs Done");
				//tae.notifyShutdown();
				
			}
			
		};
		
		execService.execute(run);

		
		try {
			Thread.sleep(2500);
		} catch (InterruptedException e2) {
			// TODO Auto-generated catch block
			e2.printStackTrace();
		}
		Runnable run2 = new Runnable()
		{

			@Override
			public void run() {
				TargetAlgorithmEvaluator tae = taes.get(1);
				
				
				System.err.print("Starting to submit random runs");
				for(List<AlgorithmRunConfiguration> rcs : randomConfigurations.subList(2,10))
				{
					tae.evaluateRunsAsync(rcs, cb);
				}
				System.err.println("All random runs submitted");
				tae.waitForOutstandingEvaluations();
				System.err.println("Random Runs Done");
				//tae.notifyShutdown();
				
			}
			
		};
		
		
		execService.execute(run2);
		
		
		
		AutoStartStopWatch watch = new AutoStartStopWatch();
		
	
		execService.shutdown();
		try {
			execService.awaitTermination(24,TimeUnit.DAYS);
		} catch (InterruptedException e1) {
			e1.printStackTrace();
			Thread.currentThread().interrupt();
		}
		
		System.err.println("Run Time: " + watch.time() / 1000.0  + " s");
		
		
	}
	
	
	
	//@After
	public void afterTest()
	{
		for(Process p : processes)
		{
			p.destroy();
		}
		
		URL url = this.getClass().getClassLoader().getResource("restart.sh");
		try {
			Process p = Runtime.getRuntime().exec(url.getFile());
			p.waitFor();
			p.destroy();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
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
