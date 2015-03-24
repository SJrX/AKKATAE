package ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.helper;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.CopyOption;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.beust.jcommander.ParameterException;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import scala.collection.immutable.Seq;
import scala.util.Random;
import akka.actor.ActorSystem;
import akka.actor.Address;
import akka.actor.AddressFromURIString;
import akka.actor.Inbox;
import akka.cluster.Cluster;

public class AkkaHelper {

	
	private static String AKKA_AUTO_NEGIOTATE_ID_FILENAME = "akka-node-id-";
	
	private static final Pattern IP_PORT_REGEX = Pattern.compile("ip=(?<ip>.+),port=(?<port>[0-9]+)");
	
	private static final Logger log = LoggerFactory.getLogger(AkkaHelper.class);
	/**
	 * See this post for more information: https://groups.google.com/forum/#!searchin/akka-user/SGE/akka-user/qczEw-xwHZA/XEtfKWRLspcJ
	 * 
	 * 
1) Pick a random number >= 1024 to serve as the port.
2) Users will supply the network to bound too, or it will be auto-detected.
3) Look in a known directory and scan for files of the name akka.N, where N is an integer.
4) Take the highest number add 1 (or assume the highest number is 0), read the other files and put the ipaddress : ports as seed nodes.
5) Try to write the file as new akka.N+1, if this fails go back to step 1.
6) Try to start the actor system, if this fails go back to step 1.
7) When shutting down the actor system delete the file akka.N+1. (also happens in step 5 and step 6)

	 */
	public static ActorSystem startAkkaSystem(String networkInterfacePriority, File directory, String configuration, Integer setID)
	{
	
		UUID uuid = UUID.randomUUID();
attemptLoop:
	for(int startAttempt = 0; startAttempt < 25; startAttempt++)
		{
			try
			{
				Random rand = new Random();
				
				int port = rand.nextInt(10240) + 10240;
				
				String ipAddress = getIPAddress(networkInterfacePriority);
				
				log.info("Attempting to Start Actor System on {}:{} using directory {} ", ipAddress, port, directory);
				File f;
				
				
				
				try {
					long ms_to_sleep = (long) (rand.nextDouble()*500);
					Thread.sleep(ms_to_sleep);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					throw new IllegalStateException(e);
				}
				
				File targetFile;
				
				String id;
				if(setID == null)
				{
					int idInt = getStartingID(directory);
					
					
					//Ensures that for the first million nodes, the string order is the same as numeric order (otherwise a string foo-2 comes after foo-12).
					id=String.format("%06d", idInt);
				} else
				{
					id=String.format("%06d", setID);
				}
				
				f = new File(directory + File.separator + "akka-attempt-" + uuid + "-" + AKKA_AUTO_NEGIOTATE_ID_FILENAME + id );
				
				f.createNewFile();
				f.deleteOnExit();
				try(FileOutputStream fout = new FileOutputStream(f))
				{
					
					try (BufferedWriter bwriter = Files.newBufferedWriter(f.toPath(), StandardCharsets.UTF_8, StandardOpenOption.SYNC))
					{
						bwriter.append("ip="+ipAddress + ",port=" + port);
						bwriter.flush();
					}
				}
				targetFile = new File(directory + File.separator + AKKA_AUTO_NEGIOTATE_ID_FILENAME + id );
				
				
				
				
				try {
					Files.createSymbolicLink(targetFile.toPath(), f.toPath());
					targetFile = new File(directory + File.separator + AKKA_AUTO_NEGIOTATE_ID_FILENAME + id );
					targetFile.deleteOnExit();
				}catch(IOException e)
				{
					
					if(!(e instanceof FileAlreadyExistsException))
					{
						log.warn("Couldn't create symbolic link {}. This may be because it already existed, in which case this error is benign, we will retry a number of times before giving up. If things look suspicious please contact the developer and tell them : Exception : {} , Message: {} ", targetFile.getAbsolutePath() , e.getClass().getCanonicalName(), e.getMessage());
						
					} else
					{
						if(setID != null)
						{
							throw new ParameterException("File "+ directory + File.separator + AKKA_AUTO_NEGIOTATE_ID_FILENAME + id  + " was already in use, each individual node needs to have a unique ID");
						}
					}
					
					
					 
					
					
					try 
					{

						Thread.sleep((long) ((rand.nextDouble() * 1500) + 500));
						
						
						continue attemptLoop;
					} catch(InterruptedException e2)
					{
						Thread.currentThread().interrupt();
						
						throw new IllegalStateException(e2);
					}
					
				} catch(UnsupportedOperationException e)
				{
					log.error("File system does not support symbolic links, you will have to manually configure individual nodes");
				}
				
				
					
					
					
					

				
				
				try 
				{
					final Config config = ConfigFactory.parseString("akka.remote.netty.tcp.port=" +port)			
							.withFallback(ConfigFactory.parseString("akka.remote.netty.tcp.hostname=" +ipAddress))
							.withFallback(ConfigFactory.parseString("akka.remote.watch-failure-detector.threshold=12"))
							.withFallback(ConfigFactory.parseString("akka.remote.watch-failure-detector.acceptable-heartbeat-pause=30"))
							.withFallback(ConfigFactory.parseString(configuration));
					
					//System.out.println(config.toString());
				      
					ActorSystem system = ActorSystem.create("ClusterSystem", config);
					
					Cluster cluster = Cluster.get(system);
					
					
					
					List<Address> nodes = getSeedNodes(directory,targetFile);
					log.info("Seed nodes for id {} are {}", id, nodes);
					cluster.joinSeedNodes(scala.collection.JavaConversions.asScalaBuffer(nodes).toList());
					log.info("Cluster node started as id-" + id);
					
					
					return system;
				} catch(RuntimeException e)
				{
					log.error("Starting Actor System failed. Retrying", e);
					targetFile.delete();
					continue;
				}
				
				
				
				
				
				
				
				
				
				
			} catch(IOException e)
			{
				log.warn("Got IOException trying again in 10 seconds", e);
				try {
					Thread.sleep(500);
				} catch (InterruptedException e1) {
					Thread.currentThread().interrupt();
					throw new IllegalStateException(e);
				}
			}
		}
		//Enumeration<NetworkInterface> nets = ;
		
		
	
		throw new IllegalStateException("Couldn't start Cluster System");
	}
	
	   
	
	    
	public static int getStartingID(File dir)
	{
		if(!dir.isDirectory())
		{
			throw new IllegalArgumentException("Expected Directory: " + dir.getAbsolutePath());
		}
		
		String[] matchingFiles = dir.list(new FilenameFilter(){

			@Override
			public boolean accept(File dir, String name) {
				if(name.startsWith(AKKA_AUTO_NEGIOTATE_ID_FILENAME))
				{
					return true;
				} else
				{
					return false;
				}
				
			}
			
		});
		
		if(matchingFiles.length == 0)
		{
			return 0;
		}
		
		String highestFileName = matchingFiles[0];
		
		for(String name : matchingFiles)
		{
		
			if(highestFileName.compareTo(name) < 0)
			{
				highestFileName = name;
			}
		}
		
		
		return Integer.valueOf(highestFileName.substring(AKKA_AUTO_NEGIOTATE_ID_FILENAME.length())) + 1;
		
		
	}
	
