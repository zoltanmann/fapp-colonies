import java.util.ArrayList;
import java.util.List;

public class Path {
	private String id;
	private List<IHwNode> nodes;
	private List<Link> links;
	private double latency;

	public Path(String id, IHwNode start) {
		this.id=id;
		nodes=new ArrayList<>();
		nodes.add(start);
		links=new ArrayList<>();
		latency=0;
	}

	public void add(Link link, IHwNode node) {
		links.add(link);
		nodes.add(node);
		latency+=link.getLatency();
	}

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

	public String getId() {
		return id;
	}

	public boolean isTheSame(Path other) {
		if(links.size()!=other.links.size())
			return false;
		for(int i=0;i<links.size();i++) {
			if(links.get(i)!=other.links.get(i))
				return false;
		}
		return true;
	}

	public boolean contains(Link l) {
		return links.contains(l);
	}

	public double getLatency() {
		return latency;
	}

	public List<Link> getLinks() {
		return links;
	}

	public List<IHwNode> getNodes() {
		return nodes;
	}
}
