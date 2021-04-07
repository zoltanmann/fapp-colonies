
public class Link {
	private String id;
	private double bw;
	private double latency;
	private IHwNode v1,v2;

	public Link(double bw, double latency, IHwNode v1, IHwNode v2) {
		id=v1.getId()+"-"+v2.getId();
		boolean idAlreadyExists;
		do {
			idAlreadyExists=false;
			for(Link l : v1.getLinks()) {
				if(l.getId().equals(id)) {
					idAlreadyExists=true;
					break;
				}
			}
			if(idAlreadyExists)
				id=id+"'";
		} while(idAlreadyExists);
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
