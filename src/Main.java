import java.io.IOException;
import java.util.Random;

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
		testDriver.doTest();
	}
}
