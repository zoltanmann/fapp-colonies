import java.io.FileWriter;
import java.io.IOException;
import java.util.Random;
import java.util.Set;

public class Main {
	private static int nrFogNodesPerRegion=7;///
	private static int nrEndDevicesPerRegion=5;///
	private static int nrAppsPerRegion=4;///
	private static int nrRegions=7;///
	private static int appSize=4;///
	//private static int compGrade=2;
	private static int nrAdditionalLinks=10;///
	private static int nrNeighborsOfEndDevice=2;
	private static int nrNodesToShareWithNeighbor=2;
	public static Random random;
	private static Server cloud;
	private static Infrastructure infra;
	private static Colony colonies[];
	private static boolean skipModel1=false;

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

	private static void createRegion(int index) {
		for(int i=0;i<nrFogNodesPerRegion;i++) {
			String serverId="s"+index+"."+i;
			double cpuCap=random.nextDouble()*9+1;
			double ramCap=random.nextDouble()*9+1;
			Server s=new Server(serverId,cpuCap,ramCap);
			if(i>0) {
				Server s0=colonies[index].getRandomFogNode();
				double bw=random.nextDouble()*4+1;
				double latency=random.nextDouble()*4+1;
				new Link(bw,latency,s0,s);
			}
			infra.addServer(s);
			colonies[index].addFogNode(s);
		}
		for(int i=0;i<nrAdditionalLinks;i++) {
			Server s1=colonies[index].getRandomFogNode();
			Server s2=colonies[index].getRandomFogNode();
			if(s1==s2)
				continue;
			double bw=random.nextDouble()*4+1;
			double latency=random.nextDouble()*4+1;
			new Link(bw,latency,s1,s2);
		}
		for(int i=0;i<nrEndDevicesPerRegion;i++) {
			EndDevice d=new EndDevice("d"+index+"."+i);
			for(int j=0;j<nrNeighborsOfEndDevice;j++) { ///
				Server s=colonies[index].getRandomFogNode();
				double bw=random.nextDouble()*5+5;
				double latency=random.nextDouble()*3;
				new Link(bw,latency,s,d);
			}
			infra.addEndDevice(d);
			colonies[index].addEndDevice(d);
		}
		Server s=colonies[index].getRandomFogNode();
		double bw=random.nextDouble()*4+1;
		double latency=random.nextDouble()*40+40;///
		new Link(bw,latency,s,cloud);
		colonies[index].addFogNode(cloud);
	}

	private static void connectRegions(Colony region1, Colony region2) {
		region1.addNeighbor(region2);
		region2.addNeighbor(region1);
		Server s1,s2;
		do {
			s1=region1.getRandomFogNode();
			s2=region2.getRandomFogNode();
		} while(s1==s2 || s1==cloud || s2==cloud);
		double bw=random.nextDouble()*4+1;
		double latency=random.nextDouble()*4+1;
		new Link(bw,latency,s1,s2);
	}

	private static Application createApp(Colony region, String idPrefix) {
		Application app=new Application();
		for(int i=0;i<appSize;i++) {
			double cpuReq=random.nextDouble()*5;
			double ramReq=random.nextDouble()*5;
			Component comp=new Component(idPrefix+i, cpuReq, ramReq);
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

	private static void createApps() {
		for(int j=0;j<nrAppsPerRegion;j++) {
			for(int i=0;i<nrRegions;i++) {
				Application app=createApp(colonies[i], "c"+i+"."+j+".");
				colonies[i].addApplication(app);
			}
		}
	}

	private static void doExperiment() throws IOException {
		int nrModels=4;
		Orchestrator orchestrators[][]=new Orchestrator[nrModels][nrRegions];
		Orchestrator model1Orchestrator=new Orchestrator(infra,1);
		for(int i=0;i<nrRegions;i++) {
			orchestrators[0][i]=model1Orchestrator;
			Infrastructure subInfra=infra.getSubInfra(colonies[i], cloud, false);
			orchestrators[1][i]=new Orchestrator(subInfra,2);
			subInfra=infra.getSubInfra(colonies[i], cloud, true);
			orchestrators[2][i]=new Orchestrator(subInfra,3);
			orchestrators[2][i].setColony(colonies[i]);
		}
		for(int i=0;i<nrRegions;i++) {
			for(int j=i+1;j<nrRegions;j++) {
				if(colonies[i].isAdjacentTo(colonies[j])) {
					orchestrators[2][i].addNeighbor(orchestrators[2][j]);
					orchestrators[2][j].addNeighbor(orchestrators[2][i]);
				}
			}
		}
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
						bigColonies[j].addFogNode(s);
				}
			}
		}
		for(int i=0;i<nrRegions;i++) {
			Infrastructure subInfra=infra.getSubInfra(bigColonies[i], cloud, false);
			orchestrators[3][i]=new Orchestrator(subInfra,4);
			orchestrators[3][i].setColony(bigColonies[i]);
		}
		for(int i=0;i<nrRegions;i++) {
			for(int j=i+1;j<nrRegions;j++) {
				if(bigColonies[i].isAdjacentTo(bigColonies[j])) {
					orchestrators[3][i].addNeighbor(orchestrators[3][j]);
					orchestrators[3][j].addNeighbor(orchestrators[3][i]);
				}
			}
		}
		FileWriter fileWriters[]=new FileWriter[nrModels];
		for(int k=0;k<nrModels;k++) {
			fileWriters[k]=new FileWriter("results_model"+(k+1)+".csv");
			fileWriters[k].write("App;NrRegions;Success;TimeMs;Migrations\n");
		}
		Result grandTotalResults[]=new Result[nrModels];
		for(int k=0;k<nrModels;k++)
			grandTotalResults[k]=new Result();
		for(int j=0;j<nrAppsPerRegion;j++) {
			Result totalResults[]=new Result[nrModels];
			for(int k=0;k<nrModels;k++)
				totalResults[k]=new Result();
			for(int i=0;i<nrRegions;i++) {
				Application app=colonies[i].getApplication(j);
				for(int k=0;k<nrModels;k++) {
					System.out.println("app "+j+", region "+i+", model "+(k+1));
					if(skipModel1 && k==0)
						continue;
					Result result=orchestrators[k][i].addApplication(app);
					totalResults[k].increaseBy(result);
				}
			}
			for(int k=0;k<nrModels;k++) {
				fileWriters[k].write(String.format("%d;%d;%s\n",j,nrRegions,totalResults[k].toString()));
				fileWriters[k].flush();
				grandTotalResults[k].increaseBy(totalResults[k]);
			}
		}
		for(int k=0;k<nrModels;k++)
			fileWriters[k].close();
		FileWriter fileWriter=new FileWriter("results_total.csv");
		fileWriter.write("Model;Success;TimeMs;Migrations\n");
		for(int k=0;k<nrModels;k++)
			fileWriter.write(""+(k+1)+";"+grandTotalResults[k].toString()+"\n");
		fileWriter.close();
	}

	public static void main(String[] args) throws IOException {
		random=new Random();
		createInfra();
		createApps();
		doExperiment();
	}
}
