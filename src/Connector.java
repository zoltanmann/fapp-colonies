
public class Connector {
	private String id;
	private double bwReq;
	private double maxLatency;
	private ISwNode v1,v2;

	public Connector(String id, double bwReq, double maxLatency, ISwNode v1, ISwNode v2) {
		this.id=id;
		this.bwReq = bwReq;
		this.maxLatency = maxLatency;
		this.v1=v1;
		this.v2=v2;
	}

	public double getBwReq() {
		return bwReq;
	}

	public double getMaxLatency() {
		return maxLatency;
	}

	public ISwNode getV1() {
		return v1;
	}

	public ISwNode getV2() {
		return v2;
	}

	public String getId() {
		return id;
	}
}
