import java.io.FileWriter;
import java.io.IOException;
import java.util.Random;
import java.util.Set;

/**
 * Experiment driver class. Everything in this class is static, so that it does not
 * need to be instantiated, but can be used directly from the main() method.
 */
public class Main {
	enum SolverType {SolverSB, SolverILP}

	/** Nr. of fog nodes per region */
	private static int nrFogNodesPerRegion=5;
	/** Nr. of regions */
	private static int nrRegions=5;
	/** Nr. of applications per region */
	private static int nrAppsPerRegion=5;
	/** Nr. of fog components per application */
	private static int appSize=5;
	/** Nr. of end devices per region */
	private static int nrEndDevicesPerRegion=5;
	/** Nr. of additional links among servers after creating an initial tree architecture. This number of links is tried to be created; the actual number of created links may be less */
	private static int nrAdditionalLinks=nrFogNodesPerRegion*2;
	/** Nr. of servers connected to an end device */
	private static int nrNeighborsOfEndDevice=2;
	/** Nr. of fog nodes per colony that will be shared with each neighboring colony  */
	private static int nrNodesToShareWithNeighbor=1;
	/** Random generator that can be used by any class in the program */
	public static Random random;
	/** The cloud, which is contained in each colony */
	private static Server cloud;
	/** The complete infrastructure */
	private static Infrastructure infra;
	/** Set of all fog colonies */
	private static Colony colonies[];
	/** To accelerate experiments, the centralized approach can be switched off with this flag */
	private static boolean skipModel1=false;

	/**
	 * Creates the infrastructure, including the colonies, and the path information.
	 */
	private static void createInfra() {
		infra=new Infrastructure();
		cloud=new Server("cloud", 1000000, 1000000);
		infra.addServer(cloud);
		colonies=new Colony[nrRegions];
		for(int i=0;i<nrRegions;i++) {
			colonies[i]=new Colony();
			createRegion(i);
		}
		for(int i=0;i<nrRegions;i++) {
			int left=(i>0)?i-1:nrRegions-1;
			int right=(i<nrRegions-1)?i+1:0;
			connectRegions(colonies[i],colonies[left]);
			connectRegions(colonies[i],colonies[right]);
		}
		infra.pruneParallelLinks();
		infra.determinePaths(2);
	}

	/**
	 * Creates the infrastructure in the fog colony (including end devices and the
	 * cloud) with the given index.
	 */
	private static void createRegion(int index) {
		for(int i=0;i<nrFogNodesPerRegion;i++) {
			String serverId="s"+index+"."+i;
			double cpuCap=random.nextDouble()*9+1;
			double ramCap=random.nextDouble()*9+1;
			Server s=new Server(serverId,cpuCap,ramCap);
			if(i>0) {
				Server s0=colonies[index].getRandomServer();
				double bw=random.nextDouble()*4+1;
				double latency=random.nextDouble()*4+1;
				new Link(bw,latency,s0,s);
			}
			infra.addServer(s);
			colonies[index].addServer(s);
		}
		for(int i=0;i<nrAdditionalLinks;i++) {
			Server s1=colonies[index].getRandomServer();
			Server s2=colonies[index].getRandomServer();
			if(s1==s2)
				continue;
			double bw=random.nextDouble()*4+1;
			double latency=random.nextDouble()*4+1;
			new Link(bw,latency,s1,s2);
		}
		for(int i=0;i<nrEndDevicesPerRegion;i++) {
			EndDevice d=new EndDevice("d"+index+"."+i);
			for(int j=0;j<nrNeighborsOfEndDevice;j++) { ///
				Server s=colonies[index].getRandomServer();
				double bw=random.nextDouble()*5+5;
				double latency=random.nextDouble()*3;
				new Link(bw,latency,s,d);
			}
			infra.addEndDevice(d);
			colonies[index].addEndDevice(d);
		}
		Server s=colonies[index].getRandomServer();
		double bw=random.nextDouble()*4+1;
		double latency=random.nextDouble()*40+40;///
		new Link(bw,latency,s,cloud);
		colonies[index].addServer(cloud);
	}

	/**
	 * Creates a link between a random fog node in region1 and a random fog node in
	 * region2.
	 */
	private static void connectRegions(Colony region1, Colony region2) {
		region1.addNeighbor(region2);
		region2.addNeighbor(region1);
		Server s1,s2;
		do {
			s1=region1.getRandomServer();
			s2=region2.getRandomServer();
		} while(s1==s2 || s1==cloud || s2==cloud);
		double bw=random.nextDouble()*4+1;
		double latency=random.nextDouble()*4+1;
		new Link(bw,latency,s1,s2);
	}

	/**
	 * Create an application targeted to the given fog colony. The idPrefix should
	 * be unique among the applications, e.g., c1.2 for application 2 in colony 1.
	 * This prefix is used to create unique IDs for the components. The application
	 * also includes a connection to a random end device of the fog colony.
	 */
	private static Application createApp(Colony region, String idPrefix) {
		Application app=new Application();
		for(int i=0;i<appSize;i++) {
			double cpuReq=random.nextDouble()*5;
			double ramReq=random.nextDouble()*5;
			Component comp=new Component(idPrefix+i, cpuReq, ramReq, region);
			if(i>0) {
				double bwReq=random.nextDouble()*3;
				double maxLatency=random.nextDouble()*30+30;///
				new Connector(bwReq, maxLatency, comp, app.getRandomComponent());
			}
			app.addComponent(comp);
		}
		/* ///
		for(int i=0;i<appSize;i++) {
			while(app.getComponent(i).getConnectors().size()<compGrade) {
				int i0=random.nextInt(appSize);
				if(i0!=i) {
					double bwReq=random.nextDouble()*3;
					double maxLatency=random.nextDouble()*20+20;///
					new Connector(bwReq, maxLatency, app.getComponent(i), app.getComponent(i0));
				}
			}
		}
		*/
		Component c=app.getRandomComponent();
		EndDevice d=region.getRandomEndDevice();
		double bwReq=random.nextDouble()*2;
		double maxLatency=random.nextDouble()*20+20;///
		new Connector(bwReq, maxLatency, c, d);
		return app;
	}

