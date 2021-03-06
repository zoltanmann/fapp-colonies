import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Search-based solver. Solves the optimization problem by trying to place new
 * components one by one. If this is not successful, the algorithm tries to 
 * migrate some already placed components to see if this way more components can 
 * be placed. Note that the solver does not maintain its state between invocations 
 * (the state is maintained by the BookKeeper), i.e., the same solver object can 
 * be applied to different problem instances.
 */
public class SolverSB implements ISolver {
	/** Reference to the bookKeeper */
	private BookKeeper bookKeeper;

	/** Interface for undoable placement and routing actions */
	interface IAction {
		void doIt();
		void undo();
	}

	/** Represents a placement action */
	class PlaceAction implements IAction {
		/** Component to place */
		private Component c;
		/** Server that should act as host */
		private Server s;

		/** Construct new action of placing component c on server s */
		public PlaceAction(Component c,Server s) {
			this.c=c;
			this.s=s;
		}

		/** Carry out the placement action */
		@Override
		public void doIt() {
			bookKeeper.place(c,s);
		}

		/** Undo the placement action */
		@Override
		public void undo() {
			bookKeeper.unPlace(c);
		}
	}

	/** Represents the action of removing a component from a server (with the aim of migrating it) */
	class UnPlaceAction implements IAction {
		/** Component to un-place */
		private Component c;
		/** Old host of the component */
		private Server s;

		/** Construct new action of removing component c from server s */
		public UnPlaceAction(Component c,Server s) {
			this.c=c;
			this.s=s;
		}

		/** Carry out the un-placement action */
		@Override
		public void doIt() {
			bookKeeper.unPlace(c);
		}

		/** Undo the un-placement action */
		@Override
		public void undo() {
			bookKeeper.place(c,s);
		}
	}

	/** Represents the action of routing a connector via a path */
	class RouteAction implements IAction {
		/** Connector to route */
		private Connector c;
		/** Path via which the connector should be routed */
		private Path p;

		/** Construct new action of routing connector c via path p */
		public RouteAction(Connector c,Path p) {
			this.c=c;
			this.p=p;
		}

		/** Carry out the routing action */
		@Override
		public void doIt() {
			bookKeeper.route(c,p);
		}

		/** Undo the routing action */
		@Override
		public void undo() {
			bookKeeper.unRoute(c);
		}
	}

	/** Represents the action of un-routing a connector from a path (as part of a migration) */
	class UnRouteAction implements IAction {
		/** Connector to un-route */
		private Connector c;
		/** Path via which the connector was routed */
		private Path p;

		/** Construct new action of un-routing connector c from paath p */
		public UnRouteAction(Connector c,Path p) {
			this.c=c;
			this.p=p;
		}

		/** Carry out the un-routing action */
		@Override
		public void doIt() {
			bookKeeper.unRoute(c);
		}

		/** Undo the un-routing action */
		@Override
		public void undo() {
			bookKeeper.route(c,p);
		}
	}

	/** Stack of undoable actions, which supports rollback to a given point */
	class ActionStack {
		/** The list of actions */
		private List<IAction> actions;

		/** Construct empty stack */
		public ActionStack() {
			actions=new ArrayList<>();
		}

		/** Carry out the given action and add it to the stack, so that it can be undone if necessary */
		public void perform(IAction action) {
			action.doIt();
			actions.add(action);
		}

		/** Get the current size of the stack, which can be later used to specify the target of a rollback */
		public int getSize() {
			return actions.size();
		}

		/** Undo actions until the stack shrinks to the given size */
		public void rollback(int targetSize) {
			while(actions.size()>targetSize) {
				IAction action=actions.get(actions.size()-1);
				action.undo();
				actions.remove(actions.size()-1);
			}
		}
	}

