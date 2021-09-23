import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/**
 * Represents the hardware infrastructure, from cloud through fog nodes to end devices.
 */
public class Infrastructure {
	/** Set of all infrastructure nodes */
	private Set<IHwNode> nodes;
	/** Set of all servers, i.e., fog nodes and cloud */
	private Set<Server> servers;
	/** Set of all end devices */
	private Set<EndDevice> endDevices;
	/** Set of available paths for each pair of infrastructure nodes */
	private Map2d<IHwNode,IHwNode,Set<Path>> paths;
	/** Set of all available paths in the infrastructure */
	private Set<Path> allPaths;
	/** Set of all paths containing a given link */
	private Map<Link,Set<Path>> pathsOfLink;

	/**
	 * Constructs new, empty infrastructure.
	 */
	public Infrastructure() {
		nodes=new HashSet<>();
		servers=new HashSet<>();
		endDevices=new HashSet<>();
		paths=new Map2d<>();
		allPaths=new HashSet<>();
		pathsOfLink=new HashMap<>();
	}

	/**
	 * Add a new server (i.e., fog node or cloud) to the infrastructure.
	 */
	public void addServer(Server s) {
		nodes.add(s);
		servers.add(s);
	}

	/**
	 * Add a new end device to the infrastructure.
	 */
	public void addEndDevice(EndDevice dev) {
		nodes.add(dev);
		endDevices.add(dev);
	}

	/**
	 * Returns the set of all infrastructure nodes (cloud, fog nodes, end devices).
	 */
	public Set<IHwNode> getNodes() {
		return nodes;
	}

	/**
	 * Returns the set of all servers (i.e., cloud and fog nodes).
	 */
	public Set<Server> getServers() {
		return servers;
	}

	/**
	 * Returns the set of all end devices.
	 */
	public Set<EndDevice> getEndDevices() {
		return endDevices;
	}

	/**
	 * Determine some paths for each pair of infrastructure nodes. The method aims
	 * at finding k different paths for each (directed) pair of nodes, but some of 
	 * those paths may be the same, so the result is at most k paths for each pair 
	 * of nodes.
	 */
	public void determinePaths(int k) {
		for(IHwNode node : nodes) {
			for(int i=0;i<k;i++) {
				determinePathsFromNode(node);
			}
		}
		prunePaths();
		//so far, only the #paths field has been updated -> need to update the other fields too
		for(IHwNode n1 : nodes) {
			for(IHwNode n2 : nodes) {
				//if(n1==n2)
				//	continue;
				allPaths.addAll(paths.get(n1, n2));
			}
		}
		for(Path path : allPaths) {
			for(Link l : path.getLinks()) {
				if(!pathsOfLink.containsKey(l))
					pathsOfLink.put(l,new HashSet<>());
				pathsOfLink.get(l).add(path);
			}
		}
	}

	/**
	 * Determine a short path from the start node to all other infrastructure nodes
	 * using a randomized BFS. The results are stored in the {@link #paths} field.
	 */
	private void determinePathsFromNode(IHwNode start) {
		//perform BFS
		Map<IHwNode,Link> visitedThrough=new HashMap<>(); //need this for being able to retrieve the paths
		visitedThrough.put(start, null);
		Queue<IHwNode> toVisit=new LinkedList<>();
		toVisit.offer(start);
		while(!toVisit.isEmpty()) {
			IHwNode node=toVisit.poll();
			List<Link> links=new ArrayList<Link>(node.getLinks());
			Collections.shuffle(links); //randomize so that different runs may lead to different paths
			for(Link link : links) {
				IHwNode node2=link.getOtherNode(node);
				if(!nodes.contains(node2))
					continue; //ignore nodes in other regions
				if(!visitedThrough.containsKey(node2)) {
					visitedThrough.put(node2,link);
					toVisit.offer(node2);
				}
			}
		}
		//retrieve the paths backwards
		for(IHwNode node : visitedThrough.keySet()) {
			//if(node==start)
			//	continue;
			String pathId=node.getId()+"-"+start.getId();
			if(paths.containsKey(node, start))
				pathId=pathId+("~"+paths.get(node, start).size()+1); //so that path ID is unique
			Path path=new Path(pathId,node);
			IHwNode n=node;
			while(n!=start) {
				Link l=visitedThrough.get(n);
				n=l.getOtherNode(n);
				path.add(l,n);
			}
			if(!paths.containsKey(node, start)) //this is the first path between this pair of nodes
				paths.put(node, start, new HashSet<>());
			paths.get(node, start).add(path);
		}
	}

	/**
	 * Remove duplicate paths in the {@link #paths} field.
	 */
	private void prunePaths() {
		for(IHwNode n1 : nodes) {
			for(IHwNode n2 : nodes) {
				Set<Path> ps=paths.get(n1, n2); //old set of paths
				Set<Path> ps2=new HashSet<>(); //new set of paths
				for(Path p : ps) {
					boolean already=false;
					for(Path p2 : ps2) {
						if(p.isTheSame(p2)) {
							already=true;
						}
					}
					if(!already)
						ps2.add(p);
				}
				paths.put(n1, n2, ps2); //overwrite ps
			}
		}
	}

