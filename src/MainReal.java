import java.io.FileWriter;
import java.io.IOException;
import java.util.Random;
import java.util.Set;

/**
 * Experiment driver class based on real-world data from Djemai et al.: "A Discrete Particle Swarm 
 * Optimization approach for Energy-efficient IoT services placement over Fog infrastructures".
 * Everything in this class is static, so that it does not need to be instantiated, but can be used 
 * directly from the main() method.
 */
public class MainReal {
	enum SolverType {SolverSB, SolverILP}

	/** Nr. of fog nodes per region */
	private static int nrL2FogNodesPerRegion=4;
	/** Nr. of regions */
	private static int nrRegions=5;
	/** Nr. of applications per region */
	private static int nrAppsPerRegion=5;
	/** Nr. of fog components per application */
	private static int appSize=3;
	/** Nr. of end devices per fog node */
	private static int nrEndDevicesPerFogNode=4;
	private static double latencyBetweenColonies=100;
	private static double bwBetweenColonies=100;
	private static double compCpuMin=100;
	private static double compCpuMax=500;
	private static double compRamMin=100;
	private static double compRamMax=500;
	private static double connLatMin=25;
	private static double connLatMax=50;
	private static double connBwMin=0.01;
	private static double connBwMax=0.6;
	/** Nr. of fog nodes per colony that will be shared with each neighboring colony  */
	private static int nrNodesToShareWithNeighbor=1;
	/** Random generator that can be used by any class in the program */
	public static Random random;
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
		Server cloud=new Server("cloud"+index, 120000, 64000, true);
		infra.addServer(cloud);
		colonies[index].addServer(cloud);
		Server proxyServer=new Server("proxy"+index, 60000, 8000, false);
		infra.addServer(proxyServer);
		colonies[index].addServer(proxyServer);
		new Link(10000,100,cloud,proxyServer);
		int endDeviceIndex=0;
		for(int i=0;i<nrL2FogNodesPerRegion;i++) {
			String serverId="s"+index+"."+i;
			double cpuCap,ramCap;
			int nodeType=i%4;
			if(nodeType<2) {
				cpuCap=6750;
				ramCap=1000;
			} else {
				cpuCap=13500;
				ramCap=2000;
			}
			Server s=new Server(serverId,cpuCap,ramCap,false);
			infra.addServer(s);
			colonies[index].addServer(s);
			new Link(10000,2,s,proxyServer);
			for(int j=0;j<nrEndDevicesPerFogNode;j++) {
				EndDevice d=new EndDevice("d"+index+"."+endDeviceIndex);
				endDeviceIndex++;
				infra.addEndDevice(d);
				colonies[index].addEndDevice(d);
				double bw,latency;
				if(i%4==0) latency=1000;
				else if(i%4==1) latency=20;
				else if(i%4==2) latency=50;
				else latency=12;
				if(j%4==0) bw=0.25;
				else if(j%4==1) bw=1;
				else if(j%4==2) bw=1000;
				else bw=1000;
				new Link(bw,latency,d,s);
			}
		}
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
		} while(s1==s2 || s1.isCloud() || s2.isCloud());
		new Link(bwBetweenColonies,latencyBetweenColonies,s1,s2);
	}

	private static void createConnector(ISwNode v1,ISwNode v2) {
		double bwReq=connBwMin+random.nextDouble()*(connBwMax-connBwMin);
		double maxLatency=connLatMin+random.nextDouble()*(connLatMax-connLatMin);
		new Connector(bwReq,maxLatency,v1,v2);
	}

	/**
	 * Create an application targeted to the given fog colony. The idPrefix should
	 * be unique among the applications, e.g., "c1.2." for application 2 in colony 1.
	 * This prefix is used to create unique IDs for the components. The application
	 * also includes a connection to a random end device of the fog colony.
	 */
	private static Application createApp(Colony region, String idPrefix) {
		Application app=new Application();
		boolean masterWorkersApp=random.nextBoolean();
		for(int i=0;i<appSize;i++) {
			double cpuReq=compCpuMin+random.nextDouble()*(compCpuMax-compCpuMin);
			double ramReq=compRamMin+random.nextDouble()*(compRamMax-compRamMin);
			Component comp=new Component(idPrefix+i,cpuReq,ramReq,region);
			if(i>0) {
				Component parent;
				if(masterWorkersApp) //master-workers application
					parent=app.getComponent(0);
				else //sequential unidirectional dataflow application
					parent=app.getComponent(i-1);
				createConnector(comp,parent);
			}
			app.addComponent(comp);
		}
		if(!masterWorkersApp)
			createConnector(app.getComponent(0),app.getComponent(appSize-1));
		Component c=app.getComponent(0);
		EndDevice d=region.getRandomEndDevice();
		createConnector(c,d);
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
