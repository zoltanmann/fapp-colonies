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

	interface IAction {
		void doIt();
		void undo();
	}

	class PlaceAction implements IAction {
		private Component c;
		private Server s;

		public PlaceAction(Component c,Server s) {
			this.c=c;
			this.s=s;
		}

		@Override
		public void doIt() {
			bookKeeper.place(c,s);
		}

		@Override
		public void undo() {
			bookKeeper.unPlace(c);
		}
	}

	class UnPlaceAction implements IAction {
		private Component c;
		private Server s;

		public UnPlaceAction(Component c,Server s) {
			this.c=c;
			this.s=s;
		}

		@Override
		public void doIt() {
			bookKeeper.unPlace(c);
		}

		@Override
		public void undo() {
			bookKeeper.place(c,s);
		}
	}

	class RouteAction implements IAction {
		private Connector c;
		private Path p;

		public RouteAction(Connector c,Path p) {
			this.c=c;
			this.p=p;
		}

		@Override
		public void doIt() {
			bookKeeper.route(c,p);
		}

		@Override
		public void undo() {
			bookKeeper.unRoute(c);
		}
	}

	class UnRouteAction implements IAction {
		private Connector c;
		private Path p;

		public UnRouteAction(Connector c,Path p) {
			this.c=c;
			this.p=p;
		}

		@Override
		public void doIt() {
			bookKeeper.unRoute(c);
		}

		@Override
		public void undo() {
			bookKeeper.route(c,p);
		}
	}

	class ActionStack {
		private List<IAction> actions;

		public ActionStack() {
			actions=new ArrayList<>();
		}

		public void perform(IAction action) {
			action.doIt();
			actions.add(action);
		}

		public int getSize() {
			return actions.size();
		}

		public void rollback(int targetSize) {
			while(actions.size()>targetSize) {
				IAction action=actions.get(actions.size()-1);
				action.undo();
				actions.remove(actions.size()-1);
			}
		}
	}

	private ActionStack actionStack;

	/**
	 * Constructor.
	 */
	public SolverSB(BookKeeper bookKeeper) {
		this.bookKeeper=bookKeeper;
		actionStack=new ActionStack();
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
	 * Try to place the given component on the given server AND to route each connector that is 
	 * incident to the given component and goes to an already placed component. If successful, return
	 * true. Otherwise, undo the changes and return false.
	 */
	private boolean tryToPlace(Component c,Server s,Colony ourColony) {
		boolean success=true;
		//A component that colony k received from colony k' may only be placed in k or k' 
		Colony targetColony=c.getTargetColony();
		if(targetColony!=ourColony && !ourColony.getServers().contains(s) && !targetColony.getServers().contains(s))
			return false;
		if(bookKeeper.getFreeCpuCap(s) < c.getCpuReq())
			return false;
		if(bookKeeper.getFreeRamCap(s) < c.getRamReq())
			return false;
		int startStackSize=actionStack.getSize();
		actionStack.perform(new PlaceAction(c,s));
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
			if(p!=null)
				actionStack.perform(new RouteAction(conn,p));
			else {
				success=false;
				break;
			}
		}
		if(!success)
			actionStack.rollback(startStackSize);
		return success;
	}

	private boolean tryToMigrate(Component c,Server newServer,Colony ourColony) {
		int startSize=actionStack.getSize();
		Server oldServer=bookKeeper.getHost(c);
		actionStack.perform(new UnPlaceAction(c,oldServer));
		for(Connector conn : c.getConnectors()) {
			if(bookKeeper.getPath(conn)!=null)
				actionStack.perform(new UnRouteAction(conn,bookKeeper.getPath(conn)));
		}
		boolean success=tryToPlace(c,newServer,ourColony);
		if(!success)
			actionStack.rollback(startSize);
		return success;
	}

	/**
	 * Helper method to create the union of an arbitrary number of sets of the same type of objects in 
	 * the form a single list.
	 */
	@SafeVarargs
	private static <T> List<T> union(Set<T>... sets) {
		List<T> result=new ArrayList<>();
		for(Set<T> s : sets)
			result.addAll(s);
		return result;
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
		List<Server> servers=union(freelyUsableServers,unpreferredServers);
		List<Component> movableComponents=union(fullyControlledComponents,obtainedComponents);
		List<Component> componentsToPlace=new ArrayList<>(newComponents);
		Collections.sort(componentsToPlace,new Comparator<Component>() { //we sort the components to be placed in increasing order of their size
			@Override
			public int compare(Component lhs,Component rhs) {
				return Double.compare(lhs.getCpuReq()*lhs.getRamReq(),rhs.getCpuReq()*rhs.getRamReq());
			}
		});
		int beginning=actionStack.getSize();
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
				if(tryToPlace(newComp,server,ourColony)) {
					succeeded=true;
					break;
				}
			}
			if(!succeeded) { //if we didn't succeed, we try if the migration of an already placed component helps
				for(Component oldComp : movableComponents) { //for each movable component, we try to find a new host
					Server oldServer=bookKeeper.getHost(oldComp);
					Server migrationTarget=null;
					int beforeMigration=actionStack.getSize();
					for(Server newServer : servers) {
						if(newServer!=oldServer && tryToMigrate(oldComp,newServer,ourColony)) {
							migrationTarget=newServer;
							break;
						}
					}
					if(migrationTarget!=null) { //if we managed to migrate this existing component
						if(tryToPlace(newComp,oldServer,ourColony)) { //if this way the relieved server can host the new component, then all is good
							succeeded=true;
							break;
						} else { //if not, then we move back the provisionally moved component to avoid a useless migration
							actionStack.rollback(beforeMigration);
						}
					}
				}
			}
			if(succeeded) { //if either directly or after a migration the component could be placed
				movableComponents.add(newComp);
				result.success=1;
			} else { //if not, then we un-place the whole application
				actionStack.rollback(beginning);
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
