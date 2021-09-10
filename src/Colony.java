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
	/** The set of all servers in the colony, including fog nodes and the cloud */
	private List<Server> servers;
	private List<EndDevice> endDevices;
	private Set<Colony> neighbors;
	private List<Application> applications;
	private Set<Server> sharedNodes;

	public Colony() {
		nodes=new HashSet<>();
		servers=new ArrayList<>();
		endDevices=new ArrayList<>();
		neighbors=new HashSet<>();
		applications=new ArrayList<>();
		sharedNodes=new HashSet<>();
	}

	public void addServer(Server node) {
		servers.add(node);
		nodes.add(node);
	}

	public void addEndDevice(EndDevice node) {
		endDevices.add(node);
		nodes.add(node);
	}

	public Server getRandomServer() {
		return servers.get(Main.random.nextInt(servers.size()));
	}

	public EndDevice getRandomEndDevice() {
		return endDevices.get(Main.random.nextInt(endDevices.size()));
	}

	public void addNeighbor(Colony col) {
		neighbors.add(col);
	}

	public void addApplication(Application a) {
		applications.add(a);
	}

	public Application getApplication(int i) {
		return applications.get(i);
	}

	public List<Server> getServers() {
		return servers;
	}

	public List<EndDevice> getEndDevices() {
		return endDevices;
	}

	public Set<Colony> getNeighbors() {
		return neighbors;
	}

	public boolean isAdjacentTo(Colony other) {
		return neighbors.contains(other);
	}

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

	public Set<Server> getSharedNodes() {
		return sharedNodes;
	}

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

	public boolean isShared(IHwNode s) {
		return sharedNodes.contains(s);
	}

	public void removeNeighbors() {
		neighbors.clear();
	}
}
