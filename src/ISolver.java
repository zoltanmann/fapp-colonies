import java.util.Set;

public interface ISolver {

	/**
	 * Perform an optimization run, trying to place the new components.
	 */
	public Result optimize(
			Set<Server> freelyUsableServers, //servers that should be preferred for placement
			Set<Server> unpreferredServers, //additional servers that can be used for placement if necessary
			Set<Component> newComponents, //newly submitted components that are not placed yet
			Set<Component> fullyControlledComponents, //already placed components whose placement is in our control
			Set<Component> obtainedComponents, //already placed components that we got from another colony and hence must not forward it to a third colony
			Set<Component> readOnlyComponents, //already placed components in a neighboring colony that have a connector to a component in our colony
			Colony ourColony,
			Conductor.ModeType mode);

}
