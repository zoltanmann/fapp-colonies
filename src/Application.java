import java.util.ArrayList;
import java.util.List;

public class Application {
	private List<Component> components;

	public Application() {
		components=new ArrayList<>();
	}

	public void addComponent(Component c) {
		components.add(c);
	}

	public int getSize() {
		return components.size();
	}

	public Component getComponent(int i) {
		return components.get(i);
	}

	public Component getRandomComponent() {
		return components.get(Main.random.nextInt(components.size()));
	}
}
