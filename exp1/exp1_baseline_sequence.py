import csv
import pandas as pd
import seaborn as sns

input_files_prefix="results_detail_"
input_files_min=0
input_files_max=9
input_files_extension=".csv"
solvers=('SolverSB','SolverILP')
metrics=('Success','TimeMs','Migrations')
models=('centralized','independent','communicating','overlapping')
ylabels={'Success':'Applications successfully placed','TimeMs':'Execution time [ms]','Migrations':'Migrations'}

def add_observation(phase,run,model,result):
    phase_list.append(phase)
    run_list.append(run)
    model_list.append(model)
    result_list.append(result)

for solver in solvers:
    for metric in metrics:
        phase_list=[]
        run_list=[]
        model_list=[]
        result_list=[]
        for i in range(input_files_min,input_files_max+1):
            input_file_name=input_files_prefix+str(i)+input_files_extension
            with open(input_file_name) as input_file:
                reader=csv.DictReader(input_file, delimiter=';')
                phase=1
                for row in reader:
                    for model in models:
                        key=metric+'-'+model+'-'+solver
                        add_observation(phase,i,model,int(row[key]))
                    phase+=1
        data=pd.DataFrame({'Phase':phase_list,'Run':run_list,'Model':model_list,'Result':result_list})
        sns.set_palette(sns.cubehelix_palette(4,hue=0.05,rot=0,light=0.9,dark=0))
        if metric=='TimeMs' and solver=='SolverILP':
            ax=sns.boxplot(x='Phase',y='Result',data=data,hue='Model')
        else:
            ax=sns.boxplot(x='Phase',y='Result',data=data,hue='Model',showmeans=True,meanprops={"marker":"o","markerfacecolor":"white","markeredgecolor":"black","markersize":"10"})
        if metric=='Success':
            ax.set(ylim=(0,5))
        if metric=='Migrations':
            ax.set(ylim=(0,10))
        if metric=='TimeMs' and solver=='SolverILP':
            ax.set(ylim=(100,500000))
            ax.set(yscale="log")
            sns.move_legend(ax,"lower center",bbox_to_anchor=(.48, 1),ncol=4,title=None,frameon=True)
        if metric=='TimeMs' and solver=='SolverSB':
            ax.set(ylim=(0,20))
        ax.set(ylabel=ylabels[metric])
        ax.get_figure().savefig('exp1_'+solver+'_'+metric+'.pdf')
        ax.get_figure().clf()

