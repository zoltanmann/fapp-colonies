import java.util.HashSet;
import java.util.Set;

/**
 * Represents a fog node or the cloud.
 */
public class Server implements IHwNode {
	/** ID of this server */
	private String id;
	/** CPU capacity of this server */
	private double cpuCap;
	/** RAM capacity of this server */
	private double ramCap;
	/** Set of links incident to this server */
	private Set<Link> links;
	/** Whether this is the cloud */
	private boolean bCloud;
	/** Set of colonies to which this server belongs */
	private Set<Integer> colonies;

	/**
	 * Constructs a server with the given attributes and an empty set of incident links.
	 */
	public Server(String id,double cpuCap,double ramCap,boolean bCloud,int colony) {
		this.id=id;
		this.cpuCap = cpuCap;
		this.ramCap = ramCap;
		this.bCloud=bCloud;
		links=new HashSet<>();
		colonies=new HashSet<>();
	}

	/**
	 * Returns the ID of the server.
	 */
	public String getId() {
		return id;
	}

	/**
	 * Returns the CPU capacity of the server.
	 */
	public double getCpuCap() {
		return cpuCap;
	}

	/**
	 * Returns the RAM capacity of the server.
	 */
	public double getRamCap() {
		return ramCap;
	}

	/**
	 * Returns the set of links that are incident to the server.
	 */
	public Set<Link> getLinks() {
		return links;
	}

	/**
	 * Add a new link to the set of links incident to this server.
	 */
	public void addLink(Link link) {
		links.add(link);
	}

	/**
	 * Remove a link from the set of links incident to this server.
	 */
	public void removeLink(Link link) {
		links.remove(link);
	}

	/**
	 * Add this server to the colony with the given identifier number.
	 */
	public void addToColony(int colony) {
		colonies.add(colony);
	}

	/**
	 * Determines if this server belongs to the coloy with the given identifier number.
	 */
	public boolean belongsToColony(int colony) {
		return colonies.contains(colony);
	}

	/**
	 * Returns string representation.
	 */
	public String toString() {
		return id+"("+cpuCap+","+ramCap+")";
	}

	public boolean isCloud() {
		return bCloud;
	}
}
