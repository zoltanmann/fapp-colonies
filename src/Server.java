import java.util.HashSet;
import java.util.Set;

public class Server implements IHwNode {
	private String id;
	private double cpuCap;
	private double ramCap;
	private Set<Link> links;

	public Server(String id, double cpuCap, double ramCap) {
		this.id=id;
		this.cpuCap = cpuCap;
		this.ramCap = ramCap;
		links=new HashSet<>();
	}

	public String getId() {
		return id;
	}

	public double getCpuCap() {
		return cpuCap;
	}

	public double getRamCap() {
		return ramCap;
	}

	public Set<Link> getLinks() {
		return links;
	}

	public void addLink(Link link) {
		links.add(link);
	}

	public String toString() {
		return id+"("+cpuCap+","+ramCap+")";
	}
}
