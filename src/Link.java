/**
 * Represents a link between two infrastructure nodes.
 */
public class Link {
	/** Unique ID of the link */
	private String id;
	/** Bandwidth of the link */
	private double bw;
	/** Latency of the link */
	private double latency;
	/** The two nodes that are connected by the link */
	private IHwNode v1,v2;

	/**
	 * Construct a new link with the given fields. The ID is generated automatically. 
	 * The link is also added to the set of incident links of the two nodes.
	 */
	public Link(double bw, double latency, IHwNode v1, IHwNode v2) {
		id=v1.getId()+"-"+v2.getId();
		boolean idAlreadyExists;
		do { //ensure that ID is unique by adding as many apostrophes as needed
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

	/**
	 * Returns the bandwidth of the link.
	 */
	public double getBw() {
		return bw;
	}

	/**
	 * Returns the latency of the link.
	 */
	public double getLatency() {
		return latency;
	}

	/**
	 * Returns the first end node of the link.
	 */
	public IHwNode getV1() {
		return v1;
	}

	/**
	 * Returns the second end node of the link.
	 */
	public IHwNode getV2() {
		return v2;
	}

	/**
	 * Returns the other end node of the link, i.e., the one that is not the same 
	 * as "node".
	 */
	public IHwNode getOtherNode(IHwNode node) {
		if(node==v1)
			return v2;
		else if(node==v2)
			return v1;
		else
			return null;
	}

	/**
	 * Returns the ID of the link.
	 */
	public String getId() {
		return id;
	}
}
