
public class Connector {
	private String id;
	private double bwReq;
	private double maxLatency;
	private ISwNode v1,v2;

	public Connector(double bwReq, double maxLatency, ISwNode v1, ISwNode v2) {
		id=v1.getId()+"-"+v2.getId();
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

	public String toString() {
		return v1.getId()+"-"+v2.getId();
	}
}
