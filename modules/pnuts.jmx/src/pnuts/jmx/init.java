package pnuts.jmx;

import pnuts.lang.Runtime;
import pnuts.lang.Package;
import pnuts.lang.Context;
import pnuts.ext.ModuleBase;

/**
 * Initialization of the pnuts.jmx module
 */
public class init extends ModuleBase {

	static String[] javaFunctions = {
	};

	static String[] files  = {
		"pnuts/jmx/functions"
	};

	static String[][] functions = {
		{
			"mbeanConnection",
			"mbeanServer",
			"dynamicMBean"
		}
	};

	static String[] requiredModules  = {
	    "pnuts.lib"
	};

	protected String[] getRequiredModules(){
		return requiredModules;
	}

	public Object execute(Context context){
		for (int i = 0; i < files.length; i++){
			autoload(functions[i], files[i], context);
		}
		return null;
	}
}
