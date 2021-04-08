import java.util.HashSet;
import java.util.Set;

public class EndDevice implements ISwNode, IHwNode {
	private String id;
	private Set<Link> links;
	private Set<Connector> connectors;

	public EndDevice(String id) {
		this.id=id;
		links=new HashSet<>();
		connectors=new HashSet<>();
	}

	public Set<Link> getLinks() {
		return links;
	}

	public void addLink(Link link) {
		links.add(link);
	}

	public void removeLink(Link link) {
		links.remove(link);
	}

	public void addConnector(Connector c) {
		connectors.add(c);
	}

	public Set<Connector> getConnectors() {
		return connectors;
	}

	public String getId() {
		return id;
	}
}
