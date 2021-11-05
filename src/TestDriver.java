import java.io.FileWriter;
import java.io.IOException;
import java.util.Set;

/**
 * Abstract ancestor class for different experiments.
 */
public abstract class TestDriver {
	/** The types of solvers to test */
	enum SolverType {SolverSB, SolverILP}

	/** Nr. of regions */
	protected int nrRegions=5;
	/** Nr. of applications per region */
	protected int nrAppsPerRegion=5;
	/** Nr. of components per application */
	protected int appSize=12;
	/** Nr. of fog nodes per colony that will be shared with each neighboring colony  */
	protected int nrNodesToShareWithNeighbor=2;

	/** The complete infrastructure */
	protected Infrastructure infra;
	/** Set of all fog colonies */
	protected Colony colonies[];
	/** To accelerate experiments, the centralized approach can be switched off with this flag */
	protected boolean skipModel1=false;

	/** Creation of the infrastructure, delegated to inheriting classes */
	protected abstract void createInfra();
	/** Creation of the applications, delegated to inheriting classes */
	protected abstract void createApps();

	/**
	 * Perform the experiments.
	 */
	private void doExperiment(String fileNameSuffix) throws IOException {
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
		FileWriter fileWriter=new FileWriter("results_detail"+fileNameSuffix+".csv");
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
		fileWriter=new FileWriter("results_total"+fileNameSuffix+".csv");
		fileWriter.write("Model;Solver;Success;TimeMs;Migrations\n");
		for(Conductor.ModeType mode : Conductor.ModeType.values()) {
			for(SolverType solver : SolverType.values()) {
				fileWriter.write(""+mode+";"+solver+";"+grandTotalResults.get(mode,solver).toString()+"\n");
			}
		}
		fileWriter.close();
	}

	/**
	 * Create infrastructure and applications, and perform the experiments.
	 */
	public void doTest(String fileNameSuffix) throws IOException {
		createInfra();
		createApps();
		doExperiment(fileNameSuffix);
	}
}
