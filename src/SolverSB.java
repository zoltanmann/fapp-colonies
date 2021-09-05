import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SolverSB {
	private BookKeeper bookKeeper;

	public SolverSB(BookKeeper bookKeeper) {
		this.bookKeeper=bookKeeper;
	}

	private Path findRoute(Connector conn, IHwNode n1, IHwNode n2) {
		for(Path p : bookKeeper.getInfra().getPaths(n1,n2)) {
			boolean enoughBw=true;
			for(Link l : p.getLinks()) {
				if(bookKeeper.getFreeBandwidth(l) < conn.getBwReq()) {
					enoughBw=false;
					break;
				}
			}
			if(enoughBw && p.getLatency() <= conn.getMaxLatency())
				return p;
		}
		return null;
	}

	private boolean isPlaceable(Component c, Server s, Colony ourColony) {
		//A component received from colony k' may only be placed in k or k' 
		Colony targetColony=c.getTargetColony();
		if(targetColony!=ourColony && !ourColony.getServers().contains(s) && !targetColony.getServers().contains(s))
			return false;
		if(bookKeeper.getFreeCpuCap(s) < c.getCpuReq())
			return false;
		if(bookKeeper.getFreeRamCap(s) < c.getRamReq())
			return false;
		for(Connector conn : c.getConnectors()) {
			ISwNode otherVertex=conn.getOtherVertex(c);
			IHwNode otherHwNode;
			if(otherVertex.isEndDevice())
				otherHwNode=(EndDevice)otherVertex;
			else
				otherHwNode=bookKeeper.getHost((Component)otherVertex);
			if(otherHwNode==null) //other component has not been placed yet
				continue;
			if(findRoute(conn,s,otherHwNode)==null)
				return false;
		}
		return true;
	}

	private void place(Component c, Server s) {
		if(bookKeeper.getHost(c)!=null)
			unPlace(c);
		bookKeeper.place(c,s);
		for(Connector conn : c.getConnectors()) {
			ISwNode otherVertex=conn.getOtherVertex(c);
			IHwNode otherHwNode;
			if(otherVertex.isEndDevice())
				otherHwNode=(EndDevice)otherVertex;
			else
				otherHwNode=bookKeeper.getHost((Component)otherVertex);
			if(otherHwNode==null) //other component has not been placed yet
				continue;
			Path p=findRoute(conn,s,otherHwNode);
			bookKeeper.route(conn,p);
		}
	}

	private void unPlace(Component c) {
		if(bookKeeper.getHost(c)==null)
			return;
		bookKeeper.unPlace(c);
		for(Connector conn : c.getConnectors()) {
			bookKeeper.unRoute(conn);
		}
	}

	public Result optimize(
			Set<Server> freelyUsableServers,
			Set<Server> unpreferredServers,
			Set<Component> newComponents,
			Set<Component> fullyControlledComponents,
			Set<Component> obtainedComponents,
			Set<Component> readOnlyComponents,
			Colony ourColony) {
		long startTime=System.currentTimeMillis();
		Map<Component,Server> oldAlpha=bookKeeper.getAlpha();
		Result result=new Result();
		List<Server> servers=new ArrayList<>(freelyUsableServers);
		servers.addAll(unpreferredServers);
		List<Component> movableComponents=new ArrayList<>(fullyControlledComponents);
		movableComponents.addAll(obtainedComponents);
		List<Component> componentsToPlace=new ArrayList<>(newComponents);
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
					if(freelyUsableServers.contains(lhs) && unpreferredServers.contains(rhs))
						return -1;
					if(freelyUsableServers.contains(rhs) && unpreferredServers.contains(lhs))
						return 1;
					return Double.compare(bookKeeper.getFreeCpuCap(rhs)*bookKeeper.getFreeRamCap(rhs),bookKeeper.getFreeCpuCap(lhs)*bookKeeper.getFreeRamCap(lhs));
				}
			});
			boolean succeeded=false;
			for(Server server : servers) {
				if(isPlaceable(newComp,server,ourColony)) {
					place(newComp,server);
					succeeded=true;
					break;
				}
			}
			if(!succeeded) {
				for(Component oldComp : movableComponents) {
					Server oldServer=bookKeeper.getHost(oldComp);
					Server migrationTarget=null;
					for(Server newServer : servers) {
						if(newServer!=oldServer && isPlaceable(oldComp,newServer,ourColony)) {
							migrationTarget=newServer;
							break;
						}
					}
					if(migrationTarget!=null) {
						place(oldComp,migrationTarget);
						if(isPlaceable(newComp,oldServer,ourColony)) {
							place(newComp,oldServer);
							succeeded=true;
							break;
						} else {
							place(oldComp,oldServer);
						}
					}
				}
			}
			if(succeeded) {
				movableComponents.add(newComp);
				result.success=1;
			} else {
				for(Component c : newComponents)
					unPlace(c);
				result.success=0;
				break;
			}
		}
		result.migrations=0;
		Map<Component,Server> newAlpha=bookKeeper.getAlpha();
		for(Component c : movableComponents) {
			if(newAlpha.containsKey(c) && oldAlpha.containsKey(c) && newAlpha.get(c)!=oldAlpha.get(c))
				result.migrations++;
		}
		result.timeMs=System.currentTimeMillis()-startTime;
		System.out.println("Result: "+result);
		return result;
	}

}
