import java.util.ArrayList;
import java.util.List;

/**
 * Represents a path in the infrastructure. A path consists of k>=1 nodes and k-1
 * links.
 */
public class Path {
	/** Unique ID of the path */
	private String id;
	/** List of nodes along the path */
	private List<IHwNode> nodes;
	/** List of links along the path */
	private List<Link> links;
	/** Total latency along the path */
	private double latency;

	/**
	 * Constructs path with the given ID and starting node.
	 */
	public Path(String id, IHwNode start) {
		this.id=id;
		nodes=new ArrayList<>();
		nodes.add(start);
		links=new ArrayList<>();
		latency=0;
	}

	/**
	 * Grow the path by adding a further link and further node to its tail.
	 */
	public void add(Link link, IHwNode node) {
		links.add(link);
		nodes.add(node);
		latency+=link.getLatency();
	}

	/**
	 * Returns string representation of the path.
	 */
	public String toString() {
		StringBuffer sb=new StringBuffer();
		sb.append("[");
		boolean start=true;
		for(IHwNode n : nodes) {
			if(!start)
				sb.append(", ");
			sb.append(n.getId());
			start=false;
		}
		sb.append("]");
		return sb.toString();
	}

	/**
	 * Returns the ID of the path.
	 */
	public String getId() {
		return id;
	}

	/**
	 * Decides if two paths are the same, i.e., they consist of the same sequence of
	 * links.
	 */
	public boolean isTheSame(Path other) {
		if(links.size()!=other.links.size())
			return false;
		for(int i=0;i<links.size();i++) {
			if(links.get(i)!=other.links.get(i))
				return false;
		}
		return true;
	}

	/**
	 * True iff the given link is contained in the path.
	 */
	public boolean contains(Link l) {
		return links.contains(l);
	}

	/**
	 * Returns the total latency of the path.
	 */
	public double getLatency() {
		return latency;
	}

	/**
	 * Returns the list of links along the path.
	 */
	public List<Link> getLinks() {
		return links;
	}

	/**
	 * Returns the list of nodes along the path.
	 */
	public List<IHwNode> getNodes() {
		return nodes;
	}
}