	public static List<Address> getSeedNodes(File dir, File myFile)
	{
		if(!dir.isDirectory())
		{
			throw new IllegalArgumentException("Expected Directory: " + dir.getAbsolutePath());
		}
		
		
		Path dirPath = dir.toPath();
		String[] matchingFiles = dir.list(new FilenameFilter(){

			@Override
			public boolean accept(File dir, String name) {
				if(name.startsWith(AKKA_AUTO_NEGIOTATE_ID_FILENAME))
				{
					return true;
				} else
				{
					return false;
				}
				
			}
			
		});
		
		
		if(matchingFiles.length == 0)
		{
			return Collections.emptyList();
		}
		
		String highestFileName = matchingFiles[0];
		
		for(String name : matchingFiles)
		{
		
			if(highestFileName.compareTo(name) > 0)
			{
				highestFileName = name;
			}
		}
		
		Arrays.sort(matchingFiles);
		
		
		
		
		int firstIndex = 0;
		
		int lastIndex;
		
	
		
		for(lastIndex=1; lastIndex < matchingFiles.length; lastIndex++)
		{
			
	
			
			if(matchingFiles[lastIndex].compareTo(myFile.getName()) >= 0)
			{
				break;
			}
		}
		
		//We only use seed nodes less than ourselves unless we are the first entry, in which case ourself is a valid seed node.
		List<String> filenames = Arrays.asList(matchingFiles).subList(firstIndex, lastIndex);
		
		log.debug("Using the following as seed nodes: {}", filenames);
		
		ArrayDeque<String> deque = new ArrayDeque<String>(filenames);
		
		
		
		
		List<Address> addresses = new ArrayList<>();
		
		
		
		int failedAttempts =0;
		final int MAX_FAILED_ATTEMPTS = Math.max(deque.size(),10); 
		while(deque.peek() != null)
		{
			
			
			if(failedAttempts > MAX_FAILED_ATTEMPTS)
			{
				break;
			}
			
			String filename = deque.poll();
			
			try {
			List<String> lines = Files.readAllLines(Paths.get(dirPath.toString(), filename), Charset.defaultCharset());
			
			if(lines.size() == 0)
			{
				//Put the entry at the back of the queue.
				deque.add(filename);
				
				log.warn("Didn't retrieve any lines from file, this seems bad");
				failedAttempts++;
				continue;
						
			} else if(lines.size() >= 2)
			{
				throw new IllegalStateException("Corrupt file detected with contents: " + lines + " should only have been one line in " + filename);
			} else
			{
				Matcher m = IP_PORT_REGEX.matcher(lines.get(0));
				
				if(!m.find())
				{
					throw new IllegalStateException("Corrupt file detected contents: " + lines.get(0) +" in file " + filename);
				}
				
				String ipAddress = m.group("ip");
				String port = m.group("port");
				
				
				addresses.add(getAddress(ipAddress, port));
				
				//AddressFromURIString.apply("akka.tcp://" + ipAddress + ":" + port +"/"));
			}
			
			} catch(IOException e)
			{
				log.warn("Error reading file: {}, got exception : {}, message: {}. Will retry this file" , filename, e.getClass().getSimpleName(),e.getMessage());
				failedAttempts++;
				deque.add(filename);
			}
			
		}
		
		
		return addresses;
		
		
		
		
	}




