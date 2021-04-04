import java.util.HashSet;
import java.util.Set;

public class EndDevice implements ISwNode, IHwNode {
	private String id;
	private Set<Link> links;

	public EndDevice(String id) {
		this.id=id;
		links=new HashSet<>();
	}

	public Set<Link> getLinks() {
		return links;
	}

	public void addLink(Link link) {
		links.add(link);
	}

	public String getId() {
		return id;
	}
}
