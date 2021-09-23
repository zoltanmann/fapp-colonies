import java.util.HashSet;
import java.util.Set;

/**
 * Represents an end device. An end device is an infrastructure, but at the same time
 * also a vertex in the application graph, since a connector may connect a component
 * with an end device.
 */
public class EndDevice implements ISwNode, IHwNode {
	/** ID of the end device */
	private String id;
	/** Set of links incident to the end device (in the infrastructure graph) */
	private Set<Link> links;
	/** Set of connectors incident to the end device (in the application graph) */
	private Set<Connector> connectors;

	/**
	 * Construct end device with the given ID, and with empty sets of incident
	 * links and connectors.
	 */
	public EndDevice(String id) {
		this.id=id;
		links=new HashSet<>();
		connectors=new HashSet<>();
	}

	/**
	 * Return the set of links incident to the end device.
	 */
	public Set<Link> getLinks() {
		return links;
	}

	/**
	 * Add a new link to the set of links incident to the end device.
	 */
	public void addLink(Link link) {
		links.add(link);
	}

	/**
	 * Remove link from the set of links incident to the end device.
	 */
	public void removeLink(Link link) {
		links.remove(link);
	}

	/**
	 * Add a new connector to the set of connectors incident to the end device.
	 */
	public void addConnector(Connector c) {
		connectors.add(c);
	}

	/**
	 * Return the set of connectors incident to the end device.
	 */
	public Set<Connector> getConnectors() {
		return connectors;
	}

	/**
	 * Return the ID of the end device.
	 */
	public String getId() {
		return id;
	}

	/**
	 * From ISwNode interface.
	 */
	@Override
	public boolean isEndDevice() {
		return true;
	}
}
