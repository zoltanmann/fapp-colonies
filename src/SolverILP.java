import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import gurobi.GRB;
import gurobi.GRB.IntAttr;
import gurobi.GRBEnv;
import gurobi.GRBException;
import gurobi.GRBLinExpr;
import gurobi.GRBModel;
import gurobi.GRBVar;

public class SolverILP implements ISolver {
	/** Reference to the bookKeeper */
	private BookKeeper bookKeeper;
	private static final double mu=10;

	/**
	 * Constructor.
	 */
	public SolverILP(BookKeeper bookKeeper) {
		this.bookKeeper=bookKeeper;
	}

	/**
	 * Helper method to create the union of an arbitrary number of sets of the same 
	 * type of objects.
	 */
	@SafeVarargs
	private static <T> Set<T> union(Set<T>... sets) {
		Set<T> result=new HashSet<>();
		for(Set<T> s : sets)
			result.addAll(s);
		return result;
	}

	/**
	 * Helper method to determine the set of links among the given set of HW nodes.
	 */
	private Set<Link> getRelevantLinks(Set<IHwNode> nodes) {
		Set<Link> links=new HashSet<>();
		for(IHwNode node : nodes) {
			for(Link link : node.getLinks()) {
				if(nodes.contains(link.getOtherNode(node)))
					links.add(link);
			}
		}
		return links;
	}

	/**
	 * Helper method to determine the set of paths between the two given HW nodes, given the set of
	 * all relevant nodes.
	 */
	private Set<Path> getRelevantPaths(Infrastructure infra,IHwNode n1,IHwNode n2,Set<IHwNode> nodes) {
		Set<Path> paths=new HashSet<>();
		for(Path p : infra.getPaths(n1,n2)) {
			boolean good=true;
			for(IHwNode n : p.getNodes()) {
				if(!nodes.contains(n)) {
					good=false;
					break;
				}
			}
			if(good)
				paths.add(p);
		}
		return paths;
	}

	/**
	 * Helper method to determine the set of all paths among the given set of HW nodes.
	 */
	private Set<Path> getRelevantPaths(Infrastructure infra,Set<IHwNode> nodes) {
		Set<Path> paths=new HashSet<>();
		for(IHwNode node1 : nodes) {
			for(IHwNode node2 : nodes) {
				paths.addAll(getRelevantPaths(infra,node1,node2,nodes));
			}
		}
		return paths;
	}

