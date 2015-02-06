package ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import ca.ubc.cs.beta.aeatk.algorithmrunconfiguration.AlgorithmRunConfiguration;
import ca.ubc.cs.beta.aeatk.algorithmrunresult.AlgorithmRunResult;
import ca.ubc.cs.beta.aeatk.algorithmrunresult.kill.StatusVariableKillHandler;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.TargetAlgorithmEvaluatorCallback;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.TargetAlgorithmEvaluatorRunObserver;

public class CallerContext {

	
	private final TargetAlgorithmEvaluatorCallback taeCallback;

	private final List<AlgorithmRunConfiguration> runConfigs;

	private final TargetAlgorithmEvaluatorRunObserver runStatusObserver;
	
	private final Map<AlgorithmRunConfiguration, StatusVariableKillHandler> killHandlers;
	
	public CallerContext(List<AlgorithmRunConfiguration> runConfigs, TargetAlgorithmEvaluatorCallback taeCallback, TargetAlgorithmEvaluatorRunObserver runStatusObserver)
	{	
		this.runConfigs= Collections.unmodifiableList(runConfigs);
		this.taeCallback = taeCallback;
		this.runStatusObserver = (runStatusObserver != null) ? runStatusObserver: new TargetAlgorithmEvaluatorRunObserver() {

			@Override
			public void currentStatus(List<? extends AlgorithmRunResult> runs) {
				// TODO Auto-generated method stub
				
			}};
			
			
		ConcurrentHashMap<AlgorithmRunConfiguration, StatusVariableKillHandler> killHandlers = new ConcurrentHashMap<>();
		
		for(AlgorithmRunConfiguration runConfig : runConfigs)
		{
			killHandlers.put(runConfig, new StatusVariableKillHandler());
		}
		
		this.killHandlers = Collections.unmodifiableMap(killHandlers);
	}
	
	
	public TargetAlgorithmEvaluatorCallback getTargetAlgorithmEvaluatorCallback() {
		return taeCallback;
	}

	public List<AlgorithmRunConfiguration> getAlgorithmRunConfigurations() {
		return runConfigs;
	}

	public TargetAlgorithmEvaluatorRunObserver getTargetAlgorithmEvaluatorRunObserver() {
		return runStatusObserver;
	}
	
	public StatusVariableKillHandler getKillHandler(AlgorithmRunConfiguration runConfig)
	{
		return killHandlers.get(runConfig);
	}
	
}
