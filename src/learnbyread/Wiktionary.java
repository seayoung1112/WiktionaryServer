package learnbyread;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.QueueingConsumer;

import de.tudarmstadt.ukp.jwktl.JWKTL;
import de.tudarmstadt.ukp.jwktl.api.IPronunciation;
import de.tudarmstadt.ukp.jwktl.api.IWikiString;
import de.tudarmstadt.ukp.jwktl.api.IWiktionaryEdition;
import de.tudarmstadt.ukp.jwktl.api.IWiktionaryEntry;
import de.tudarmstadt.ukp.jwktl.api.IWiktionaryPage;
import de.tudarmstadt.ukp.jwktl.api.IWiktionaryRelation;
import de.tudarmstadt.ukp.jwktl.api.IWiktionarySense;
import de.tudarmstadt.ukp.jwktl.api.filter.WiktionaryEntryFilter;
import de.tudarmstadt.ukp.jwktl.api.util.Language;

public class Wiktionary {
	
	private final static String QUEUE_NAME = "wkt_queue";
	private static IWiktionaryEdition wkt = null;
	public static void main(String[] args) throws Exception {
		if(args.length > 1 && args[0].equals("-p")){
			WiktionaryParser.parseToDb(args[1]);
			return;
		}
		Wiktionary.initialize();
		ConnectionFactory factory = new ConnectionFactory();
	    factory.setHost("localhost");
	    Connection connection = factory.newConnection();
	    Channel channel = connection.createChannel();
	    
//	    auto delete, but not exclusive (all servers will share a same queue)
	    channel.queueDeclare(QUEUE_NAME, false, false, true, null);
	    channel.basicQos(1);

	    QueueingConsumer consumer = new QueueingConsumer(channel);
	    channel.basicConsume(QUEUE_NAME, false, consumer);

	    System.out.println(" [x] Awaiting requests");
	    
	    while (true) {
	        QueueingConsumer.Delivery delivery = consumer.nextDelivery();
	        System.out.println("[.] new message arrived");
	        BasicProperties props = delivery.getProperties();
	        BasicProperties replyProps = new BasicProperties
	                                         .Builder()
	                                         .correlationId(props.getCorrelationId())
	                                         .build();

	        String word = new String(delivery.getBody());
	        
	        channel.basicPublish( "", props.getReplyTo(), replyProps, lookup(word).toJSONString().getBytes());

	        channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
	    }
	}
	
	public static void initialize() throws FileNotFoundException{
		wkt = JWKTL.openEdition(new File(Config.Instance().getDbPath()));
	}
	
	public static void clear() {
		if(wkt != null)
			wkt.close();
	}
	
