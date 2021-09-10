import java.util.ArrayList;
import java.util.List;

/**
 * Represents an application, storing the list of components that the application
 * consists of. The application does not know where it should be or is already
 * deployed.
 */
public class Application {
	/** The components of the application */
	private List<Component> components;

	/**
	 * Create application with 0 components.
	 */
	public Application() {
		components=new ArrayList<>();
	}

	/**
	 * Add a further component to the application.
	 */
	public void addComponent(Component c) {
		components.add(c);
	}

	/**
	 * Get the number of components.
	 */
	public int getSize() {
		return components.size();
	}

	/**
	 * Get the i-th components in the list of components. PRE: i is at least 0 and
	 * less than the number of components.
	 */
	public Component getComponent(int i) {
		return components.get(i);
	}

	/**
	 * Get a randomly chosen component. PRE: there is at least one component, and
	 * Main.random is already initialized.
	 */
	public Component getRandomComponent() {
		return components.get(Main.random.nextInt(components.size()));
	}

	/**
	 * Get the list of components.
	 */
	public List<Component> getComponents() {
		return components;
	}
}
