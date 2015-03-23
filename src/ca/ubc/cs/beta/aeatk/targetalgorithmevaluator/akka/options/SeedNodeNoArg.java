package ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.options;

import ca.ubc.cs.beta.aeatk.misc.options.NoArgumentHandler;

public class SeedNodeNoArg implements NoArgumentHandler {

	@Override
	public boolean handleNoArguments() {
StringBuilder sb = new StringBuilder();
		
		sb.append("akka-seed-node is a utility that can be used to just start a seed node. This is useful when you don't want to hammer the file system to negiotate ids\n ").append("\n\n");

		sb.append("  Basic Usage:\n");
		sb.append("  akka-seed-node --akka-seed-dir /path/to/dir --id 0 \n\n");
	
		sb.append("  To specify networks to listen to:\n");
		sb.append("  akka-seed-node --akka-seed-dir /path/to/dir --id 0 --akka-network 10.59.0,127.0.0.1 \n\n");
	
		
		sb.append("  Full version information is available with :\n");
		sb.append("  akka-seed-node -v\n\n");
		
		sb.append("  A full command line reference is available with:\n");
		sb.append("  akka-seed-node --help\n\n");
			  
	
		System.out.println(sb);
		
		return true;
	}

}