	/**
	 * Create all applications.
	 */
	private static void createApps() {
		for(int j=0;j<nrAppsPerRegion;j++) {
			for(int i=0;i<nrRegions;i++) {
				Application app=createApp(colonies[i], "c"+i+"."+j+".");
				colonies[i].addApplication(app);
			}
		}
	}

	/**
	 * Perform the experiments.
	 */
	private static void doExperiment() throws IOException {
		//create Conductors, together with the corresponding BookKeepers and Solvers
		Map2d<Conductor.ModeType,SolverType,Conductor> conductors=new Map2d<>();
		for(Conductor.ModeType modeType : Conductor.ModeType.values()) {
			for(SolverType solverType : SolverType.values()) {
				BookKeeper bookKeeper=new BookKeeper(infra);
				ISolver solver=null;
				if(solverType==SolverType.SolverILP)
					solver=new SolverILP(bookKeeper);
				if(solverType==SolverType.SolverSB)
					solver=new SolverSB(bookKeeper);
				Conductor conductor=new Conductor(bookKeeper,solver,modeType);
				conductors.put(modeType,solverType,conductor);
			}
		}
		//create overlapping colonies
		Colony[] bigColonies=new Colony[nrRegions];
		for(int i=0;i<nrRegions;i++) {
			bigColonies[i]=colonies[i].clone();
			bigColonies[i].removeNeighbors();
		}
		for(int i=0;i<nrRegions;i++) {
			for(int j=0;j<nrRegions;j++) {
				if(colonies[i].isAdjacentTo(colonies[j])) {
					bigColonies[i].addNeighbor(bigColonies[j]);
					Set<Server> shared=bigColonies[i].shareNodes(nrNodesToShareWithNeighbor);
					for(Server s : shared)
						bigColonies[j].addServer(s);
				}
			}
		}
		//initialize file output
		FileWriter fileWriter=new FileWriter("results_detail.csv");
		fileWriter.write("App;NrRegions");
		for(Conductor.ModeType mode : Conductor.ModeType.values()) {
			for(SolverType solver : SolverType.values()) {
				String postfix="-"+mode+"-"+solver;
				fileWriter.write(";Success"+postfix+";TimeMs"+postfix+";Migrations"+postfix);
			}
		}
		fileWriter.write("\n");
		//initialize grandTotalResults
		Map2d<Conductor.ModeType,SolverType,Result> grandTotalResults=new Map2d<>();
		for(Conductor.ModeType mode : Conductor.ModeType.values()) {
			for(SolverType solver : SolverType.values()) {
				grandTotalResults.put(mode,solver,new Result());
			}
		}
		//main experiment cycle
		for(int j=0;j<nrAppsPerRegion;j++) {
			//initialize totalResults
			Map2d<Conductor.ModeType,SolverType,Result> totalResults=new Map2d<>();
			for(Conductor.ModeType mode : Conductor.ModeType.values()) {
				for(SolverType solver : SolverType.values()) {
					totalResults.put(mode,solver,new Result());
				}
			}
			//add next application in each region
			for(int i=0;i<nrRegions;i++) {
				Application app=colonies[i].getApplication(j);
				for(Conductor.ModeType mode : Conductor.ModeType.values()) {
					for(SolverType solver : SolverType.values()) {
						System.out.println("app "+j+", region "+i+", model "+mode+", solver "+solver);
						if(skipModel1 && mode==Conductor.ModeType.centralized)
							continue;
						Conductor conductor=conductors.get(mode,solver);
						Colony colony=colonies[i];
						if(mode==Conductor.ModeType.overlapping)
							colony=bigColonies[i];
						Result result=conductor.deployApplication(colony,app);
						totalResults.get(mode,solver).increaseBy(result);
					}
				}
			}
			//write result to file
			fileWriter.write(String.format("%d;%d",j,nrRegions));
			for(Conductor.ModeType mode : Conductor.ModeType.values()) {
				for(SolverType solver : SolverType.values()) {
					fileWriter.write(";"+totalResults.get(mode,solver).toString());
					fileWriter.flush();
					grandTotalResults.get(mode,solver).increaseBy(totalResults.get(mode,solver));
				}
			}
			fileWriter.write("\n");
			fileWriter.flush();
		}
		fileWriter.close();
		//write aggregated results to the other file
		fileWriter=new FileWriter("results_total.csv");
		fileWriter.write("Model;Solver;Success;TimeMs;Migrations\n");
		for(Conductor.ModeType mode : Conductor.ModeType.values()) {
			for(SolverType solver : SolverType.values()) {
				fileWriter.write(""+mode+";"+solver+";"+grandTotalResults.get(mode,solver).toString()+"\n");
			}
		}
		fileWriter.close();
	}

	/**
	 * Main method.
	 */
	public static void main(String[] args) throws IOException {
		random=new Random();
		createInfra();
		createApps();
		doExperiment();
	}
}
