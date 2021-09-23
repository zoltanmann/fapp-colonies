/**
 * Represents a connector either between two components or between a component and
 * an end device. The connector is undirected. There can be multiple connectors
 * between the same two vertices.
 */
public class Connector {
	/** ID of the connector */
	private String id;
	/** Bandwidth requirement of the connector */
	private double bwReq;
	/** Maximum allowed latency for the connector */
	private double maxLatency;
	/** The two end vertices of the connector */
	private ISwNode v1,v2;

	/**
	 * Constructs the connector with the given attribute values. The ID of the
	 * connector is automatically generated from the IDs of the end vertices, ensuring
	 * its uniqueness. The connector is automatically added to v1's and v2's set of 
	 * incident connectors.
	 */
	public Connector(double bwReq, double maxLatency, ISwNode v1, ISwNode v2) {
		id=v1.getId()+"-"+v2.getId();
		//If there are already connectors between v1 and v2, then this ID needs to be changed to make it unique
		boolean idAlreadyExists;
		do {
			idAlreadyExists=false;
			for(Connector c : v1.getConnectors()) {
				if(c.getId().equals(id)) {
					idAlreadyExists=true;
					break;
				}
			}
			if(idAlreadyExists)
				id=id+"'";
		} while(idAlreadyExists);
		this.bwReq = bwReq;
		this.maxLatency = maxLatency;
		this.v1=v1;
		this.v2=v2;
		v1.addConnector(this);
		v2.addConnector(this);
	}

	/**
	 * Return the bandwidth requirement of the connector.
	 */
	public double getBwReq() {
		return bwReq;
	}

	/**
	 * Return the maximum allowed latency for the connector.
	 */
	public double getMaxLatency() {
		return maxLatency;
	}

	/**
	 * Return the first end vertex of the connector.
	 */
	public ISwNode getV1() {
		return v1;
	}

	/**
	 * Return the second end vertex of the connector.
	 */
	public ISwNode getV2() {
		return v2;
	}

	/**
	 * Return the end vertex of the connector, which is not v.
	 * PRE: v is one the two end vertices.
	 */
	public ISwNode getOtherVertex(ISwNode v) {
		if(v==v1)
			return v2;
		if(v==v2)
			return v1;
		return null;
	}

	/**
	 * Return the ID of the connector.
	 */
	public String getId() {
		return id;
	}

	/**
	 * Return string representation.
	 */
	public String toString() {
		return v1.getId()+"-"+v2.getId();
	}
}
