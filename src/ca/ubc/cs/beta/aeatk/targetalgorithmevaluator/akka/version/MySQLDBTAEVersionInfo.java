package ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.akka.version;

import org.mangosdk.spi.ProviderFor;

import ca.ubc.cs.beta.aeatk.misc.version.AbstractVersionInfo;
import ca.ubc.cs.beta.aeatk.misc.version.VersionInfo;

@ProviderFor(VersionInfo.class)
public class MySQLDBTAEVersionInfo extends AbstractVersionInfo {

	public MySQLDBTAEVersionInfo() {
		super("AKKA Target Algorithm Evaluator", "akka-version.txt", true);
	}

}
