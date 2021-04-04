import java.util.HashSet;
import java.util.Set;

public class Component implements ISwNode {
	private String id;
	private double cpuReq;
	private double ramReq;
	private Set<Connector> connectors;

	public Component(String id, double cpuReq, double ramReq) {
		this.id=id;
		this.cpuReq = cpuReq;
		this.ramReq = ramReq;
		connectors=new HashSet<>();
	}

	public double getCpuReq() {
		return cpuReq;
	}

	public double getRamReq() {
		return ramReq;
	}

	public String getId() {
		return id;
	}

	public Set<Connector> getConnectors() {
		return connectors;
	}

	public void addConnector(Connector conn) {
		connectors.add(conn);
	}
}
