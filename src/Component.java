import java.util.HashSet;
import java.util.Set;

/**
 * Represents a component of an application.
 */
public class Component implements ISwNode {
	/** ID of the component */
	private String id;
	/** CPU requirement of the component */
	private double cpuReq;
	/** RAM requirement of the component */
	private double ramReq;
	/** Set of connectors incident to the component */
	private Set<Connector> connectors;
	/** The identifier of the fog colony that this component (actually, the whole application) is designated for */
	private int targetColony;

	/**
	 * Construct Component with the given attributes. The set of incident connectors
	 * is initialized to be empty.
	 */
	public Component(String id, double cpuReq, double ramReq, int targetColony) {
		this.id=id;
		this.cpuReq = cpuReq;
		this.ramReq = ramReq;
		connectors=new HashSet<>();
		this.targetColony=targetColony;
	}

	/** 
	 * Return the CPU requirement of the component.
	 */
	public double getCpuReq() {
		return cpuReq;
	}

	/** 
	 * Return the RAM requirement of the component.
	 */
	public double getRamReq() {
		return ramReq;
	}

	/** 
	 * Return the ID of the component.
	 */
	public String getId() {
		return id;
	}

	/** 
	 * Return the identifier number of the colony that the component (actually, the 
	 * application that the component belongs to) is designated for.
	 */
	public int getTargetColony() {
		return targetColony;
	}

	/**
	 * Return the set of connectors incident to this component.
	 */
	public Set<Connector> getConnectors() {
		return connectors;
	}

	/**
	 * Add a new connector to the set of connectors incident to this component.
	 */
	public void addConnector(Connector conn) {
		connectors.add(conn);
	}

	/**
	 * Return string representation.
	 */
	public String toString() {
		return id+"("+cpuReq+","+ramReq+")";
	}

	/**
	 * From interface ISwNode.
	 */
	@Override
	public boolean isEndDevice() {
		return false;
	}
}
