import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

public class Infrastructure {
	private Set<IHwNode> nodes;
	private Set<Server> servers;
	private Set<EndDevice> endDevices;
	private Map2d<IHwNode,IHwNode,Set<Path>> paths;
	private Set<Path> allPaths;
	private Map<Link,Set<Path>> pathsOfLink;

	public Infrastructure() {
		nodes=new HashSet<>();
		servers=new HashSet<>();
		endDevices=new HashSet<>();
		paths=new Map2d<>();
		allPaths=new HashSet<>();
		pathsOfLink=new HashMap<>();
	}

	public void addServer(Server s) {
		nodes.add(s);
		servers.add(s);
	}

	public void addEndDevice(EndDevice dev) {
		nodes.add(dev);
		endDevices.add(dev);
	}

	public Set<IHwNode> getNodes() {
		return nodes;
	}

	public Set<Server> getServers() {
		return servers;
	}

	public Set<EndDevice> getEndDevices() {
		return endDevices;
	}

	public void determinePaths(int k) {
		for(IHwNode node : nodes) {
			for(int i=0;i<k;i++) {
				determinePathsFromNode(node);
			}
		}
		prunePaths();
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

	private void determinePathsFromNode(IHwNode start) {
		//perform BFS
		Map<IHwNode,Link> visitedThrough=new HashMap<>();
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
				pathId=pathId+("~"+paths.get(node, start).size()+1);
			Path path=new Path(pathId,node);
			IHwNode n=node;
			while(n!=start) {
				Link l=visitedThrough.get(n);
				n=l.getOtherNode(n);
				path.add(l,n);
			}
			if(!paths.containsKey(node, start))
				paths.put(node, start, new HashSet<>());
			paths.get(node, start).add(path);
		}
	}

	private void prunePaths() {
		for(IHwNode n1 : nodes) {
			for(IHwNode n2 : nodes) {
				Set<Path> ps=paths.get(n1, n2);
				Set<Path> ps2=new HashSet<>();
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
				paths.put(n1, n2, ps2);
			}
		}
	}

	public Set<Path> getPaths(IHwNode n1, IHwNode n2) {
		return paths.get(n1, n2);
	}

	public Set<Path> getAllPaths() {
		return allPaths;
	}

	public Set<Path> getPathsOfLink(Link l) {
		return pathsOfLink.get(l);
	}

	public void print() {
		System.out.println("Nodes: "+nodes);
		System.out.println("Paths: "+paths);
	}

	public static Infrastructure unite(Infrastructure[] regions) {
		Infrastructure infra=new Infrastructure();
		for(Infrastructure region : regions) {
			infra.nodes.addAll(region.nodes);
			infra.servers.addAll(region.servers);
			infra.endDevices.addAll(region.endDevices);
		}
		return infra;
	}

	public void pruneParallelLinks() {
		for(IHwNode node : nodes) {
			Map<IHwNode,Link> neighbors=new HashMap<>();
			Set<Link> linksToPrune=new HashSet<>();
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

	public Infrastructure getSubInfra(Set<Colony> colonies, Server cloud) {
		Infrastructure subInfra=new Infrastructure();
		for(Colony colony : colonies) {
			for(Server s : colony.getFogNodes())
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

	public Infrastructure getSubInfra(Colony colony, Server cloud, boolean withNeighborColonies) {
		Set<Colony> colonies=new HashSet<>();
		colonies.add(colony);
		if(withNeighborColonies) {
			for(Colony c2 : colony.getNeighbors())
				colonies.add(c2);
		}
		return getSubInfra(colonies, cloud);
	}

	public boolean containsPath(Path p) {
		for(IHwNode n : p.getNodes()) {
			if(!nodes.contains(n))
				return false;
		}
		return true;
	}
}
