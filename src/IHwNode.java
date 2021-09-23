import java.util.Set;

/**
 * Represents a node in the infrastructure graph.
 * Common abstraction for servers (i.e., fog nodes and the cloud) and end devices.
 */
public interface IHwNode {
	/**
	 * Return the ID of the node.
	 */
	public String getId();
	/**
	 * Return the set of links incident to the node.
	 */
	public Set<Link> getLinks();
	/**
	 * Add a new link to the node's set of incident links.
	 */
	public void addLink(Link link);
	/**
	 * Remove a link from the node's set of incident links.
	 */
	public void removeLink(Link link);
}
