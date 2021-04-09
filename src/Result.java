
public class Result {
	public int success;
	public long timeMs;
	public long migrations;

	public Result() {
		success=0;
		timeMs=0;
		migrations=0;
	}

	public void increaseBy(Result other) {
		success+=other.success;
		timeMs+=other.timeMs;
		migrations+=other.migrations;
	}

	public String toString() {
		return ""+success+";"+timeMs+";"+migrations;
	}
}
