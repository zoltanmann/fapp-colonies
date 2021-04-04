import java.util.HashSet;
import java.util.Set;

public class Main {
	private static Infrastructure infra;
	private static Orchestrator orch;

	private static void createInfra() {
		infra=new Infrastructure();
		Server s1=new Server("s1",1,1);
		Server s2=new Server("s2",2,2);
		Server s3=new Server("s3",3,3);
		Server s4=new Server("s4",4,4);
		EndDevice d1=new EndDevice("d1");
		infra.addServer(s1);
		infra.addServer(s2);
		infra.addServer(s3);
		infra.addServer(s4);
		infra.addEndDevice(d1);
		new Link("s1-s2", 10, 10, s1, s2);
		new Link("s2-s4", 10, 10, s2, s4);
		new Link("s3-s1", 10, 10, s3, s1);
		new Link("s3-s4", 10, 10, s3, s4);
		new Link("s1-d1", 10, 10, s1, d1);
		infra.determinePaths(2);
	}

	public static void main(String[] args) {
		createInfra();
		infra.print();
		orch=new Orchestrator(infra);
		Set<Component> app=new HashSet<>();
		Component c1=new Component("c1",1,1);
		Component c2=new Component("c2",2,1);
		app.add(c1);
		app.add(c2);
		new Connector("c1-c2", 1, 10, c1, c2);
		new Connector("c1-d1", 1, 10, c1, (EndDevice)infra.getEndDevices().toArray()[0]);
		orch.addApplication(app);
		orch.print();
		app=new HashSet<>();
		app.add(new Component("c3",1,1));
		app.add(new Component("c4",2,1));
		orch.addApplication(app);
		orch.print();
	}
}
