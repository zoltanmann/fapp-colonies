import java.io.IOException;
import java.util.Random;

/**
 * Main class to start an experiment.
 */
public class Main {
	/** Random generator that can be used by any class in the program */
	public static Random random;

	/**
	 * Main method.
	 */
	public static void main(String[] args) throws IOException {
		random=new Random();
		TestDriver testDriver;
		//testDriver=new TestSynthetic();
		testDriver=new TestReal();
		for(int i=0;i<2;i++)
			testDriver.doTest("_"+i);
	}
}
