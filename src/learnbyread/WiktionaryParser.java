package learnbyread;

import java.io.File;

import de.tudarmstadt.ukp.jwktl.*;

public class WiktionaryParser {
	public static void parseToDb(String fileName) throws Exception {
	    File dumpFile = new File(fileName);
	    File outputDirectory = new File(Config.Instance().getDbPath());
	    boolean overwriteExisting = true;
	      
	    JWKTL.parseWiktionaryDump(dumpFile, outputDirectory, overwriteExisting);
	}
}
