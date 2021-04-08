import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class Main {
	private static int nrFogNodesPerRegion=15;///
	private static int nrEndDevicesPerRegion=10;
	private static int nrAppsPerRegion=10;
	private static int nrRegions=15;///
	private static int appSize=5;
	private static Random random;
	private static Server cloud;

	private static Infrastructure createRegion(int index) {
		Infrastructure infra=new Infrastructure();
		for(int i=0;i<nrFogNodesPerRegion;i++) {
			String serverId="s"+index+"."+i;
			double cpuCap=random.nextDouble()*9+1;
			double ramCap=random.nextDouble()*9+1;
			Server s=new Server(serverId,cpuCap,ramCap);
			if(infra.getServers().size()>0) {
				Server s0=new ArrayList<Server>(infra.getServers()).get(random.nextInt(infra.getServers().size()));
				double bw=random.nextDouble()*4+1;
				double latency=random.nextDouble()*4+1;
				new Link(bw,latency,s0,s);
			}
			infra.addServer(s);
		}
		List<Server> serverList=new ArrayList<Server>(infra.getServers());
		for(int i=0;i<50;i++) {
			Server s1=serverList.get(random.nextInt(serverList.size()));
			Server s2=serverList.get(random.nextInt(serverList.size()));
			double bw=random.nextDouble()*4+1;
			double latency=random.nextDouble()*4+1;
			new Link(bw,latency,s1,s2);
		}
		Server s=serverList.get(random.nextInt(serverList.size()));
		double bw=random.nextDouble()*4+1;
		double latency=random.nextDouble()*50+50;
		new Link(bw,latency,s,cloud);
		for(int i=0;i<nrEndDevicesPerRegion;i++) {
			EndDevice d=new EndDevice("d"+index+"."+i);
			s=serverList.get(random.nextInt(serverList.size()));
			bw=random.nextDouble()*5+5;
			latency=random.nextDouble()*3;
			new Link(bw,latency,s,d);
			infra.addEndDevice(d);
		}
		return infra;
	}

	private static void connectRegions(Infrastructure region1, Infrastructure region2) {
		List<Server> serverList1=new ArrayList<Server>(region1.getServers());
		List<Server> serverList2=new ArrayList<Server>(region2.getServers());
		Server s1=serverList1.get(random.nextInt(serverList1.size()));
		Server s2=serverList2.get(random.nextInt(serverList2.size()));
		double bw=random.nextDouble()*4+1;
		double latency=random.nextDouble()*4+1;
		new Link(bw,latency,s1,s2);
	}

	private static Infrastructure[] createInfra() {
		cloud=new Server("cloud", 1000000, 1000000);
		Infrastructure[] infra=new Infrastructure[nrRegions];
		for(int i=0;i<nrRegions;i++) {
			infra[i]=createRegion(i);
		}
		for(int i=0;i<nrRegions;i++) {
			int i1=random.nextInt(nrRegions);
			int i2=random.nextInt(nrRegions);
			if(i1!=i)
				connectRegions(infra[i], infra[i1]);
			if(i2!=i)
				connectRegions(infra[i], infra[i2]);
		}
		for(int i=0;i<nrRegions;i++) {
			infra[i].addServer(cloud);
		}
		for(int i=0;i<nrRegions;i++) {
			infra[i].determinePaths(2);
		}
		return infra;
	}

	/*
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
		new Link(10, 10, s1, s2);
		new Link(10, 10, s2, s4);
		new Link(10, 10, s3, s1);
		new Link(10, 10, s3, s4);
		new Link(10, 10, s1, d1);
		infra.determinePaths(2);
	}
	*/

	private static Set<Component> createApp(Infrastructure region, String idPrefix) {
		Component[] app=new Component[appSize];
		for(int i=0;i<appSize;i++) {
			double cpuReq=random.nextDouble()*5;
			double ramReq=random.nextDouble()*5;
			app[i]=new Component(idPrefix+i, cpuReq, ramReq);
		}
		for(int i=0;i<appSize;i++) {
			while(app[i].getConnectors().size()<2) {
				int i0=random.nextInt(appSize);
				if(i0!=i) {
					double bwReq=random.nextDouble()*3;
					double maxLatency=random.nextDouble()*90+10;
					new Connector(bwReq, maxLatency, app[i], app[i0]);
				}
			}
		}
		Component c=app[random.nextInt(appSize)];
		EndDevice d=new ArrayList<EndDevice>(region.getEndDevices()).get(random.nextInt(region.getEndDevices().size()));
		double bwReq=random.nextDouble()*2;
		double maxLatency=random.nextDouble()*40+40;
		new Connector(bwReq, maxLatency, c, d);
		return new HashSet<Component>(Arrays.asList(app));
	}

	private static Map2d<Infrastructure,Integer,Set<Component>> createApps(Infrastructure[] regions) {
		Map2d<Infrastructure,Integer,Set<Component>> apps=new Map2d<Infrastructure,Integer,Set<Component>>();
		for(int j=0;j<nrAppsPerRegion;j++) {
			for(int i=0;i<nrRegions;i++) {
				Set<Component> app=createApp(regions[i], "c"+i+"."+j+".");
				apps.put(regions[i], j, app);
			}
		}
		return apps;
	}

	private static void doModel2(Infrastructure[] regions, Map2d<Infrastructure,Integer,Set<Component>> apps) throws IOException {
		Orchestrator[] orchestrators=new Orchestrator[nrRegions];
		for(int i=0;i<nrRegions;i++)
			orchestrators[i]=new Orchestrator(regions[i]);
		FileWriter fileWriter=new FileWriter("results_model2.csv");
		fileWriter.write("App;Success;TimeMs;Migrations\n");
		for(int j=0;j<nrAppsPerRegion;j++) {
			long totalSuccess=0;
			long totalTimeMs=0;
			long totalMigrations=0;
			for(int i=0;i<nrRegions;i++) {
				System.out.println("Model 2, Region "+i+", app "+j);
				Set<Component> app=apps.get(regions[i],j);
				Result result=orchestrators[i].addApplication(app);
				totalSuccess+=result.success?1:0;
				totalTimeMs+=result.timeMs;
				totalMigrations+=result.migrations;
			}
			//fileWriter.write(""+j+";"+totalSuccess/nrRegions+";"+totalTimeMs/nrRegions+";"+totalMigrations/nrRegions+"\n");
			//fileWriter.write(String.format("%d;%.2f;%.2f;%.2f\n",j,totalSuccess/nrRegions,totalTimeMs/nrRegions,totalMigrations/nrRegions));
			fileWriter.write(String.format("%d;%d;%d;%d\n",j,totalSuccess,totalTimeMs,totalMigrations));
			fileWriter.flush();
		}
		fileWriter.close();
	}

	private static void doModel1(Infrastructure[] regions, Map2d<Infrastructure,Integer,Set<Component>> apps) throws IOException {
		Infrastructure infra=Infrastructure.unite(regions);
		infra.determinePaths(2);
		Orchestrator orchestrator=new Orchestrator(infra);
		FileWriter fileWriter=new FileWriter("results_model1.csv");
		fileWriter.write("App;Success;TimeMs;Migrations\n");
		for(int j=0;j<nrAppsPerRegion;j++) {
			long totalSuccess=0;
			long totalTimeMs=0;
			long totalMigrations=0;
			for(int i=0;i<nrRegions;i++) {
				System.out.println("Model 1, Region "+i+", app "+j);
				Set<Component> app=apps.get(regions[i],j);
				Result result=orchestrator.addApplication(app);
				totalSuccess+=result.success?1:0;
				totalTimeMs+=result.timeMs;
				totalMigrations+=result.migrations;
			}
			fileWriter.write(String.format("%d;%d;%d;%d\n",j,totalSuccess,totalTimeMs,totalMigrations));
			fileWriter.flush();
		}
		fileWriter.close();
	}

	public static void main(String[] args) throws IOException {
		random=new Random();
		Infrastructure[] regions=createInfra();
		Map2d<Infrastructure,Integer,Set<Component>> apps=createApps(regions);
		//doModel2(regions, apps);
		doModel1(regions, apps);
		/*
		Set<Component> app=new HashSet<>();
		Component c1=new Component("c1",1,1);
		Component c2=new Component("c2",2,1);
		app.add(c1);
		app.add(c2);
		new Connector(1, 10, c1, c2);
		new Connector(1, 10, c1, (EndDevice)infra.getEndDevices().toArray()[0]);
		orch.addApplication(app);
		orch.print();
		app=new HashSet<>();
		app.add(new Component("c3",1,1));
		app.add(new Component("c4",2,1));
		orch.addApplication(app);
		orch.print();
		*/
	}
}