	/**
	 * Perform an optimization run, trying to place the new components.
	 */
	@Override
	public Result optimize(
			Set<Server> freelyUsableServers,
			Set<Server> unpreferredServers,
			Set<Component> newComponents,
			Set<Component> fullyControlledComponents,
			Set<Component> obtainedComponents,
			Set<Component> readOnlyComponents,
			Colony ourColony,
			Conductor.ModeType mode) {
		long startTime=System.currentTimeMillis();
		Result result=new Result();
		Infrastructure infra=bookKeeper.getInfra();
		//preparing collections
		Set<Component> ourComponents=union(newComponents,fullyControlledComponents,obtainedComponents);
		Set<Component> allComponents=union(ourComponents,readOnlyComponents);
		Set<Component> oldComponents=union(fullyControlledComponents,obtainedComponents,readOnlyComponents);
		Set<Connector> allConnectors=new HashSet<>();
		for(Component comp : ourComponents)
			allConnectors.addAll(comp.getConnectors());
		//sanity check (for debugging purposes)
		for(Connector conn : allConnectors) {
			if((conn.getV1() instanceof Component && !allComponents.contains(conn.getV1()))
					|| (conn.getV2() instanceof Component && !allComponents.contains(conn.getV2()))) {
				System.out.println("freelyUsableServers: "+freelyUsableServers);
				System.out.println("unpreferredServers: "+unpreferredServers);
				System.out.println("newComponents: "+newComponents);
				System.out.println("fullyControlledComponents: "+fullyControlledComponents);
				System.out.println("obtainedComponents: "+obtainedComponents);
				System.out.println("readOnlyComponents: "+readOnlyComponents);
				System.out.println("PROBLEM with connector "+conn);
				System.out.println("Component 1: "+conn.getV1()+" on "+bookKeeper.getHost((Component)conn.getV1()));
				System.out.println("Component 2: "+conn.getV2()+" on "+bookKeeper.getHost((Component)conn.getV2()));
				System.exit(-1);
			}
		}
		Set<EndDevice> endDevices=new HashSet<>();
		for(Component comp : allComponents) {
			for(Connector conn : comp.getConnectors()) {
				ISwNode other=conn.getOtherVertex(comp);
				if(other.isEndDevice())
					endDevices.add((EndDevice)other);
			}
		}
		Set<ISwNode> allSwNodes=new HashSet<>(allComponents);
		allSwNodes.addAll(endDevices);
		Set<Server> servers=union(freelyUsableServers,unpreferredServers);
		Set<IHwNode> allHwNodes=new HashSet<>(servers);
		allHwNodes.addAll(endDevices);
		Set<Path> allPaths=getRelevantPaths(infra,allHwNodes);
		Set<Link> allLinks=getRelevantLinks(allHwNodes);
		//creating variables
		Map2d<ISwNode,IHwNode,GRBVar> x=new Map2d<>();
		Map2d<Connector,Path,GRBVar> y=new Map2d<>();
		Map<Component,GRBVar> z=new HashMap<>();
		try {
			GRBEnv env=new GRBEnv("milp.log");
			GRBModel model=new GRBModel(env);
			for(ISwNode sn : allSwNodes) {
				for(IHwNode hn : allHwNodes) {
					double objWeight=0;
					if(unpreferredServers.contains(hn))
						objWeight=mu;
					GRBVar var=model.addVar(0,1,objWeight,GRB.BINARY,"x_"+sn.getId()+"_"+hn.getId());
					x.put(sn,hn,var);
				}
			}
			for(Connector conn : allConnectors) {
				for(Path path : allPaths) {
					GRBVar var=model.addVar(0,1,0,GRB.BINARY,"y_"+conn.getId()+"_"+path.getId());
					y.put(conn,path,var);
				}
			}
			for(Component comp : oldComponents) {
				GRBVar var=model.addVar(0,1,1,GRB.BINARY,"z_"+comp.getId());//part of obj. function with coeff. 1
				z.put(comp, var);
			}
			//(9)-(10) each component on exactly one server and on no end device
			for(Component comp : allComponents) {
				GRBLinExpr expr = new GRBLinExpr();
				for(Server s : servers) {
					GRBVar var=x.get(comp,s);
					expr.addTerm(1,var);
				}
				model.addConstr(expr,GRB.EQUAL,1,"Exactly1_"+comp.getId());
				for(EndDevice dev : endDevices) {
					expr = new GRBLinExpr();
					GRBVar var=x.get(comp,dev);
					expr.addTerm(1,var);
					model.addConstr(expr,GRB.EQUAL,0,"Surely0_"+comp.getId()+"_"+dev.getId());
				}
			}
			//(11) each connector on exactly one path
			for(Connector conn : allConnectors) {
				GRBLinExpr expr=new GRBLinExpr();
				for(Path path : allPaths) {
					GRBVar var=y.get(conn,path);
					expr.addTerm(1,var);
				}
				model.addConstr(expr,GRB.EQUAL,1,"Exactly1_"+conn.getId());
			}
			//(12)-(13) each end device on itself
			for(EndDevice dev : endDevices) {
				for(IHwNode hn : allHwNodes) {
					GRBLinExpr expr = new GRBLinExpr();
					GRBVar var=x.get(dev,hn);
					expr.addTerm(1,var);
					if(dev!=hn)
						model.addConstr(expr,GRB.EQUAL,0,"Surely0_"+dev.getId()+"_"+hn.getId());
					else
						model.addConstr(expr,GRB.EQUAL,1,"Surely1_"+dev.getId());
				}
			}
			//(14) consistency of x and y
			for(Connector conn : allConnectors) {
				for(IHwNode n1 : allHwNodes) {
					GRBVar x1=x.get(conn.getV1(), n1);
					for(IHwNode n2 : allHwNodes) {
						GRBLinExpr expr = new GRBLinExpr();
						GRBVar x2=x.get(conn.getV2(), n2);
						for(Path p : getRelevantPaths(infra,n1,n2,allHwNodes)) {
							GRBVar yVar=y.get(conn, p);
							expr.addTerm(1,yVar);
						}
						expr.addTerm(-1,x1);
						expr.addTerm(-1,x2);
						//System.out.println("conn: "+conn+", n1: "+n1+", n2: "+n2+", x1: "+x1+", x2: "+x2);//just for debugging purposes
						model.addConstr(expr,GRB.GREATER_EQUAL,-1,"Consistent_"+conn.getId()+"_"+n1.getId()+"_"+n2.getId());
					}
				}
			}
			//(15)-(16) node capacity constraints
			//first, we need to calculate the available capacity, i.e., the capacity that would be free if our components were unplaced
			Map<Server,Double> availableCpuCap=new HashMap<>();
			Map<Server,Double> availableRamCap=new HashMap<>();
			for(Server s : servers) {
				availableCpuCap.put(s,bookKeeper.getFreeCpuCap(s));
				availableRamCap.put(s,bookKeeper.getFreeRamCap(s));
			}
			for(Component c : oldComponents) {
				Server s=bookKeeper.getHost(c);
				availableCpuCap.put(s,availableCpuCap.get(s)+c.getCpuReq());
				availableRamCap.put(s,availableRamCap.get(s)+c.getRamReq());
			}
			for(Server s : servers) {
				GRBLinExpr expr1 = new GRBLinExpr();
				GRBLinExpr expr2 = new GRBLinExpr();
				for(Component c : allComponents) {
					GRBVar xVar=x.get(c, s);
					expr1.addTerm(c.getCpuReq(), xVar);
					expr2.addTerm(c.getRamReq(), xVar);
				}
				model.addConstr(expr1,GRB.LESS_EQUAL,availableCpuCap.get(s),"NodeCpu_"+s.getId());
				model.addConstr(expr2,GRB.LESS_EQUAL,availableRamCap.get(s),"NodeRam_"+s.getId());
			}
			//(17) bandwidth constraints
			//first, we need to calculate the available bandwidth, i.e., the bandwidth that would be free if our connectors were unrouted
			Map<Link,Double> availableBw=new HashMap<>();
			for(Link l : allLinks)
				availableBw.put(l,bookKeeper.getFreeBandwidth(l));
			for(Connector c : allConnectors) {
				Path p=bookKeeper.getPath(c);
				if(p!=null) {
					for(Link l : p.getLinks()) {
						if(allLinks.contains(l))
							availableBw.put(l,availableBw.get(l)+c.getBwReq());
					}
				}
			}
			for(Link l : allLinks) {
				GRBLinExpr expr = new GRBLinExpr();
				for(Path p : allPaths) {
					if(p.contains(l)) {
						for(Connector conn : allConnectors) {
							GRBVar yVar=y.get(conn, p);
							expr.addTerm(conn.getBwReq(), yVar);
						}
					}
				}
				model.addConstr(expr,GRB.LESS_EQUAL,availableBw.get(l),"Bw_"+l.getId());
			}
			//(18) latency constraints
			for(Connector conn : allConnectors) {
				GRBLinExpr expr = new GRBLinExpr();
				for(Path p : allPaths) {
					GRBVar yVar=y.get(conn, p);
					expr.addTerm(p.getLatency(), yVar);
				}
				model.addConstr(expr,GRB.LESS_EQUAL,conn.getMaxLatency(),"Latency_"+conn.getId());
			}
			//(19) setting migration variables
			for(Component c : oldComponents) {
				GRBVar zVar=z.get(c);
				GRBVar xVar=x.get(c,bookKeeper.getHost(c));
				GRBLinExpr expr = new GRBLinExpr();
				expr.addTerm(1, zVar);
				expr.addTerm(1, xVar);
				model.addConstr(expr,GRB.EQUAL,1,"Migr_"+c);
			}
			//foreign components must not be migrated
			for(Component c : readOnlyComponents) {
				Server s=bookKeeper.getHost(c);
				GRBVar xVar=x.get(c, s);
				GRBLinExpr expr = new GRBLinExpr();
				expr.addTerm(1, xVar);
				model.addConstr(expr,GRB.EQUAL,1,"NoMigr_"+c);
			}
			if(mode==Conductor.ModeType.communicating) {
				//A component that colony k received from colony k' may only be placed in k or k'
				for(Component c : obtainedComponents) {
					int targetColony=c.getTargetColony();
					for(Server s : servers) {
						if(!ourColony.getServers().contains(s) && !s.belongsToColony(targetColony)) {
							GRBVar xVar=x.get(c, s);
							GRBLinExpr expr = new GRBLinExpr();
							expr.addTerm(1, xVar);
							model.addConstr(expr,GRB.EQUAL,0,"NoForward_"+c+"_"+s);
						}
					}
				}
				//If there is a connector c1-c2, and k' and k'' are colonies different from each other and from ours, then it is forbidden to place c1 on k' and c2 on k''
				for(Connector conn : allConnectors) {
					ISwNode sn1=conn.getV1();
					ISwNode sn2=conn.getV2();
					if(!ourComponents.contains(sn1) || !ourComponents.contains(sn2))
						continue;
					if(sn1 instanceof Component && sn2 instanceof Component) {
						for(Colony k1 : ourColony.getNeighbors()) {
							for(Colony k2 : ourColony.getNeighbors()) {
								if(k1==k2)
									continue;
								for(Server s1 : k1.getServers()) {
									//if(freelyUsableServers.contains(s1))//case of the cloud
									if(!unpreferredServers.contains(s1))//covers both the case of the cloud and non-communicating colonies
										continue;
									GRBVar x1=x.get(sn1,s1);
									for(Server s2 : k2.getServers()) {
										//if(freelyUsableServers.contains(s2))//case of the cloud
										if(!unpreferredServers.contains(s2))//covers both the case of the cloud and non-communicating colonies
											continue;
										GRBVar x2=x.get(sn2,s2);
										GRBLinExpr expr = new GRBLinExpr();
										expr.addTerm(1,x1);
										expr.addTerm(1,x2);
										//System.out.println("NoCross_"+sn1+"_"+sn2+"_"+s1+"_"+s2);
										model.addConstr(expr,GRB.LESS_EQUAL,1,"NoCross_"+sn1+"_"+sn2+"_"+s1+"_"+s2);
									}
								}
							}
						}
					}
				}
			}
			//perform optimization
			//model.write("model.lp");
			model.getEnv().set(GRB.DoubleParam.TimeLimit,60);
			model.getEnv().set(GRB.IntParam.LogToConsole,0);
			model.optimize();
			if(model.get(IntAttr.SolCount)>0) {
				//model.write("solution.sol");
				result.success=1;
				result.migrations=0;
				//retrieve solution
				for(Component comp : ourComponents) {
					Server oldServer=bookKeeper.getHost(comp);
					for(Server s : servers) {
						GRBVar var=x.get(comp,s);
						if(var.get(GRB.DoubleAttr.X)>0.5) {
							if(newComponents.contains(comp))
								bookKeeper.place(comp,s);
							else if(s!=oldServer) {
								bookKeeper.unPlace(comp);
								bookKeeper.place(comp,s);
								result.migrations++;
							}
							break;
						}
					}
				}
				for(Connector conn : allConnectors) {
					Path oldPath=bookKeeper.getPath(conn);
					for(Path p: allPaths) {
						GRBVar var=y.get(conn,p);
						if(var.get(GRB.DoubleAttr.X)>0.5) {
							if(oldPath==null)
								bookKeeper.route(conn,p);
							else if(p!=oldPath) {
								bookKeeper.unRoute(conn);
								bookKeeper.route(conn,p);
							}
							break;
						}
					}
				}
			} else {
				result.success=0;
				result.migrations=0;
			}
			model.dispose();
			env.dispose();
		} catch (GRBException e) {
			e.printStackTrace();
			result.success=0;
			result.migrations=0;
		}
		result.timeMs=System.currentTimeMillis()-startTime;
		System.out.println("Result: "+result);
		return result;
	}
}
