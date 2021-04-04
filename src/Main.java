
public class Main {
	private static Infrastructure infra;
	private static Orchestrator orch;

	private static void createInfra() {
		infra=new Infrastructure();
		Server s1=new Server("s1",1,1);
		Server s2=new Server("s2",2,2);
		Server s3=new Server("s3",3,3);
		Server s4=new Server("s4",4,4);
		infra.addNode(s1);
		infra.addNode(s2);
		infra.addNode(s3);
		infra.addNode(s4);
		new Link("s1-s2", 10, 10, s1, s2);
		new Link("s2-s4", 10, 10, s2, s4);
		new Link("s3-s1", 10, 10, s3, s1);
		new Link("s3-s4", 10, 10, s3, s4);
		infra.determinePaths(2);
	}

	public static void main(String[] args) {
		createInfra();
		infra.print();
		orch=new Orchestrator(infra, null, null);
	}
}