	public static JSONObject lookup(String word)
	{
		return lookup(word, true);
	}
//	if extent is true, will try to find the origin of this word (for plural, tense, etc) and add it.
	@SuppressWarnings("unchecked")
	public static JSONObject lookup(String word, boolean extent)
	{
        System.out.println(" [.] looking up " + word);
// 		look up in the dictionary
        WiktionaryEntryFilter filter = new WiktionaryEntryFilter();
        filter.setAllowedWordLanguages(Language.ENGLISH);
        List<IWiktionaryEntry> entries = wkt.getEntriesForWord(word, filter);
//      try lower case if on result found
        if(entries.size() == 0)
        	entries = wkt.getEntriesForWord(word.toLowerCase(), filter);
        JSONObject res = new JSONObject();
        res.put("word", word);
//        if(entries.size() > 0){
//        	IWiktionaryPage page = entries.get(0).getPage();
//        	res.put("pronunciation", page)
//        }
        JSONArray resList = new JSONArray();
        for (IWiktionaryEntry entry : entries)
        {
        	
        	JSONObject obj = new JSONObject();
        	obj.put("part_of_speech", entry.getPartOfSpeech().toString());
        	JSONArray pronunList = new JSONArray();
        	for(IPronunciation pronun : entry.getPronunciations()){
        		String type = pronun.getNote();
//        		only get RP and US pronunciation
        		if(type.equals("RP") || type.equals("US")){
        			JSONObject pronunObj = new JSONObject();
        			pronunObj.put("type", type);
        			pronunObj.put("text", pronun.getText());
        			pronunList.add(pronunObj);
        		}
        	}
        	obj.put("pronunciation", pronunList);
        	JSONArray senseList = new JSONArray();
        	for(IWiktionarySense sense : entry.getSenses())
        	{
        		JSONObject s = new JSONObject();
        		JSONArray relationList = new JSONArray();
        		if(sense.getRelations() != null)
        		{
		        	for(IWiktionaryRelation relation : sense.getRelations())
		        	{
		        		relationList.add(relation.toString());
		        	}
        		}
	        	JSONArray referenceList = new JSONArray();
	        	if(sense.getReferences() != null)
	        	{
		        	for(IWikiString ref : sense.getReferences())
		        	{
		        		referenceList.add(ref.toString());
		        	}
        		}
	        	String[] analyzed = analyzeAndPrettify(sense.getGloss().getText()); 
        		s.put("content", analyzed[0]);
        		if(extent && analyzed[1] != null){
        			s.put("origin", lookup(analyzed[1], false));
        		}
        		s.put("index", sense.getIndex());
        		s.put("references", referenceList);
        		s.put("relations", relationList);
        		senseList.add(s);
        	}
        	obj.put("senses", senseList);
        	resList.add(obj);
        }
        res.put("entries", resList);
        return res;
	}
	
	
	protected static final Pattern COMMENT_PATTERN = Pattern.compile("<!--.+?-->");
    protected static final Pattern QUOTES_PATTERN = Pattern.compile("'''?");
    protected static final Pattern WIKILINK_PATTERN = Pattern.compile("\\[\\[((?:[^|\\]]+?\\|)*)([^|\\]]+?)\\]\\]");
    protected static final Pattern TEMPLATE_PATTERN = Pattern.compile("\\{\\{.+?\\}\\}");
    protected static final Pattern REFERENCES_PATTERN = Pattern.compile("<ref[^>]*>.+?</ref>");
    protected static final Pattern HTML_PATTERN = Pattern.compile("<[^>]+>");
    protected static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s\\s+");
	private static String[] analyzeAndPrettify(String text)
	{
		String result = text, origin = null;
        result = result.replace("\t", " ");
        result = COMMENT_PATTERN.matcher(result).replaceAll("");        
        result = QUOTES_PATTERN.matcher(result).replaceAll("");
        result = WIKILINK_PATTERN.matcher(result).replaceAll("$2");
        result = REFERENCES_PATTERN.matcher(result).replaceAll("");
        result = result.replaceAll("\\{\\{.+?definition\\|(.+?)\\}\\}", " $1");
//      form-of template
        Matcher om = Pattern.compile("\\{\\{(en-)?(.+of)\\|([^\\|]+)(\\|.+)?\\}\\}").matcher(result);
        if(om.find()){
        	origin = om.group(3);
        }
        result = om.replaceAll("$2 $3");
//      context template eg: (math) (computer science)
        result = result.replaceAll("\\{\\{context\\|(.+)\\|lang=\\w+\\}\\}", "\\($1\\)");
        result = TEMPLATE_PATTERN.matcher(result).replaceAll("");
        result = HTML_PATTERN.matcher(result).replaceAll("");
        result = result.replace("’", "'");
        result = result.replace("�", "'");
        result = result.replace("°", "");
        result = WHITESPACE_PATTERN.matcher(result).replaceAll(" ");
        while (result.length() > 0 && "*: ".contains(result.substring(0, 1)))
                result = result.substring(1);
        String a[] = new String[2];
        a[0] = result.trim();
        a[1] = origin;
        return a;
	}
}
