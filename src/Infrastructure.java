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

	public Infrastructure() {
		nodes=new HashSet<>();
		servers=new HashSet<>();
		endDevices=new HashSet<>();
		paths=new Map2d<>();
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
				pathId=pathId+(paths.get(node, start).size()+1);
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

	public void print() {
		System.out.println("Nodes: "+nodes);
		System.out.println("Paths: "+paths);
	}
}
