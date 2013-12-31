package learnbyread.test;

import java.io.FileNotFoundException;

import learnbyread.Wiktionary;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class WiktionaryTest {
	@BeforeClass
	public static void setUp() throws FileNotFoundException{
		Wiktionary.initialize();
	}
	
	@AfterClass
	public static void tearDown(){
		Wiktionary.clear();
	}
	
	@Test
	public void lookupTest() {
		System.out.println(Wiktionary.lookup("record"));
	}
}
