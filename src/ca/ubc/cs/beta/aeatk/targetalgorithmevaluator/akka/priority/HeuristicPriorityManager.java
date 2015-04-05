package ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.priority;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import net.jcip.annotations.ThreadSafe;
import ca.ubc.cs.beta.aeatk.algorithmrunconfiguration.AlgorithmRunConfiguration;
import ca.ubc.cs.beta.aeatk.misc.associatedvalue.Pair;
import ca.ubc.cs.beta.aeatk.parameterconfigurationspace.ParameterConfiguration;
import ca.ubc.cs.beta.aeatk.parameterconfigurationspace.ParameterConfigurationSpace;

/**
 * This class is responsible for trying to manage priorities in a 
 * sane way.
 * 
 * The heuristic being chosen is the number of times the configuration with the most runs,
 * switches, means that this run is doing 'better'.
 * 
 * 
 * 
 * @author Steve Ramage <seramage@cs.ubc.ca>
 *
 */

@ThreadSafe
public class HeuristicPriorityManager {

	
	public AtomicInteger totalRuns = new AtomicInteger(0);
	private final ConcurrentHashMap<ParameterConfiguration, AtomicInteger> configurationRunCount = new ConcurrentHashMap<>();
	
	private final AtomicReference<Pair<ParameterConfiguration, Integer>> incumbentHeuristic = new AtomicReference<>(); 
			
	private final AtomicInteger numberOfSwitches = new AtomicInteger(0);
	
	
	private final double MAX_NUMBER_OF_RUNS = 10_000_000;
	
	public HeuristicPriorityManager()
	{
		ParameterConfiguration nullConfig = ParameterConfigurationSpace.getNullConfigurationSpace().getDefaultConfiguration();
		incumbentHeuristic.set(new Pair<>(nullConfig, 0));
	}
	public double getPriority(List<AlgorithmRunConfiguration> rcs)
	{
		
		//We don't really care about single runs, and they shouldn't ever take priority.
		if(rcs == null || rcs.size() < 2 )
		{
			return 1;
		}
		
		totalRuns.addAndGet(rcs.size());
		
		ParameterConfiguration config = null;
		for(AlgorithmRunConfiguration rc : rcs)
		{
			if(config == null)
			{
				config = rc.getParameterConfiguration();
			}
			
			if(!config.equals(rc.getParameterConfiguration()))
			{
				//Not an automatic configurator run, don't have a
				//heuristic
				return 1;
			}
			
			
			
			
		}
		
		configurationRunCount.putIfAbsent(config, new AtomicInteger(0));
		
		
		AtomicInteger runCountForConfiguration = configurationRunCount.get(config);
		
		runCountForConfiguration.addAndGet(rcs.size());
		
		
		Pair<ParameterConfiguration, Integer> newPair = new Pair<>(config, runCountForConfiguration.get());
		
		Pair<ParameterConfiguration, Integer> incumbent;
		
		
		
		boolean change = true;
		do
		{
			incumbent = incumbentHeuristic.get();
				
			if(incumbent.getSecond() >=  newPair.getSecond())
			{ 
				//Prioritize by number of switches 
				return numberOfSwitches.get() + rcs.size() / MAX_NUMBER_OF_RUNS;
			}
			
			if(incumbent.getFirst().equals(config))
			{
				change = false;
			}
		} while(!incumbentHeuristic.compareAndSet(incumbent, newPair));
		
		
		if(change)
		{
			return numberOfSwitches.incrementAndGet() + rcs.size() / MAX_NUMBER_OF_RUNS;
		} else
		{
			return numberOfSwitches.get() + rcs.size() / MAX_NUMBER_OF_RUNS;
		}
		
		
	}
}
