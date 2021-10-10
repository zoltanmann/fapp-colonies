import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Search-based solver. Solves the optimization problem by trying to place new
 * components one by one in decreasing order of their size. If this is not successful,
 * the algorithm tries to migrate some already placed components to see if this way
 * more components can be placed. Note that the solver is stateless (the state is
 * maintained by the BookKeeper), i.e., the same solver object can be applied to 
 * different problem instances.
 */
public class SolverSB implements ISolver {
	/** Reference to the bookKeeper */
	private BookKeeper bookKeeper;

	/**
	 * Constructor.
	 */
	public SolverSB(BookKeeper bookKeeper) {
		this.bookKeeper=bookKeeper;
	}

	/**
	 * Tries to route the given connector between the given infrastructure nodes.
	 * Returns either a valid path or null if no valid path could not be found.
	 */
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

	/**
	 * Determines if the given component can be placed on the given server AND each
	 * connector that is incident to the given component and goes to an already placed
	 * component can be routed if the given component is placed on the given server.
	 */
	private boolean isPlaceable(Component c, Server s, Colony ourColony) {
		//A component that colony k received from colony k' may only be placed in k or k' 
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
		//TODO: problem the fact that each connector is routeable on its own does not mean that they can all be routed at once
		return true;
	}

	/**
	 * Place the given component on the given server AND route each connector that is
	 * incident to the given component and goes to an already placed component. If
	 * the given component already had a host (i.e., it has to be migrated), then 
	 * unPlace() is called first, together with unRoute() for the incident connectors. 
	 * NB: this method does not check placeability; isPraceable() has to be called 
	 * before this method.
	 */
	private void place(Component c, Server s) {
		if(bookKeeper.getHost(c)!=null) {
			unPlace(c);
			for(Connector conn : c.getConnectors()) {
				bookKeeper.unRoute(conn);
			}
		}
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

	/**
	 * Removes the given component from its current host AND un-routes all connectors
	 * incident to this component. 
	 */
	private void unPlace(Component c) {
		if(bookKeeper.getHost(c)==null)
			return;
		bookKeeper.unPlace(c);
		for(Connector conn : c.getConnectors()) {
			bookKeeper.unRoute(conn);
		}
	}

	/**
	 * Perform an optimization run, trying to place the new components.
	 */
	@Override
	public Result optimize(
			Set<Server> freelyUsableServers,
			Set<Server> unpreferredServers,
			Set<Component> newComponents,
			Set<Component> fullyControlledComponents,
			Set<Component> obtainedComponents,
			Set<Component> readOnlyComponents,
			Colony ourColony) {
		long startTime=System.currentTimeMillis();
		Map<Component,Server> oldAlpha=bookKeeper.getAlpha(); //we save it so that we can compute the number of migrations in the end
		Result result=new Result();
		List<Server> servers=new ArrayList<>(freelyUsableServers);
		servers.addAll(unpreferredServers);
		List<Component> movableComponents=new ArrayList<>(fullyControlledComponents);
		movableComponents.addAll(obtainedComponents);
		List<Component> componentsToPlace=new ArrayList<>(newComponents);
		Collections.sort(componentsToPlace,new Comparator<Component>() { //we sort the components to be placed in increasing order of their size
			@Override
			public int compare(Component lhs,Component rhs) {
				return Double.compare(lhs.getCpuReq()*lhs.getRamReq(),rhs.getCpuReq()*rhs.getRamReq());
			}
		});
		while(componentsToPlace.size()>0) {
			Component newComp=componentsToPlace.remove(componentsToPlace.size()-1); //we pick the biggest of the components that still need to be placed
			Collections.sort(servers,new Comparator<Server>() { //we sort the servers such that the best servers are at the beginning. This has to be repeated each time, since the free capacity of servers may change
				@Override
				public int compare(Server lhs,Server rhs) {
					if(freelyUsableServers.contains(lhs) && unpreferredServers.contains(rhs))
						return -1; // the freely usable servers are first, the unpreferred servers only after them
					if(freelyUsableServers.contains(rhs) && unpreferredServers.contains(lhs))
						return 1;
					return Double.compare(bookKeeper.getFreeCpuCap(rhs)*bookKeeper.getFreeRamCap(rhs),bookKeeper.getFreeCpuCap(lhs)*bookKeeper.getFreeRamCap(lhs)); //within a category, servers with more free capacity are better
				}
			});
			//try to place the component on one of the servers
			boolean succeeded=false;
			for(Server server : servers) {
				if(isPlaceable(newComp,server,ourColony)) {
					place(newComp,server);
					succeeded=true;
					break;
				}
			}
			if(!succeeded) { //if we didn't succeed, we try if the migration of an already placed component helps
				for(Component oldComp : movableComponents) { //for each movable component, we try to find a new host
					Server oldServer=bookKeeper.getHost(oldComp);
					Server migrationTarget=null;
					for(Server newServer : servers) {
						if(newServer!=oldServer && isPlaceable(oldComp,newServer,ourColony)) {
							migrationTarget=newServer;
							break;
						}
					}
					if(migrationTarget!=null) { //if we managed to find a new host for this existing component, we move it there provisionally
						place(oldComp,migrationTarget);
						if(isPlaceable(newComp,oldServer,ourColony)) { //if this way the relieved server can host the new component, then all is good
							place(newComp,oldServer);
							succeeded=true;
							break;
						} else { //if not, then we move back the provisionally moved component to avoid a useless migration
							place(oldComp,oldServer);
						}
					}
				}
			}
			if(succeeded) { //if either directly or after a migration the component could be placed
				movableComponents.add(newComp);
				result.success=1;
			} else { //if not, then we un-place the whole application
				for(Component c : newComponents)
					unPlace(c); //has no effect if c is not placed yet
				result.success=0;
				break;
			}
		}
		//calculate number of migrations
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
