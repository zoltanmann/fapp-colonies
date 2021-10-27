/**
 * Experiment driver class based on real-world data from Djemai et al.: "A Discrete Particle Swarm 
 * Optimization approach for Energy-efficient IoT services placement over Fog infrastructures" and 
 * from Xia et al.: "Combining Hardware Nodes and Software Components Ordering-based Heuristics for 
 * Optimizing the Placement of Distributed IoT Applications in the Fog".
 */
public class TestReal extends TestDriver {
	/** Nr. of fog nodes per region */
	private int nrL2FogNodesPerRegion=12;
	/** Nr. of end devices per fog node */
	private int nrEndDevicesPerFogNode=4;

	private double cpuCloud=120000;
	private double ramCloud=64000;
	private double cpuProxyServer=60000;
	private double ramProxyServer=8000;
	private double bwCloudProxyServer=10000;
	private double latCloudProxyServer=100;
	private double cpuEdgeSmall=6750;
	private double ramEdgeSmall=1000;
	private double cpuEdgeBig=13500;
	private double ramEdgeBig=2000;
	private double bwEdgeProxyServer=10000;
	private double latEdgeProxyServer=2;
	private double latD0IoT=100;
	private double latD1IoT=20;
	private double latD2IoT=50;
	private double latD3IoT=12;
	private double bwD0IoT=0.65;
	private double bwD1IoT=1;
	private double bwD2IoT=1000;
	private double bwD3IoT=1000;
	private double latencyBetweenColonies=50;
	private double bwBetweenColonies=100;
	private double compCpuMin=2500;
	private double compCpuMax=5000;
	private double compRamMin=500;
	private double compRamMax=1000;
	private double connLatMin=40;
	private double connLatMax=200;
	private double connBwMin=0.2;
	private double connBwMax=0.6;

	/**
	 * Creates the infrastructure, including the colonies, and the path information.
	 */
	@Override
	protected void createInfra() {
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
	private void createRegion(int index) {
		Server cloud=new Server("cloud"+index,cpuCloud,ramCloud,true);
		infra.addServer(cloud);
		colonies[index].addServer(cloud);
		Server proxyServer=new Server("proxy"+index,cpuProxyServer,ramProxyServer,false);
		infra.addServer(proxyServer);
		colonies[index].addServer(proxyServer);
		new Link(bwCloudProxyServer,latCloudProxyServer,cloud,proxyServer);
		int endDeviceIndex=0;
		for(int i=0;i<nrL2FogNodesPerRegion;i++) {
			String serverId="s"+index+"."+i;
			double cpuCap,ramCap;
			int nodeType=i%4;
			if(nodeType<2) {
				cpuCap=cpuEdgeSmall;
				ramCap=ramEdgeSmall;
			} else {
				cpuCap=cpuEdgeBig;
				ramCap=ramEdgeBig;
			}
			Server s=new Server(serverId,cpuCap,ramCap,false);
			infra.addServer(s);
			colonies[index].addServer(s);
			new Link(bwEdgeProxyServer,latEdgeProxyServer,s,proxyServer);
			for(int j=0;j<nrEndDevicesPerFogNode;j++) {
				EndDevice d=new EndDevice("d"+index+"."+endDeviceIndex);
				endDeviceIndex++;
				infra.addEndDevice(d);
				colonies[index].addEndDevice(d);
				double bw,latency;
				if(i%4==0) latency=latD0IoT;
				else if(i%4==1) latency=latD1IoT;
				else if(i%4==2) latency=latD2IoT;
				else latency=latD3IoT;
				if(j%4==0) bw=bwD0IoT;
				else if(j%4==1) bw=bwD1IoT;
				else if(j%4==2) bw=bwD2IoT;
				else bw=bwD3IoT;
				new Link(bw,latency,d,s);
			}
		}
	}

	/**
	 * Creates a link between a random fog node in region1 and a random fog node in
	 * region2.
	 */
	private void connectRegions(Colony region1, Colony region2) {
		region1.addNeighbor(region2);
		region2.addNeighbor(region1);
		Server s1,s2;
		do {
			s1=region1.getRandomServer();
			s2=region2.getRandomServer();
		} while(s1==s2 || s1.isCloud() || s2.isCloud());
		new Link(bwBetweenColonies,latencyBetweenColonies,s1,s2);
	}

	private void createConnector(ISwNode v1,ISwNode v2) {
		double bwReq=connBwMin+Main.random.nextDouble()*(connBwMax-connBwMin);
		double maxLatency=connLatMin+Main.random.nextDouble()*(connLatMax-connLatMin);
		new Connector(bwReq,maxLatency,v1,v2);
	}

	/**
	 * Create an application targeted to the given fog colony. The idPrefix should
	 * be unique among the applications, e.g., "c1.2." for application 2 in colony 1.
	 * This prefix is used to create unique IDs for the components. The application
	 * also includes a connection to a random end device of the fog colony.
	 */
	private Application createApp(Colony region, String idPrefix) {
		Application app=new Application();
		boolean masterWorkersApp=Main.random.nextBoolean();
		for(int i=0;i<appSize;i++) {
			double cpuReq=compCpuMin+Main.random.nextDouble()*(compCpuMax-compCpuMin);
			double ramReq=compRamMin+Main.random.nextDouble()*(compRamMax-compRamMin);
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
	@Override
	protected void createApps() {
		for(int j=0;j<nrAppsPerRegion;j++) {
			for(int i=0;i<nrRegions;i++) {
				Application app=createApp(colonies[i],"c"+i+"."+j+".");
				colonies[i].addApplication(app);
			}
		}
	}

}
