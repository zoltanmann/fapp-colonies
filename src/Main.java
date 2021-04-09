import java.io.FileWriter;
import java.io.IOException;
import java.util.Random;

public class Main {
	private static int nrFogNodesPerRegion=6;///
	private static int nrEndDevicesPerRegion=5;///
	private static int nrAppsPerRegion=4;///
	private static int nrRegions=5;///
	private static int appSize=3;///
	private static int compGrade=1;
	private static int nrAdditionalLinks=10;///
	private static int nrNeighborsOfEndDevice=3;
	public static Random random;
	private static Server cloud;
	private static Infrastructure infra;
	private static Colony colonies[];

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
		double latency=random.nextDouble()*50+50;
		new Link(bw,latency,s,cloud);
	}

	private static void connectRegions(Colony region1, Colony region2) {
		region1.addNeighbor(region2);
		region2.addNeighbor(region1);
		Server s1,s2;
		do {
			s1=region1.getRandomFogNode();
			s2=region2.getRandomFogNode();
		} while(s1==s2);
		double bw=random.nextDouble()*4+1;
		double latency=random.nextDouble()*4+1;
		new Link(bw,latency,s1,s2);
	}

	private static Application createApp(Colony region, String idPrefix) {
		Application app=new Application();
		for(int i=0;i<appSize;i++) {
			double cpuReq=random.nextDouble()*5;
			double ramReq=random.nextDouble()*5;
			app.addComponent(new Component(idPrefix+i, cpuReq, ramReq));
		}
		for(int i=0;i<appSize;i++) {
			while(app.getComponent(i).getConnectors().size()<compGrade) {
				int i0=random.nextInt(appSize);
				if(i0!=i) {
					double bwReq=random.nextDouble()*3;
					double maxLatency=random.nextDouble()*90+10;
					new Connector(bwReq, maxLatency, app.getComponent(i), app.getComponent(i0));
				}
			}
		}
		Component c=app.getRandomComponent();
		EndDevice d=region.getRandomEndDevice();
		double bwReq=random.nextDouble()*2;
		double maxLatency=random.nextDouble()*40+40;
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
		int nrModels=2;
		Orchestrator orchestrators[][]=new Orchestrator[nrModels][nrRegions];
		for(int i=0;i<nrRegions;i++) {
			orchestrators[0][i]=new Orchestrator(infra);
			Infrastructure subInfra=infra.getSubInfra(colonies[i], cloud);
			orchestrators[1][i]=new Orchestrator(subInfra);
		}
		FileWriter fileWriters[]=new FileWriter[nrModels];
		for(int k=0;k<nrModels;k++) {
			fileWriters[k]=new FileWriter("results_model"+(k+1)+".csv");
			fileWriters[k].write("App;NrRegions;Success;TimeMs;Migrations\n");
		}
		for(int j=0;j<nrAppsPerRegion;j++) {
			Result totalResults[]=new Result[nrModels];
			for(int k=0;k<nrModels;k++)
				totalResults[k]=new Result();
			for(int i=0;i<nrRegions;i++) {
				Application app=colonies[i].getApplication(j);
				for(int k=0;k<nrModels;k++) {
					System.out.println("app "+j+", region "+i+", model "+(k+1));
					Result result=orchestrators[k][i].addApplication(app);
					totalResults[k].increaseBy(result);
				}
			}
			for(int k=0;k<nrModels;k++) {
				fileWriters[k].write(String.format("%d;%d;%s\n",j,nrRegions,totalResults[k].toString()));
				fileWriters[k].flush();
			}
		}
		for(int k=0;k<nrModels;k++)
			fileWriters[k].close();
	}

	public static void main(String[] args) throws IOException {
		random=new Random();
		createInfra();
		createApps();
		doExperiment();
	}
}
