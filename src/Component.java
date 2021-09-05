import java.util.HashSet;
import java.util.Set;

public class Component implements ISwNode {
	private String id;
	private double cpuReq;
	private double ramReq;
	private Set<Connector> connectors;
	private Colony targetColony;

	public Component(String id, double cpuReq, double ramReq, Colony targetColony) {
		this.id=id;
		this.cpuReq = cpuReq;
		this.ramReq = ramReq;
		connectors=new HashSet<>();
		this.targetColony=targetColony;
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

	public Colony getTargetColony() {
		return targetColony;
	}

	public Set<Connector> getConnectors() {
		return connectors;
	}

	public void addConnector(Connector conn) {
		connectors.add(conn);
	}

	public String toString() {
		return id+"("+cpuReq+","+ramReq+")";
	}

	public boolean isEndDevice() {
		return false;
	}
}
