import csv
import pandas as pd
import seaborn as sns

nodes_nums=(12,24,36,48,60)

solvers=('SolverSB','SolverILP')
metrics=('Success','TimeMs','Migrations')
models=('centralized','independent','communicating','overlapping')
ylabels={'Success':'Applications successfully placed','TimeMs':'Execution time [ms]','Migrations':'Migrations'}

def add_observation(nodes_num,run,model,result):
    nodes_num_list.append(2+5*nodes_num)
    run_list.append(run)
    model_list.append(model)
    result_list.append(result)

for solver in solvers:
    for metric in metrics:
        nodes_num_list=[]
        run_list=[]
        model_list=[]
        result_list=[]
        for nodes_num in nodes_nums:
            for i in range(10):
                input_file_name=str(nodes_num)+'nodes/results_total_'+str(i)+'.csv'
                with open(input_file_name) as input_file:
                    reader=csv.DictReader(input_file, delimiter=';')
                    for row in reader:
                        if row['Solver']==solver:
                            add_observation(nodes_num,i,row['Model'],int(row[metric]))
        data=pd.DataFrame({'Nodes per region':nodes_num_list,'Run':run_list,'Model':model_list,'Result':result_list})
        sns.set_palette(sns.cubehelix_palette(4,hue=0.05,rot=0,light=0.9,dark=0))
        if metric=='TimeMs' and solver=='SolverILP':
            ax=sns.boxplot(x='Nodes per region',y='Result',data=data,hue='Model')
        else:
            ax=sns.boxplot(x='Nodes per region',y='Result',data=data,hue='Model',showmeans=True,meanprops={"marker":"o","markerfacecolor":"white","markeredgecolor":"black","markersize":"10"})
        if metric=='Success':
            ax.set(ylim=(0,25))
        if metric=='Migrations':
            ax.set(ylim=(0,160))
        if metric=='TimeMs' and solver=='SolverILP':
            ax.set(ylim=(1000,5000000))
            ax.set(yscale="log")
            sns.move_legend(ax,"lower center",bbox_to_anchor=(.48, 1),ncol=4,title=None,frameon=True)
        if metric=='TimeMs' and solver=='SolverSB':
            ax.set(ylim=(0,200))
        ax.set(ylabel=ylabels[metric])
        ax.get_figure().savefig('exp2_'+solver+'_'+metric+'.pdf')
        ax.get_figure().clf()

