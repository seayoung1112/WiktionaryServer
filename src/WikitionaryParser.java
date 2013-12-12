import java.io.File;
import de.tudarmstadt.ukp.jwktl.*;

public class WikitionaryParser {
	public static void main(String[] args) throws Exception {
	    File dumpFile = new File("/Users/jzhao/Documents/Dic/en.xml.bz2");
	    File outputDirectory = new File("/Users/jzhao/Documents/Db");
	    boolean overwriteExisting = true;
	      
	    JWKTL.parseWiktionaryDump(dumpFile, outputDirectory, overwriteExisting);
	}
}
