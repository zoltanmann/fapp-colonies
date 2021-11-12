/**
 * Experiment driver class based on synthetic test data.
 */
public class TestSynthetic extends TestDriver {
	/** Nr. of fog nodes per region */
	protected int nrFogNodesPerRegion=10;
	/** Nr. of end devices per region */
	protected int nrEndDevicesPerRegion=10;
	/** Nr. of additional links among servers after creating an initial tree architecture. This number of links is tried to be created; the actual number of created links may be less */
	protected int nrAdditionalLinks=nrFogNodesPerRegion*2;
	/** Nr. of servers connected to an end device */
	protected int nrNeighborsOfEndDevice=2;

	/**
	 * Creates the infrastructure, including the colonies, and the path information.
	 */
	@Override
	protected void createInfra() {
		infra=new Infrastructure();
		colonies=new Colony[nrRegions];
		for(int i=0;i<nrRegions;i++) {
			colonies[i]=new Colony(i);
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
		Server cloud=new Server("cloud"+index,1000000,1000000,true,index);
		infra.addServer(cloud);
		colonies[index].addServer(cloud);
		for(int i=0;i<nrFogNodesPerRegion;i++) {
			String serverId="s"+index+"."+i;
			double cpuCap=Main.random.nextDouble()*9+1;
			double ramCap=Main.random.nextDouble()*9+1;
			Server s=new Server(serverId,cpuCap,ramCap,false,index);
			if(i>0) {
				Server s0=colonies[index].getRandomServer();
				double bw=Main.random.nextDouble()*4+1;
				double latency=Main.random.nextDouble()*4+1;
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
			double bw=Main.random.nextDouble()*4+1;
			double latency=Main.random.nextDouble()*4+1;
			new Link(bw,latency,s1,s2);
		}
		for(int i=0;i<nrEndDevicesPerRegion;i++) {
			EndDevice d=new EndDevice("d"+index+"."+i);
			for(int j=0;j<nrNeighborsOfEndDevice;j++) { ///
				Server s=colonies[index].getRandomServer();
				double bw=Main.random.nextDouble()*5+5;
				double latency=Main.random.nextDouble()*3;
				new Link(bw,latency,s,d);
			}
			infra.addEndDevice(d);
			colonies[index].addEndDevice(d);
		}
		Server s=colonies[index].getRandomServer();
		double bw=Main.random.nextDouble()*4+1;
		double latency=Main.random.nextDouble()*40+40;///
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
		} while(s1==s2 || s1.isCloud() || s2.isCloud());
		double bw=Main.random.nextDouble()*4+1;
		double latency=Main.random.nextDouble()*4+1;
		new Link(bw,latency,s1,s2);
	}

	/**
	 * Create an application targeted to the given fog colony. The idPrefix should
	 * be unique among the applications, e.g., c1.2 for application 2 in colony 1.
	 * This prefix is used to create unique IDs for the components. The application
	 * also includes a connection to a random end device of the fog colony.
	 */
	private Application createApp(Colony region, String idPrefix) {
		Application app=new Application();
		for(int i=0;i<appSize;i++) {
			double cpuReq=Main.random.nextDouble()*5;
			double ramReq=Main.random.nextDouble()*5;
			Component comp=new Component(idPrefix+i,cpuReq,ramReq,region.getNr());
			if(i>0) {
				double bwReq=Main.random.nextDouble()*3;
				double maxLatency=Main.random.nextDouble()*30+30;///
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
		double bwReq=Main.random.nextDouble()*2;
		double maxLatency=Main.random.nextDouble()*20+20;///
		new Connector(bwReq, maxLatency, c, d);
		return app;
	}

	/**
	 * Create all applications.
	 */
	@Override
	protected void createApps() {
		for(int j=0;j<nrAppsPerRegion;j++) {
			for(int i=0;i<nrRegions;i++) {
				Application app=createApp(colonies[i], "c"+i+"."+j+".");
				colonies[i].addApplication(app);
			}
		}
	}

}
