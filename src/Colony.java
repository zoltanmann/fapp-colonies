import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Colony {
	private Set<IHwNode> nodes;
	private List<Server> fogNodes;
	private List<EndDevice> endDevices;
	private Set<Colony> neighbors;
	private List<Application> applications;

	public Colony() {
		nodes=new HashSet<>();
		fogNodes=new ArrayList<>();
		endDevices=new ArrayList<>();
		neighbors=new HashSet<>();
		applications=new ArrayList<>();
	}

	public void addFogNode(Server node) {
		fogNodes.add(node);
		nodes.add(node);
	}

	public void addEndDevice(EndDevice node) {
		endDevices.add(node);
		nodes.add(node);
	}

	public Server getRandomFogNode() {
		return fogNodes.get(Main.random.nextInt(fogNodes.size()));
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

	public List<Server> getFogNodes() {
		return fogNodes;
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
}
