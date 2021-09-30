/**
 * Represents the aggregated result of a set of optimization runs. The fields are public
 * so they can be read and written directly.
 */
public class Result {
	/** Number of optimization runs that were successful */
	public int success;
	/** Total execution time of the optimization runs (in milliseconds) */
	public long timeMs;
	/** Total number of migrations in the optimization runs */
	public long migrations;

	/**
	 * Constructs a Result object in which each field is 0.
	 */
	public Result() {
		success=0;
		timeMs=0;
		migrations=0;
	}

	/**
	 * Add to this Result object the other one that is given as parameter.
	 */
	public void increaseBy(Result other) {
		success+=other.success;
		timeMs+=other.timeMs;
		migrations+=other.migrations;
	}

	/**
	 * Return string representation.
	 */
	public String toString() {
		return ""+success+";"+timeMs+";"+migrations;
	}
}
