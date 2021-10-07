import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Keeps track of the components and connectors mapped on the infrastructure. There 
 * can be multiple BookKeeper objects for the same infrastructure, keeping track of 
 * different experiments.
 */
public class BookKeeper {
	/** Reference to the infrastructure for read-only access */
	private Infrastructure infra;
	/** Placement of components on servers */
	private Map<Component,Server> alpha;
	/** Routing of connectors via paths */
	private Map<Connector,Path> beta;
	/** Free CPU capacity of the servers */
	private Map<Server,Double> freeCpuCap;
	/** Free RAM capacity of the servers */
	private Map<Server,Double> freeRamCap;
	/** Available bandwidth of the links */
	private Map<Link,Double> freeBandwidth;

	/**
	 * Create new BookKeeper with no components and no connectors mapped.
	 */
	public BookKeeper(Infrastructure infrastructure) {
		this.infra=infrastructure;
		alpha=new HashMap<>();
		beta=new HashMap<>();
		freeCpuCap=new HashMap<>();
		for(Server s : infra.getServers())
			freeCpuCap.put(s,s.getCpuCap());
		freeRamCap=new HashMap<>();
		for(Server s : infra.getServers())
			freeRamCap.put(s,s.getRamCap());
		freeBandwidth=new HashMap<>();
		for(Link l : infra.getAllInternalLinks())
			freeBandwidth.put(l,l.getBw());
	}

	/**
	 * Return current mapping of components.
	 */
	public Map<Component,Server> getAlpha() {
		return alpha;
	}

	/**
	 * Return the server that hosts the given component, or null if the component is not placed.
	 */
	public Server getHost(Component c) {
		return alpha.get(c);
	}

	/**
	 * Return the path that the given connector is mapped on, or null if the connector 
	 * is not routed.
	 */
	public Path getPath(Connector c) {
		return beta.get(c);
	}

	/**
	 * Return the free CPU capacity of the given server.
	 */
	public double getFreeCpuCap(Server server) {
		return freeCpuCap.get(server);
	}

	/**
	 * Return the free RAM capacity of the given server.
	 */
	public double getFreeRamCap(Server server) {
		return freeRamCap.get(server);
	}

	/**
	 * Return the free bandwidth of the given link.
	 */
	public double getFreeBandwidth(Link link) {
		return freeBandwidth.get(link);
	}

	/**
	 * Return all components currently placed.
	 */
	public Set<Component> getComponents() {
		return alpha.keySet();
	}

	/**
	 * Return all components currently placed in the given colony.
	 */
	public Set<Component> getComponents(Colony colony) {
		Set<Component> result=new HashSet<>();
		for (Map.Entry<Component,Server> entry : alpha.entrySet()) {
			if(colony.getServers().contains(entry.getValue()))
				result.add(entry.getKey());
		}
		return result;
	}

	/**
	 * Return the infrastructure.
	 */
	public Infrastructure getInfra() {
		return infra;
	}

	/**
	 * Place the given component on the given server. Note that connectors of the component are not routed
	 * by this method. Note also that if the component is already placed, it should first be un-placed.
	 */
	public void place(Component c, Server s) {
		freeCpuCap.put(s,freeCpuCap.get(s)-c.getCpuReq());
		freeRamCap.put(s,freeRamCap.get(s)-c.getRamReq());
		alpha.put(c,s);
	}

	/**
	 * Remove a previously placed component. PRE: the component must be currently placed on a server.
	 * Note that the connectors of the component are not un-routed by this method.
	 */
	public void unPlace(Component c) {
		Server s=alpha.get(c);
		freeCpuCap.put(s,freeCpuCap.get(s)+c.getCpuReq());
		freeRamCap.put(s,freeRamCap.get(s)+c.getRamReq());
		alpha.remove(c);
	}

	/**
	 * Map the given connector on the given path. Note that if the connector is already routed, it 
	 * should first be un-routed.
	 */
	public void route(Connector conn, Path p) {
		for(Link l : p.getLinks())
			freeBandwidth.put(l,freeBandwidth.get(l)-conn.getBwReq());
		beta.put(conn,p);
	}

	/**
	 * Remove a previously routed connector. PRE: the connector must be currently mapped on a path.
	 */
	public void unRoute(Connector conn) {
		Path p=beta.get(conn);
		for(Link l : p.getLinks())
			freeBandwidth.put(l,freeBandwidth.get(l)+conn.getBwReq());
		beta.remove(conn);
	}
}