	/**
	 * Returns the set of available paths between the given pair of nodes. Before 
	 * calling this method, the method {@link #determinePaths(int)} must have been 
	 * called.
	 */
	public Set<Path> getPaths(IHwNode n1, IHwNode n2) {
		return paths.get(n1, n2);
	}

	/**
	 * Returns the set of all available paths . Before calling this method, the 
	 * method {@link #determinePaths(int)} must have been called.
	 */
	public Set<Path> getAllPaths() {
		return allPaths;
	}

	/**
	 * Returns the set of available paths containing the given link. Before calling 
	 * this method, the method {@link #determinePaths(int)} must have been called.
	 */
	public Set<Path> getPathsOfLink(Link l) {
		return pathsOfLink.get(l);
	}

	/**
	 * Outputs a readable description of the infrastructure to the standard output.
	 */
	public void print() {
		System.out.println("Nodes: "+nodes);
		System.out.println("Paths: "+paths);
	}

	/**
	 * Unite a set of infrastructures to a single infrastructure. Note that no path
	 * information is available in the returned infrastructure.
	 */
	public static Infrastructure unite(Infrastructure[] regions) {
		Infrastructure infra=new Infrastructure();
		for(Infrastructure region : regions) {
			infra.nodes.addAll(region.nodes);
			infra.servers.addAll(region.servers);
			infra.endDevices.addAll(region.endDevices);
		}
		return infra;
	}

	/**
	 * If there are k parallel links between two nodes, then k-1 of those links are
	 * removed.
	 */
	public void pruneParallelLinks() {
		for(IHwNode node : nodes) {
			Map<IHwNode,Link> neighbors=new HashMap<>();
			Set<Link> linksToPrune=new HashSet<>(); //to avoid ConcurrentModificationException, we collect first the links to be removed, and remove them in a later step
			for(Link link : node.getLinks()) {
				IHwNode neighbor=link.getOtherNode(node);
				if(neighbors.containsKey(neighbor))
					linksToPrune.add(link);
				neighbors.put(neighbor,link);
			}
			for(Link link : linksToPrune) {
				link.getV1().removeLink(link);
				link.getV2().removeLink(link);
			}
		}
	}

	/**
	 * Returns the set of links that connect two nodes within the infrastructure. If
	 * this infrastructure is a colony that also has links to other colonies, those are
	 * not in the returned set.
	 */
	public Set<Link> getAllInternalLinks() {
		Set<Link> links=new HashSet<>();
		for(IHwNode node : nodes) {
			for(Link link : node.getLinks()) {
				if(nodes.contains(link.getOtherNode(node)))
					links.add(link);
			}
		}
		return links;
	}

	/**
	 * Return the part of the infrastructure that belongs to the given set of
	 * colonies, and the cloud. Note that the returned infrastructure also contains
	 * the path information.
	 */
	public Infrastructure getSubInfra(Set<Colony> colonies, Server cloud) {
		Infrastructure subInfra=new Infrastructure();
		for(Colony colony : colonies) {
			for(Server s : colony.getServers())
				subInfra.addServer(s);
			for(EndDevice d : colony.getEndDevices())
				subInfra.addEndDevice(d);
		}
		subInfra.addServer(cloud);
		for(IHwNode n1 : subInfra.nodes) {
			for(IHwNode n2 : subInfra.nodes) {
				subInfra.paths.put(n1, n2, paths.get(n1, n2));
				subInfra.allPaths.addAll(paths.get(n1, n2));
			}
		}
		for(IHwNode n : subInfra.nodes) {
			for(Link l : n.getLinks()) {
				if(subInfra.nodes.contains(l.getOtherNode(n))) {
					subInfra.pathsOfLink.put(l, new HashSet<>());
					for(Path p : pathsOfLink.get(l)) {
						if(subInfra.containsPath(p))
							subInfra.pathsOfLink.get(l).add(p);
					}
				}
			}
		}
		return subInfra;
	}

	/**
	 * Return the part of the infrastructure that belongs to the given colony and 
	 * potentially its neighbors (if withNeighborColonies==true), and the cloud. Note 
	 * that the returned infrastructure also contains the path information.
	 */
	public Infrastructure getSubInfra(Colony colony, Server cloud, boolean withNeighborColonies) {
		Set<Colony> colonies=new HashSet<>();
		colonies.add(colony);
		if(withNeighborColonies) {
			for(Colony c2 : colony.getNeighbors())
				colonies.add(c2);
		}
		return getSubInfra(colonies, cloud);
	}

	/**
	 * Determine if the given path lies completely in this infrastructure.
	 */
	public boolean containsPath(Path p) {
		for(IHwNode n : p.getNodes()) {
			if(!nodes.contains(n))
				return false;
		}
		return true;
	}
}
