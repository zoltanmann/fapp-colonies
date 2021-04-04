
public class Link {
	private String id;
	private double bw;
	private double latency;
	private IHwNode v1,v2;

	public Link(String id, double bw, double latency, IHwNode v1, IHwNode v2) {
		this.id=id;
		this.bw = bw;
		this.latency = latency;
		this.v1=v1;
		this.v2=v2;
		v1.addLink(this);
		v2.addLink(this);
	}

	public double getBw() {
		return bw;
	}

	public double getLatency() {
		return latency;
	}

	public IHwNode getV1() {
		return v1;
	}

	public IHwNode getV2() {
		return v2;
	}

	public IHwNode getOtherNode(IHwNode node) {
		if(node==v1)
			return v2;
		else if(node==v2)
			return v1;
		else
			return null;
	}

	public String getId() {
		return id;
	}
}
