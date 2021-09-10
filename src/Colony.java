import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Represents a fog colony, by storing the infrastructure nodes (end devices, fog 
 * nodes, cloud) contained in the colony. The Colony knows which applications it
 * has to host, but does not maintain the specific placement. 
 */
public class Colony {
	/** The set of all HW nodes in the colony, including end devices, fog nodes, cloud */
	private Set<IHwNode> nodes;
	/** The list of all servers in the colony, including fog nodes and the cloud */
	private List<Server> servers;
	/** The list of end devices belonging to the colony */
	private List<EndDevice> endDevices;
	/** The set of neighboring colonies */
	private Set<Colony> neighbors;
	/** The list of applications designated for this colony */
	private List<Application> applications;
	/** The set of servers shared with neighboring colonies */
	private Set<Server> sharedNodes;

	/**
	 * Construct empty colony.
	 */
	public Colony() {
		nodes=new HashSet<>();
		servers=new ArrayList<>();
		endDevices=new ArrayList<>();
		neighbors=new HashSet<>();
		applications=new ArrayList<>();
		sharedNodes=new HashSet<>();
	}

	/**
	 * Add a server to the colony.
	 */
	public void addServer(Server node) {
		servers.add(node);
		nodes.add(node);
	}

	/**
	 * Add an end device to the colony.
	 */
	public void addEndDevice(EndDevice node) {
		endDevices.add(node);
		nodes.add(node);
	}

	/**
	 * Get a randomly chosen server of the colony. PRE: the colony contains at least
	 * one server, and Main.random has already been initialized.
	 */
	public Server getRandomServer() {
		return servers.get(Main.random.nextInt(servers.size()));
	}

	/**
	 * Get a randomly chosen end device of the colony. PRE: the colony contains at 
	 * least one end device, and Main.random has already been initialized.
	 */
	public EndDevice getRandomEndDevice() {
		return endDevices.get(Main.random.nextInt(endDevices.size()));
	}

	/**
	 * Add a colony to the set of neighboring colonies.
	 */
	public void addNeighbor(Colony col) {
		neighbors.add(col);
	}

	/**
	 * Add an application to the list of applications designated for this colony.
	 * The applications designated for the colony will be processed in the order in 
	 * which they were added. 
	 */
	public void addApplication(Application a) {
		applications.add(a);
	}

	/**
	 * Get the i-th application designated for this colony. PRE: i is at least 0 and
	 * less than the number of applications designated for this colony.
	 */
	public Application getApplication(int i) {
		return applications.get(i);
	}

	/**
	 * Get the list of servers (fog nodes and the cloud) contained in this colony.
	 */
	public List<Server> getServers() {
		return servers;
	}

	/**
	 * Get the list of end devices belonging to this colony.
	 */
	public List<EndDevice> getEndDevices() {
		return endDevices;
	}

	/**
	 * Get the set of neighboring colonies.
	 */
	public Set<Colony> getNeighbors() {
		return neighbors;
	}

	/**
	 * Returns whether the given colony is a neighbor of this colony.
	 */
	public boolean isAdjacentTo(Colony other) {
		return neighbors.contains(other);
	}

	/**
	 * Pick k random fog nodes from this colony, to be shared with a neighboring
	 * colony. The selected fog nodes are marked as shared and returned, but the
	 * neighboring colonies are not changed by this method.
	 * The cloud is never selected for sharing.
	 * Already shared servers are not selected.
	 * PRE: k is not more than the number of fog nodes in the colony that are not
	 * shared yet.
	 */
	public Set<Server> shareNodes(int k) {
		Set<Server> result=new HashSet<>();
		List<Server> potentialNodesToShare=new ArrayList<>();
		for(Server s : servers) {
			if(!sharedNodes.contains(s) && !s.getId().equals("cloud"))
				potentialNodesToShare.add(s);
		}
		for(int i=0;i<k;i++) {
			Server s=potentialNodesToShare.get(Main.random.nextInt(potentialNodesToShare.size()));
			potentialNodesToShare.remove(s);
			result.add(s);
		}
		sharedNodes.addAll(result);
		return result;
	}

	/**
	 * Return the set of fog nodes shared with neighboring colonies.
	 */
	public Set<Server> getSharedNodes() {
		return sharedNodes;
	}

	/**
	 * Return a shallow copy of this colony.
	 */
	public Colony clone() {
		Colony other=new Colony();
		other.nodes.addAll(nodes);
		other.servers.addAll(servers);
		other.endDevices.addAll(endDevices);
		other.neighbors.addAll(neighbors);
		other.applications.addAll(applications);
		other.sharedNodes.addAll(sharedNodes);
		return other;
	}

	/**
	 * Return if the given HW node is shared with a neighboring colony.
	 */
	public boolean isShared(IHwNode s) {
		return sharedNodes.contains(s);
	}

	/**
	 * Empty the set of neighboring colonies.
	 */
	public void removeNeighbors() {
		neighbors.clear();
	}
}
