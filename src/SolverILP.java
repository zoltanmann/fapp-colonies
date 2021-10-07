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

public class SolverILP {
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
	 * Helper method to determine the set of paths among the given set of HW nodes.
	 */
	private Set<Path> getRelevantPaths(Infrastructure infra,Set<IHwNode> nodes) {
		Set<Path> paths=new HashSet<>();
		for(IHwNode node1 : nodes) {
			for(IHwNode node2 : nodes) {
				paths.addAll(infra.getPaths(node1, node2));
			}
		}
		return paths;
	}

	/**
	 * Perform an optimization run, trying to place the new components.
	 */
	public Result optimize(
			Set<Server> freelyUsableServers,
			Set<Server> unpreferredServers,
			Set<Component> newComponents,
			Set<Component> fullyControlledComponents,
			Set<Component> obtainedComponents,
			Set<Component> readOnlyComponents,
			Colony ourColony) {
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
						for(Path p : infra.getPaths(n1, n2)) {
							GRBVar yVar=y.get(conn, p);
							expr.addTerm(1,yVar);
						}
						expr.addTerm(-1,x1);
						expr.addTerm(-1,x2);
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
					for(Link l : p.getLinks())
						availableBw.put(l,availableBw.get(l)+c.getBwReq());
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
			//A component that colony k received from colony k' may only be placed in k or k'
			for(Component c : obtainedComponents) {
				Colony targetColony=c.getTargetColony();
				for(Server s : servers) {
					if(!ourColony.getServers().contains(s) && !targetColony.getServers().contains(s)) {
						GRBVar xVar=x.get(c, s);
						GRBLinExpr expr = new GRBLinExpr();
						expr.addTerm(1, xVar);
						model.addConstr(expr,GRB.EQUAL,0,"NoForward_"+c+"_"+s);
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
