import java.util.Set;

/**
 * Represents a vertex in the application graph.
 * Common abstraction for components and end devices.
 */
public interface ISwNode {
	/**
	 * Return the ID of the node.
	 */
	public String getId();
	/**
	 * Add a new connector to the set of connectors incident to this vertex.
	 */
	public void addConnector(Connector c);
	/**
	 * Return the set of connectors incident to the vertex.
	 */
	public Set<Connector> getConnectors();
	/**
	 * Returns true iff this vertex is an end device.
	 */
	public boolean isEndDevice();
}
