package ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.helper;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.options.AkkaWorkerOptions;

public class AkkaHelperTester {

	public static void main(String[] args) throws IOException
	{
		/*
		final AkkaWorkerOptions opts = new AkkaWorkerOptions();
		
		final String configuration ="akka {\n" + 
				"  loglevel = \"WARNING\"\n" +
				"  actor {\n" + 
				"    provider = \"akka.cluster.ClusterActorRefProvider\"\n" + 
				"  }\n" + 
				"  remote {\n" + 
				"    log-remote-lifecycle-events = off\n" + 
				"    netty.tcp {\n" + 
				"      hostname = \"127.0.0.1\"\n" + 
				"      port = 0\n" + 
				"    }\n" + 
				"  }\n" + 
				"\n" + 
				"  cluster {\n" + 
				//"    seed-nodes = [\n" + 
				//"      \"akka.tcp://ClusterSystem@127.0.0.1:61001\"]\n" + 
				//,\n" + 
				//"      \"akka.tcp://ClusterSystem@127.0.0.1:61001\"]\n" + 
				"\n" + 
				"	  jmx.enabled = " + (opts.clustOpts.jmxEnabled ? "on" : "off") + "\n"+ 
				"	  gossip-interval = "+opts.clustOpts.gossipInterval + " ms\n"+
				"	  leader-actions-interval = "+ opts.clustOpts.leaderActionInterval+" ms\n"+
				"	  unreachable-nodes-reaper-interval = " +opts.clustOpts.unreachableNodesReaperInterval+" ms\n"+
				"	  periodic-tasks-initial-delay = "+ opts.clustOpts.periodicTasksInitialDelay + " ms\n"+
				"	  failure-detector.heartbeat-interval = "+opts.clustOpts.failureDetectorHeartbeatInterval+" ms\n"+
				"     auto-down-unreachable-after = " +opts.clustOpts.autoDownUnreachableAfter+ " ms\n" + 
				"  }\n" + 
				"}\n";
			
		
		
		
		final File dir = Files.createTempDirectory("roar").toFile();
		final int threads = 8;
		final CountDownLatch latch = new CountDownLatch(threads);
		Runnable run = new Runnable()
		{
			public void run()
			{
				
				latch.countDown();
				try {
					latch.await();
				} catch (InterruptedException e) {
					Thread.currentThread();
					e.printStackTrace();
				}
				
				AkkaHelper.startAkkaSystem("172.27", dir, configuration);
				
			}
		};
		
		
		ExecutorService execService = Executors.newCachedThreadPool();
		
		for(int i=0; i < threads; i++)
		{
			execService.execute(run);
		}
		
		*/
		
	}
}

