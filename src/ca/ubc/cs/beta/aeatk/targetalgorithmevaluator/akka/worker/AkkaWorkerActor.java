package ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.worker;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import scala.concurrent.duration.FiniteDuration;
import ca.ubc.cs.beta.aeatk.algorithmrunresult.AlgorithmRunResult;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.ProcessRunMessage;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.messages.ContactMaster;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.messages.WorkerAvailableMessage;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.messages.LocalRunUpdateMessage;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.messages.ObserverUpdateResponse;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.messages.ProcessRunCompletedMessage;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.messages.RequestObserverUpdateMessage;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.messages.ShutdownMessage;
import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.actor.UntypedActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;

public class AkkaWorkerActor extends UntypedActor {

	LoggingAdapter log = Logging.getLogger(getContext().system(), this);
	
	private ActorRef externalWorker;
	private ActorSelection master;
	
	private ProcessRunMessage prun = null;
	
	private AlgorithmRunResult latestAlgorithmRunResult = null;

	private final AtomicReference<ProcessRunMessage> pRunToKill; 
	
	public AkkaWorkerActor(AtomicReference<ProcessRunMessage> pRunToKill)
	{
		this.pRunToKill = pRunToKill;
	}
	@Override
	public void onReceive(Object arg0) throws Exception 
	{
		if(arg0 instanceof ContactMaster)
		{
			master = this.getContext().actorSelection("akka.tcp://AKKA-TAE-Master@" + ((ContactMaster) arg0).getHost() + ":" + ((ContactMaster) arg0).getPort() + "/user/master");
			
			master.tell(new WorkerAvailableMessage(),getSelf());
			
			externalWorker = getSender();
			
		} else if(arg0 instanceof ProcessRunMessage)
		{
			prun = (ProcessRunMessage) arg0;
			
			externalWorker.tell(arg0, getSelf());
		} else if(arg0 instanceof ProcessRunCompletedMessage)
		{
			prun = null;
			master.tell(arg0, getSelf());
		} else if(arg0 instanceof ShutdownMessage)
		{
			externalWorker.tell(arg0, getSelf());
		} else if(arg0 instanceof LocalRunUpdateMessage)
		{
			this.latestAlgorithmRunResult = ((LocalRunUpdateMessage) arg0).getAlgorithmRunResult();
		} else if (arg0 instanceof RequestObserverUpdateMessage)
		{
			
			RequestObserverUpdateMessage rou = (RequestObserverUpdateMessage) arg0;
			if(prun == null)
			{
				log.debug("Got request for observer update for a run we didn't know we were doing", prun);
				prun = rou.getProcessRun();
				externalWorker.tell(prun, getSelf());
				
			} else if (!prun.equals(rou.getProcessRun()))
			{
				log.error("Got request for a run we aren't doing: {} vs {}", prun, rou.getProcessRun() );
				System.err.println("Either signal master we are shutting down or do something else");
				System.err.println("Either signal master we are shutting down or do something else");
				System.err.println("Either signal master we are shutting down or do something else");
				System.err.println("Either signal master we are shutting down or do something else");
				System.err.println("Either signal master we are shutting down or do something else");
			} else
			{
				if(latestAlgorithmRunResult != null)
				{
					if(latestAlgorithmRunResult.getAlgorithmRunConfiguration().equals(prun.getAlgorithmRunConfiguration()))
					{
					
						getSender().tell(new ObserverUpdateResponse(prun, latestAlgorithmRunResult), getSelf());
					}
					
					if(rou.kill())
					{
						this.pRunToKill.set(rou.getProcessRun());
					}
					
				} else
				{
					//log.info(" No current run info");
				}
			}
			
		} else
		{  
		
			unhandled(arg0);
		}
	}

}
