import java.util.Set;

public interface IHwNode {
	public String getId();
	public Set<Link> getLinks();
	public void addLink(Link link);
}
