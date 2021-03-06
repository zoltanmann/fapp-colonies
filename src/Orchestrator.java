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

/**
 * ILP-based orchestrator. Solves the optimization problem by transforming it to an
 * integer program using the Gurobi interface, letting Gurobi solve the problem, and
 * then converting Gurobi's solution to a solution of the original problem.
 */
public class Orchestrator {
	private Infrastructure infra;
	private Set<Component> components;
	private Map<Component,Server> compMapping;
	private Set<Orchestrator> neighbors;
	private static final double mu=10;
	private static final double nu=10;
	private Colony colony;
	private int coordModel;

	public Orchestrator(Infrastructure infra, int coordModel) {
		this.infra=infra;
		components=new HashSet<>();
		compMapping=new HashMap<>();
		neighbors=new HashSet<>();
		colony=null;
		this.coordModel=coordModel;
	}

	public void addNeighbor(Orchestrator neighbor) {
		neighbors.add(neighbor);
	}

	public void setColony(Colony colony) {
		this.colony=colony;
	}

	public Map<Component,Server> getCompMapping() {
		return compMapping;
	}

	public Result addApplication(Application app) {
		long startTime=System.currentTimeMillis();
		//long t=System.currentTimeMillis();
		Result result=new Result();
		//preparing collections
		Set<Component> allComponents=new HashSet<>(components);
		Set<Component> ourComponents=new HashSet<>(components);
		Map<Component,Server> foreignCompMapping=new HashMap<>();
		for(int i=0;i<app.getSize();i++) {
			allComponents.add(app.getComponent(i));
			ourComponents.add(app.getComponent(i));
		}
		for(Orchestrator neighbor : neighbors) {
			for(Component comp : neighbor.getCompMapping().keySet()) {
				Server s=neighbor.getCompMapping().get(comp);
				if(coordModel==3 || (coordModel==4 && colony.getServers().contains(s))) {
					allComponents.add(comp);
					foreignCompMapping.put(comp, s);
				}
			}
		}
		Set<Connector> allConnectors=new HashSet<>();
		for(Component comp : ourComponents) {
			for(Connector conn : comp.getConnectors()) {
				allConnectors.add(conn);
			}
		}
		Set<ISwNode> allSwNodes=new HashSet<>(allComponents);
		allSwNodes.addAll(infra.getEndDevices());
		Set<IHwNode> allHwNodes=infra.getNodes();
		Set<Path> allPaths=infra.getAllPaths();
		Set<Link> allLinks=infra.getAllInternalLinks();
		//System.out.println("Preparations finished: "+(System.currentTimeMillis()-t));
		//t=System.currentTimeMillis();
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
					if(coordModel==3 && !colony.getServers().contains(hn))
						objWeight=mu;
					if(coordModel==4 && colony.isShared(hn))
						objWeight=nu;
					GRBVar var=model.addVar(0,1,objWeight,GRB.BINARY,"x_"+sn.getId()+"_"+hn.getId());
					x.put(sn,hn,var);
				}
			}
			//System.out.println("allConnectors: "+allConnectors);
			//System.out.println("allPaths: "+allPaths);
			for(Connector conn : allConnectors) {
				for(Path path : allPaths) {
					GRBVar var=model.addVar(0,1,0,GRB.BINARY,"y_"+conn.getId()+"_"+path.getId());
					y.put(conn,path,var);
				}
			}
			for(Component comp : components) {
				GRBVar var=model.addVar(0,1,1,GRB.BINARY,"z_"+comp.getId());//part of obj. function with coeff. 1
				z.put(comp, var);
			}
			//System.out.println("Variables created: "+(System.currentTimeMillis()-t));
			//t=System.currentTimeMillis();
			//(9)-(10) each component on exactly one server and on no end device
			for(Component comp : allComponents) {
				GRBLinExpr expr = new GRBLinExpr();
				for(Server s : infra.getServers()) {
					GRBVar var=x.get(comp,s);
					expr.addTerm(1,var);
				}
				model.addConstr(expr,GRB.EQUAL,1,"Exactly1_"+comp.getId());
				for(EndDevice dev : infra.getEndDevices()) {
					expr = new GRBLinExpr();
					GRBVar var=x.get(comp,dev);
					expr.addTerm(1,var);
					model.addConstr(expr,GRB.EQUAL,0,"Surely0_"+comp.getId()+"_"+dev.getId());
				}
			}
			//System.out.println("(9)-(10): "+(System.currentTimeMillis()-t));
			//t=System.currentTimeMillis();
			//(11) each connector on exactly one path
			for(Connector conn : allConnectors) {
				GRBLinExpr expr=new GRBLinExpr();
				for(Path path : allPaths) {
					GRBVar var=y.get(conn,path);
					expr.addTerm(1,var);
				}
				model.addConstr(expr,GRB.EQUAL,1,"Exactly1_"+conn.getId());
			}
			//System.out.println("(11): "+(System.currentTimeMillis()-t));
			//t=System.currentTimeMillis();
			//(12)-(13) each end device on itself
			for(EndDevice dev : infra.getEndDevices()) {
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
			//System.out.println("(12)-(13): "+(System.currentTimeMillis()-t));
			//t=System.currentTimeMillis();
			//(14) consistency of x and y
			for(Connector conn : allConnectors) {
				for(IHwNode n1 : allHwNodes) {
					GRBVar x1=x.get(conn.getV1(), n1);
					//System.out.println("x1: "+x1);
					for(IHwNode n2 : allHwNodes) {
						GRBLinExpr expr = new GRBLinExpr();
						GRBVar x2=x.get(conn.getV2(), n2);
						//System.out.println("x2: "+x2);
						for(Path p : infra.getPaths(n1, n2)) {
							//System.out.println("conn: "+conn);
							//System.out.println("p: "+p);
							GRBVar yVar=y.get(conn, p);
							//System.out.println("yVar: "+yVar);
							expr.addTerm(1,yVar);
						}
						expr.addTerm(-1,x1);
						expr.addTerm(-1,x2);
						model.addConstr(expr,GRB.GREATER_EQUAL,-1,"Consistent_"+conn.getId()+"_"+n1.getId()+"_"+n2.getId());
					}
				}
			}
			//System.out.println("(14): "+(System.currentTimeMillis()-t));
			//t=System.currentTimeMillis();
			//(15)-(16) node capacity constraints
			for(Server s : infra.getServers()) {
				GRBLinExpr expr1 = new GRBLinExpr();
				GRBLinExpr expr2 = new GRBLinExpr();
				for(Component c : allComponents) {
					GRBVar xVar=x.get(c, s);
					expr1.addTerm(c.getCpuReq(), xVar);
					expr2.addTerm(c.getRamReq(), xVar);
				}
				model.addConstr(expr1,GRB.LESS_EQUAL,s.getCpuCap(),"NodeCpu_"+s.getId());
				model.addConstr(expr2,GRB.LESS_EQUAL,s.getRamCap(),"NodeRam_"+s.getId());
			}
			//System.out.println("(15)-(16): "+(System.currentTimeMillis()-t));
			//t=System.currentTimeMillis();
			//(17) bandwidth constraints
			for(Link l : allLinks) {
				//System.out.println("l: "+l.getId());
				GRBLinExpr expr = new GRBLinExpr();
				//if(infra.getPathsOfLink(l)==null)
				//	System.out.println("NULL: "+l.getId());
				for(Path p : infra.getPathsOfLink(l)) {
					//System.out.println("p: "+p);
					for(Connector conn : allConnectors) {
						//System.out.println("conn: "+conn);
						GRBVar yVar=y.get(conn, p);
						expr.addTerm(conn.getBwReq(), yVar);
					}
				}
				model.addConstr(expr,GRB.LESS_EQUAL,l.getBw(),"Bw_"+l.getId());
			}
			//System.out.println("(17): "+(System.currentTimeMillis()-t));
			//t=System.currentTimeMillis();
			//(18) latency constraints
			for(Connector conn : allConnectors) {
				GRBLinExpr expr = new GRBLinExpr();
				for(Path p : allPaths) {
					GRBVar yVar=y.get(conn, p);
					expr.addTerm(p.getLatency(), yVar);
				}
				model.addConstr(expr,GRB.LESS_EQUAL,conn.getMaxLatency(),"Latency_"+conn.getId());
			}
			//System.out.println("(18): "+(System.currentTimeMillis()-t));
			//t=System.currentTimeMillis();
			//(19) setting migration variables
			for(Component c : components) {
				GRBVar zVar=z.get(c);
				GRBVar xVar=x.get(c, compMapping.get(c));
				GRBLinExpr expr = new GRBLinExpr();
				expr.addTerm(1, zVar);
				expr.addTerm(1, xVar);
				model.addConstr(expr,GRB.EQUAL,1,"Migr_"+c);
			}
			//foreign components must not be migrated
			for(Component c : foreignCompMapping.keySet()) {
				Server s=foreignCompMapping.get(c);
				GRBVar xVar=x.get(c, s);
				GRBLinExpr expr = new GRBLinExpr();
				expr.addTerm(1, xVar);
				model.addConstr(expr,GRB.EQUAL,1,"NoMigr_"+c);
			}
			//System.out.println("(19): "+(System.currentTimeMillis()-t));
			//t=System.currentTimeMillis();
			//perform optimization
			//model.write("model.lp");
			model.getEnv().set(GRB.DoubleParam.TimeLimit,60);
			model.getEnv().set(GRB.IntParam.LogToConsole,0);
			//System.out.println("Running model.optimize()");
			model.optimize();
			//System.out.println("Optimize finished: "+(System.currentTimeMillis()-t));
			//t=System.currentTimeMillis();
			if(model.get(IntAttr.SolCount)>0) {
				//model.write("solution.sol");
				result.success=1;
				result.migrations=0;
				for(Component comp : components) {
					GRBVar var=z.get(comp);
					if(var.get(GRB.DoubleAttr.X)>0.5) {
						result.migrations++;
						System.out.println("Migration of component "+comp.getId());
					}
				}
				//retrieve solution
				for(Component comp : ourComponents) {
					for(Server s : infra.getServers()) {
						GRBVar var=x.get(comp,s);
						if(var.get(GRB.DoubleAttr.X)>0.5) {
							if(coordModel==1 || coordModel==2) {
								compMapping.put(comp,s);
								components.add(comp);
								System.out.println("Allocating component "+comp.getId()+" to own server "+s.getId());
							} else {
								if(colony.getServers().contains(s)) {
									compMapping.put(comp,s);
									components.add(comp);
									System.out.println("Allocating component "+comp.getId()+" to own server "+s.getId());
								} else {
									for(Orchestrator orch : neighbors) {
										if(orch.colony.getServers().contains(s)) {
											orch.compMapping.put(comp, s);
											orch.components.add(comp);
											System.out.println("Allocating component "+comp.getId()+" to neighbor's server "+s.getId());
										}
										compMapping.remove(comp);
										components.remove(comp);
										System.out.println("Removing component "+comp.getId()+" from own infrastructure; s="+s.getId());
									}
								}
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
		System.out.println("Success: "+result.success);
		System.out.println("TimeMs: "+result.timeMs);
		return result;
	}

	public void print() {
		System.out.println(compMapping);
	}
}
