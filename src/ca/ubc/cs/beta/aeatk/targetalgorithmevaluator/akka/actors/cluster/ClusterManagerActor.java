package ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.actors.cluster;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.actor.ActorRef;
import akka.actor.Address;
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
	  
	  private int minNumberOfNodes = 0;
	  private int maxNumberOfNodes = 0;
	  
	  private Set<Address> allNodes = new HashSet<Address>();
	  private Set<Address> taeNodes = new HashSet<Address>();
	  private Set<Address> workerNodes = new HashSet<Address>();
	  
	  private Map<Address, Long> startTimes = new HashMap<>();
	  private Map<Address, Long> endTimes = new HashMap<>();
	  
	  private static Logger log = LoggerFactory.getLogger(ClusterManagerActor.class);
	  
	  private long startTime;
	  //subscribe to cluster changes, MemberUp
	  @Override
	  public void preStart() {
	    cluster.subscribe(getSelf(), MemberUp.class);
	    cluster.subscribe(getSelf(), MemberRemoved.class);
	    startTime = System.currentTimeMillis();
	   // cluster.subscribe(getSelf(), MemberExited.class);
	    
	  }

	  //re-subscribe when restart
	  @Override
	  public void postStop() {
	    cluster.unsubscribe(getSelf());
	    
	    
	    long endTime = System.currentTimeMillis();
	    
	    long durationInMilliSeconds = endTime - startTime;
	    
	    long totalNodeDurationInMilliSeconds = 0;
	    
	    for(Entry<Address, Long> ent : startTimes.entrySet())
	    {
	    
	    	long nodeStartTime = ent.getValue();
	    	
	    	Long nodeEndTime = endTimes.get(ent.getKey());
	    	
	    	if(nodeEndTime == null)
	    	{
	    		nodeEndTime = endTime;
	    	}
	    	
	    	long nodeDurationInMilliSeconds = nodeEndTime - nodeStartTime;
	    	
	    	
	    	totalNodeDurationInMilliSeconds += nodeDurationInMilliSeconds;
	    }
	    
	    
	    
	    log.info("Node Statistics: Total Nodes Seen: {}, Total TAEs: {}, Total Workers:{} , Min nodes at any one time: {} , Max nodes at any one time: {}, Average Number of Workers: {} (approximate), over {} seconds", allNodes.size(), taeNodes.size(), workerNodes.size(), minNumberOfNodes, maxNumberOfNodes , totalNodeDurationInMilliSeconds / (double) durationInMilliSeconds, ((double) durationInMilliSeconds) / 1000.0);
	  }

	  @Override
	  public void onReceive(Object message)
	  {

			if (message instanceof MemberUp) {
			    MemberUp mUp = (MemberUp) message;
			   
			    allNodes.add(mUp.member().address());
			    System.out.println("Member" + mUp.member().address() + " has roles " + mUp.member().getRoles());
			    
			    if(mUp.member().getRoles().contains("worker"))
    			{
    				workerNodes.add(mUp.member().address());
    				startTimes.put(mUp.member().address(), System.currentTimeMillis());
    			} else if(mUp.member().getRoles().contains("tae"))
    			{
    				taeNodes.add(mUp.member().address());
    			} else
    			{
    				log.error("Unknown role for worker: {}", mUp.member().getRoles());
    			}
    			
			    
			    numberOfNodes++;
			    
			   
			    
			    maxNumberOfNodes = Math.max(maxNumberOfNodes, numberOfNodes);
		    } else if (message instanceof MemberRemoved)
		    {
				MemberRemoved mR = (MemberRemoved) message;
				numberOfNodes--;
				minNumberOfNodes = Math.min(minNumberOfNodes, numberOfNodes);
				//We can put this in for every node because we iterate only over the starting nodes
				endTimes.put(mR.member().address(), System.currentTimeMillis());
		    } else if(message instanceof CurrentClusterState)
		    {
		    	CurrentClusterState ccs = (CurrentClusterState) message;
		    	numberOfNodes = 0;
		    	long startTime = System.currentTimeMillis();
		    	for(Member mb : ccs.getMembers())
		    	{
		    		if(mb.status().equals(MemberStatus.Up))
		    		{
		    			
		    			allNodes.add(mb.address());
		    			if(mb.getRoles().contains("worker"))
		    			{
		    				workerNodes.add(mb.address());
		    				startTimes.put(mb.address(), startTime);
		    			} else if(mb.getRoles().contains("tae"))
		    			{
		    				taeNodes.add(mb.address());
		    			} else
		    			{
		    				log.error("Unknown role for worker: {}", mb.getRoles());
		    			}
		    			
		    			numberOfNodes++;
		    		}
		    		
		    		
				    
		    		
		    	}
		    	minNumberOfNodes = numberOfNodes;
		    	maxNumberOfNodes = Math.max(maxNumberOfNodes, numberOfNodes);
		    	
		    } else
		    {
		      unhandled(message);
		      return;
		    }
			
			//System.out.println("Number of nodes:" + numberOfNodes);
	  }

}
