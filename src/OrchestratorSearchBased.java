import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class OrchestratorSearchBased {
	private Infrastructure infra;
	private Set<Component> components;
	private Map<Component,Server> alpha;
	private Map<Connector,Path> beta;
	private Map<Server,Double> freeCpuCap;
	private Map<Server,Double> freeRamCap;
	private Map<Link,Double> freeBandwidth;

	public OrchestratorSearchBased(Infrastructure infra) {
		this.infra=infra;
		components=new HashSet<>();
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

	private Path findRoute(Connector conn, IHwNode n1, IHwNode n2) {
		for(Path p : infra.getPaths(n1,n2)) {
			boolean enoughBw=true;
			for(Link l : p.getLinks()) {
				if(freeBandwidth.get(l)<conn.getBwReq()) {
					enoughBw=false;
					break;
				}
			}
			if(enoughBw && p.getLatency() <= conn.getMaxLatency())
				return p;
		}
		return null;
	}

	private boolean isPlaceable(Component c, Server s) {
		if(freeCpuCap.get(s)<c.getCpuReq())
			return false;
		if(freeRamCap.get(s)<c.getRamReq())
			return false;
		for(Connector conn : c.getConnectors()) {
			ISwNode otherVertex=conn.getOtherVertex(c);
			if(otherVertex.isEndDevice() || components.contains(otherVertex)) {
				IHwNode otherHwNode;
				if(otherVertex.isEndDevice())
					otherHwNode=(EndDevice)otherVertex;
				else
					otherHwNode=alpha.get(otherVertex);
				if(findRoute(conn,s,otherHwNode)==null)
					return false;
			}
		}
		return true;
	}

	private void place(Component c, Server s) {
		if(alpha.containsKey(c))
			unPlace(c);
		alpha.put(c,s);
		components.add(c);
		freeCpuCap.put(s,freeCpuCap.get(s)-c.getCpuReq());
		freeRamCap.put(s,freeRamCap.get(s)-c.getRamReq());
		for(Connector conn : c.getConnectors()) {
			ISwNode otherVertex=conn.getOtherVertex(c);
			if(otherVertex.isEndDevice() || components.contains(otherVertex)) {
				IHwNode otherHwNode;
				if(otherVertex.isEndDevice())
					otherHwNode=(EndDevice)otherVertex;
				else
					otherHwNode=alpha.get(otherVertex);
				Path p=findRoute(conn,s,otherHwNode);
				for(Link l : p.getLinks())
					freeBandwidth.put(l,freeBandwidth.get(l)-conn.getBwReq());
				beta.put(conn,p);
			}
		}
		alpha.put(c,s);
		components.add(c);
	}

	private void unPlace(Component c) {
		if(!alpha.containsKey(c))
			return;
		Server s=alpha.get(c);
		freeCpuCap.put(s,freeCpuCap.get(s)+c.getCpuReq());
		freeRamCap.put(s,freeRamCap.get(s)+c.getRamReq());
		for(Connector conn : c.getConnectors()) {
			Path p=beta.get(conn);
			for(Link l : p.getLinks())
				freeBandwidth.put(l,freeBandwidth.get(l)+conn.getBwReq());
			beta.remove(conn);
		}
		alpha.remove(c);
		components.remove(c);
	}

	public Result addApplication(Application app) {
		long startTime=System.currentTimeMillis();
		Map<Component,Server> oldAlpha=new HashMap<>(alpha);
		Result result=new Result();
		result.success=1;
		List<Server> servers=new ArrayList<>(infra.getServers());
		List<Component> componentsToPlace=app.getComponents();
		Collections.sort(componentsToPlace,new Comparator<Component>() {
			@Override
			public int compare(Component lhs,Component rhs) {
				return Double.compare(rhs.getCpuReq()*rhs.getRamReq(),lhs.getCpuReq()*lhs.getRamReq());
			}
		});
		while(componentsToPlace.size()>0) {
			Component newComp=componentsToPlace.remove(componentsToPlace.size()-1);
			Collections.sort(servers,new Comparator<Server>() {
				@Override
				public int compare(Server lhs,Server rhs) {
					return Double.compare(rhs.getCpuCap()*rhs.getRamCap(),lhs.getCpuCap()*lhs.getRamCap());
				}
			});
			boolean succeeded=false;
			for(Server server : servers) {
				if(isPlaceable(newComp,server)) {
					place(newComp,server);
					succeeded=true;
					break;
				}
			}
			if(!succeeded) {
				for(Component oldComp : components) {
					Server oldServer=alpha.get(oldComp);
					Server migrationTarget=null;
					for(Server newServer : servers) {
						if(newServer!=oldServer && isPlaceable(oldComp,newServer)) {
							migrationTarget=newServer;
							break;
						}
					}
					if(migrationTarget!=null) {
						place(oldComp,migrationTarget);
						if(isPlaceable(newComp,oldServer)) {
							place(newComp,oldServer);
							succeeded=true;
							break;
						} else {
							place(oldComp,oldServer);
						}
					}
				}
			}
			if(!succeeded) {
				for(Component c : app.getComponents())
					unPlace(c);
				result.success=0;
				break;
			}
		}
		result.migrations=0;
		for(Component c : components) {
			if(alpha.containsKey(c) && oldAlpha.containsKey(c) && alpha.get(c)!=oldAlpha.get(c))
				result.migrations++;
		}
		result.timeMs=System.currentTimeMillis()-startTime;
		System.out.println("Success: "+result.success);
		System.out.println("TimeMs: "+result.timeMs);
		return result;
	}

	public void print() {
		System.out.println(alpha);
	}
}
