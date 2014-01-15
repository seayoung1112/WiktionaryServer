package learnbyread;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.Map;

import org.yaml.snakeyaml.Yaml;

public class Config {
	@SuppressWarnings("rawtypes")
	private Map _map;
	private static Config _config = null;
	@SuppressWarnings("rawtypes")
	private Config() throws FileNotFoundException{
		Yaml yaml = new Yaml();
		InputStream input = new FileInputStream(new File("config.yaml"));
		_map = (Map)yaml.load(input);
	}
	public static Config Instance() throws FileNotFoundException{
		if(_config == null){
			_config = new Config();
		}
		return _config;
	}
	public String getDbPath(){
		return (String) _map.get("db");
	}
}
