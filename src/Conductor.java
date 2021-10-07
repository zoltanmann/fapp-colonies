import java.util.HashSet;
import java.util.Set;

/**
 * Conducts a sequence of experiments.
 */
public class Conductor {
	/** Supported approaches for decentralization and coordination */
	enum ModeType {centralized,independent,communicating,overlapping}
	/** 1:1 relation to a BookKeeper */
	private BookKeeper bookKeeper;
	/** The decentralization and coordination model used in the experiments */
	private ModeType mode;
	/** 1:1 relation to a Solver */
	private SolverSB solver;

	/**
	 * Construct the Conductor and create the linked Solver as well.
	 */
	public Conductor(BookKeeper bookKeeper,ModeType mode) {
		this.bookKeeper=bookKeeper;
		this.mode=mode;
		solver=new SolverSB(bookKeeper);
	}

	/**
	 * Deploy an application to the given colony.
	 */
	public Result deployApplication(Colony colony,Application app) {
		Set<Server> freelyUsableServers=null; //servers that should be preferred for placement
		Set<Server> unpreferredServers=null; //additional servers that can be used for placement if necessary
		Set<Component> newComponents=new HashSet<>(app.getComponents()); //newly submitted components that are not placed yet
		Set<Component> fullyControlledComponents=null; //already placed components whose placement is in our control
		Set<Component> obtainedComponents=null; //already placed components that we got from another colony and hence must not forward it to a third colony
		Set<Component> readOnlyComponents=null; //already placed components in a neighboring colony that have a connector to a component in our colony
		switch(mode) {
		case centralized:
			freelyUsableServers=bookKeeper.getInfra().getServers();
			unpreferredServers=new HashSet<>();
			fullyControlledComponents=bookKeeper.getComponents();
			obtainedComponents=new HashSet<>();
			readOnlyComponents=new HashSet<>();
			break;
		case independent:
			freelyUsableServers=new HashSet<>(colony.getServers());
			unpreferredServers=new HashSet<>();
			fullyControlledComponents=bookKeeper.getComponents(colony);
			obtainedComponents=new HashSet<>();
			readOnlyComponents=new HashSet<>();
			break;
		case communicating:
			freelyUsableServers=new HashSet<>(colony.getServers());
			unpreferredServers=new HashSet<>();
			for(Colony colony2 : colony.getNeighbors())
				unpreferredServers.addAll(colony2.getServers());
			fullyControlledComponents=bookKeeper.getComponents(colony);
			obtainedComponents=new HashSet<>();
			readOnlyComponents=new HashSet<>();
			for(Component c : fullyControlledComponents) {
				if(c.getTargetColony()!=colony) {
					fullyControlledComponents.remove(c);
					obtainedComponents.add(c);
				}
			}
			for(Colony neiCol : colony.getNeighbors()) {
				for(Component c : bookKeeper.getComponents(neiCol)) {
					boolean relevant=false;
					for(Connector conn : c.getConnectors()) {
						ISwNode otherVertex=conn.getOtherVertex(c);
						if(otherVertex instanceof Component) {
							Component c2=(Component)otherVertex;
							if(fullyControlledComponents.contains(c2) || obtainedComponents.contains(c2)) {
								relevant=true;
								break;
							}
						}
					}
					if(relevant)
						readOnlyComponents.add(c);
				}
			}
			break;
		case overlapping:
			freelyUsableServers=new HashSet<>(colony.getServers());
			freelyUsableServers.removeAll(colony.getSharedNodes());
			unpreferredServers=colony.getSharedNodes();
			fullyControlledComponents=bookKeeper.getComponents(colony);
			for(Component c : fullyControlledComponents) {
				if(c.getTargetColony()!=colony)
					fullyControlledComponents.remove(c);
			}
			obtainedComponents=new HashSet<>();
			readOnlyComponents=new HashSet<>();
			break;
		}
		return solver.optimize(freelyUsableServers,unpreferredServers,newComponents,fullyControlledComponents,obtainedComponents,readOnlyComponents,colony);
	}
}