	/**
	 * @param ipAddress
	 * @param port
	 * @return
	 */
	private static Address getAddress(String ipAddress, String port) {
		return new Address("akka.tcp","ClusterSystem",ipAddress, Integer.valueOf(port));
	}
	
	public static String getIPAddress(String nics)
	{
		
		
		List<String> ipAddresses = new ArrayList<>(Arrays.asList(nics.split(",")));
		
		List<String> availableAddresses = new ArrayList<String>();
		
		
		
		try
		{ 
	        for (NetworkInterface netint : Collections.list(NetworkInterface.getNetworkInterfaces()))
	        {
	        	if(!netint.isUp())
	        	{
	        		continue;
	        	}
	        	
	        	Enumeration<InetAddress> inetAddresses = netint.getInetAddresses();
		        for (InetAddress inetAddress : Collections.list(inetAddresses)) {
		        	availableAddresses.add(inetAddress.getHostAddress());
		        	
		        	if(inetAddress instanceof Inet4Address && !netint.isLoopback())
		        	{
		        		//Add loopbacks;
		        		ipAddresses.add(inetAddress.getHostAddress());
		        	}
		        	
		        }
		        
		        
	        	
	        }
	        
	        for(String address : ipAddresses)
	        {
	        	for(String availableAddress : availableAddresses)
	        	{
	        		if(availableAddress.startsWith(address))
	        		{
	        			return availableAddress;
	        		}
	        	}
	        }
	        
	        return "127.0.0.1";
		} catch(SocketException e)
		{
			throw new IllegalStateException("Not sure how I got a SocketException Here", e);
		}
	}
	
	private static class RestartException extends Exception
	{
		
	}

	/**
	 * They say doing the same thing over and over again is a sign of insanity :)
	 * 
	 * In this case it should work there is a race condition:
	 * 
	 * See this post: https://groups.google.com/forum/#!searchin/akka-user/akka.actor.UnstartedCell$20/akka-user/NnlggvIm1gU/u2M-TbYfSyQJ
	 * Also this:
	 * https://github.com/akka/akka/issues/15409
	 * 
	 * 
	 * @param system
	 * @return
	 */
	public static Inbox getInbox(ActorSystem system) {
		
		while(true)
		{
			try 
			{
				 return Inbox.create(system);
				 
				 
			} catch(ClassCastException e)
			{
				
				/*
				if(e.getMessage().contains("akka.actor.UnstartedCell"))
				{
					System.err.println("Good");
				}*/
				
				try {
					LoggerFactory.getLogger(AkkaHelper.class).warn("Couldn't create Inbox, sleeping for 1500 ms (you can most likely disregard this message, unless it seems to be stuck in an infinite loop)");
					Thread.sleep(1500);
				} catch (InterruptedException e1) {
					Thread.currentThread().interrupt();
					throw new IllegalStateException("Couldn't create Inbox and was interrupted");
				}
				
			}
		}
		
	}
}