	/** The stack of undoable actions */
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
	 * Returns either a valid path or null if no valid path could be found.
	 */
	private Path findRoute(Connector conn,IHwNode n1,IHwNode n2,Set<IHwNode> allHwNodes) {
		for(Path p : bookKeeper.getInfra().getPaths(n1,n2)) {
			if(p.getLatency() > conn.getMaxLatency())
				continue;
			boolean relevant=true;
			for(IHwNode n : p.getNodes()) {
				if(!allHwNodes.contains(n)) {
					relevant=false;
					break;
				}
			}
			if(!relevant)
				continue;
			boolean enoughBw=true;
			for(Link l : p.getLinks()) {
				if(bookKeeper.getFreeBandwidth(l) < conn.getBwReq()) {
					enoughBw=false;
					break;
				}
			}
			if(enoughBw)
				return p;
		}
		return null;
	}

	/**
	 * Try to place the given component on the given server AND to route each connector that is 
	 * incident to the given component and goes to an already placed component or to an end device. 
	 * If successful, return true. Otherwise, undo the changes and return false.
	 */
	private boolean tryToPlace(Component c,Server s,Colony ourColony,Set<IHwNode> allHwNodes,Conductor.ModeType mode) {
		boolean success=true;
		if(mode==Conductor.ModeType.communicating) {
			//A component that colony k received from colony k' may only be placed in k or k' 
			int targetColony=c.getTargetColony();
			if(targetColony!=ourColony.getNr() && !ourColony.getServers().contains(s) && !s.belongsToColony(targetColony))
				return false;
			//If a neighbor of c is in colony k', then c must not be placed in a colony k'' different from both k' and our colony k
			if(!ourColony.getServers().contains(s)) {
				for(Connector conn : c.getConnectors()) {
					ISwNode otherSwNode=conn.getOtherVertex(c);
					IHwNode otherHwNode;
					if(otherSwNode.isEndDevice())
						otherHwNode=(EndDevice)otherSwNode;
					else
						otherHwNode=bookKeeper.getHost((Component)otherSwNode);
					if(otherHwNode==null)
						continue;
					if(!ourColony.getServers().contains(otherHwNode) && !ourColony.getEndDevices().contains(otherHwNode)) {
						for(Colony neiCol : ourColony.getNeighbors()) {
							if(neiCol.getServers().contains(otherHwNode) || neiCol.getEndDevices().contains(otherHwNode)) {
								if(!neiCol.getServers().contains(s))
									return false;
							}
						}
					}
				}
			}
		}
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
			Path p=findRoute(conn,s,otherHwNode,allHwNodes);
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

	/**
	 * Try to migrate the given component on the given server AND to re-route each connector that is 
	 * incident to the given component and goes to an already placed component or to an end device. 
	 * If successful, return true. Otherwise, undo the changes and return false.
	 * PRE: c is already placed.
	 */
	private boolean tryToMigrate(Component c,Server newServer,Colony ourColony,Set<IHwNode> allHwNodes,Conductor.ModeType mode) {
		int startSize=actionStack.getSize();
		Server oldServer=bookKeeper.getHost(c);
		actionStack.perform(new UnPlaceAction(c,oldServer));
		for(Connector conn : c.getConnectors()) {
			if(bookKeeper.getPath(conn)!=null)
				actionStack.perform(new UnRouteAction(conn,bookKeeper.getPath(conn)));
		}
		boolean success=tryToPlace(c,newServer,ourColony,allHwNodes,mode);
		if(!success)
			actionStack.rollback(startSize);
		return success;
	}

	/**
	 * Helper method to create the union of an arbitrary number of sets of the same type of objects in 
	 * the form of a single list.
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
			Colony ourColony,
			Conductor.ModeType mode) {
		long startTime=System.currentTimeMillis();
		//System.out.println("fullyControlledComponents: "+fullyControlledComponents.size());
		Map<Component,Server> oldAlpha=bookKeeper.getAlpha(); //we save it so that we can compute the number of migrations in the end
		Result result=new Result();
		List<Server> servers=union(freelyUsableServers,unpreferredServers);
		List<Component> movableComponents=union(fullyControlledComponents,obtainedComponents);
		List<Component> componentsToPlace=new ArrayList<>(newComponents);
		List<Component> allComponents=union(newComponents,fullyControlledComponents,obtainedComponents,readOnlyComponents);
		Set<EndDevice> endDevices=new HashSet<>();
		for(Component comp : allComponents) {
			for(Connector conn : comp.getConnectors()) {
				ISwNode other=conn.getOtherVertex(comp);
				if(other.isEndDevice())
					endDevices.add((EndDevice)other);
			}
		}
		Set<IHwNode> allHwNodes=new HashSet<>(servers);
		allHwNodes.addAll(endDevices);
		//compute for each new component its distance from the end devices in the application graph
		Map<Component,Integer> distanceFromEndDevices=new HashMap<>();
		int level=0;
		while(distanceFromEndDevices.size()<newComponents.size()) {
			Set<Component> nextLevelComps=new HashSet<>();
			for(Component c : newComponents) {
				if(distanceFromEndDevices.containsKey(c))
					continue;
				for(Connector conn : c.getConnectors()) {
					ISwNode otherSwNode=conn.getOtherVertex(c);
					if(otherSwNode instanceof EndDevice || distanceFromEndDevices.containsKey(otherSwNode)) {
						nextLevelComps.add(c);
						break;
					}
				}
			}
			level++;
			for(Component c : nextLevelComps)
				distanceFromEndDevices.put(c,level);
		}
		Collections.sort(componentsToPlace,new Comparator<Component>() { //we sort the components to be placed in decreasing order of their distance from end devices
			@Override
			public int compare(Component lhs,Component rhs) {
				return Integer.compare(distanceFromEndDevices.get(rhs),distanceFromEndDevices.get(lhs));
			}
		});
		//find the end devices connected to the application to place
		Set<EndDevice> importantEndDevices=new HashSet<>();
		for(Component comp : newComponents) {
			for(Connector conn : comp.getConnectors()) {
				ISwNode other=conn.getOtherVertex(comp);
				if(other.isEndDevice())
					importantEndDevices.add((EndDevice)other);
			}
		}
		if(importantEndDevices.isEmpty()) //it is important that this set is not empty
			importantEndDevices.addAll(endDevices);
		//compute for each server its distance from the end devices connected to the application to place
		Map<Server,Integer> distanceFromImportantEndDevices=new HashMap<>();
		for(Server s : servers) {
			for(EndDevice ed : importantEndDevices) {
				int dist=-1;
				for(Path p : bookKeeper.getInfra().getPaths(ed,s)) {
					if(p.getLinks().size()<dist || dist==-1)
						dist=p.getLinks().size();
				}
				if(!distanceFromImportantEndDevices.containsKey(s) || dist<distanceFromImportantEndDevices.get(s))
					distanceFromImportantEndDevices.put(s,dist);
			}
		}
		Collections.sort(servers,new Comparator<Server>() { //we sort the servers such that the best servers are at the beginning
			@Override
			public int compare(Server lhs,Server rhs) {
				if(freelyUsableServers.contains(lhs) && unpreferredServers.contains(rhs))
					return -1; // the freely usable servers are first, the unpreferred servers only after them
				if(freelyUsableServers.contains(rhs) && unpreferredServers.contains(lhs))
					return 1;
				return Integer.compare(distanceFromImportantEndDevices.get(lhs),distanceFromImportantEndDevices.get(rhs)); //within a category, servers that are closer to the relevant end devices are better
			}
		});
		//now the actual algorithm can start
		int beginning=actionStack.getSize();
		while(componentsToPlace.size()>0) {
			Component newComp=componentsToPlace.remove(componentsToPlace.size()-1); //we pick the component that is nearest to the end devices
			//try to place the component on one of the servers
			boolean succeeded=false;
			for(Server server : servers) {
				if(tryToPlace(newComp,server,ourColony,allHwNodes,mode)) {
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
						if(newServer!=oldServer && tryToMigrate(oldComp,newServer,ourColony,allHwNodes,mode)) {
							migrationTarget=newServer;
							break;
						}
					}
					if(migrationTarget!=null) { //if we managed to migrate this existing component
						if(tryToPlace(newComp,oldServer,ourColony,allHwNodes,mode)) { //if this way the relieved server can host the new component, then all is good
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
