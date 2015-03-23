package ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.actors.cluster;

import java.util.concurrent.ConcurrentSkipListMap;

import akka.actor.ActorRef;
import akka.actor.UntypedActor;
import akka.cluster.Cluster;
import akka.cluster.Member;
import akka.cluster.ClusterEvent.CurrentClusterState;
import akka.cluster.ClusterEvent.MemberExited;
import akka.cluster.ClusterEvent.MemberRemoved;
import akka.cluster.ClusterEvent.MemberUp;
import akka.cluster.protobuf.msg.ClusterMessages.MemberStatus;

public class ClusterManagerActor extends UntypedActor {

	  Cluster cluster = Cluster.get(getContext().system());

	  private int numberOfNodes = 0;
	  
	  
	  
	  //subscribe to cluster changes, MemberUp
	  @Override
	  public void preStart() {
	    cluster.subscribe(getSelf(), MemberUp.class);
	    cluster.subscribe(getSelf(), MemberRemoved.class);
	   // cluster.subscribe(getSelf(), MemberExited.class);
	    
	  }

	  //re-subscribe when restart
	  @Override
	  public void postStop() {
	    cluster.unsubscribe(getSelf());
	  }

	  @Override
	  public void onReceive(Object message)
	  {

			if (message instanceof MemberUp) {
			    MemberUp mUp = (MemberUp) message;
			   
			    numberOfNodes++;
		    } else if (message instanceof MemberRemoved)
		    {
				MemberRemoved mR = (MemberRemoved) message;
				numberOfNodes--;
		    } else if(message instanceof CurrentClusterState)
		    {
		    	CurrentClusterState ccs = (CurrentClusterState) message;
		    	numberOfNodes = 0;
		    	for(Member mb : ccs.getMembers())
		    	{
		    		if(mb.status().equals(MemberStatus.Up))
		    		{
		    			numberOfNodes++;
		    		}
		    		
		    	}
		    	
		    } else
		    {
		      unhandled(message);
		      return;
		    }
			
			//System.out.println("Number of nodes:" + numberOfNodes);
	  }

}
