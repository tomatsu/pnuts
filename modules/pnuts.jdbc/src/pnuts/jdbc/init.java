package pnuts.jdbc;

import pnuts.lang.Context;
import pnuts.ext.ModuleBase;

/**
 * Initialization of the pnuts.math module.
 */
public class init extends ModuleBase {

	static String[] files  = {
		"pnuts/jdbc/functions"
	};

	static String[][] functions = {
		// pnuts/jdo/functions
		{
			"openJDBC"
		},

	};

	static String[] requiredModules  = {
	    "pnuts.lib",
	    "pnuts.util",
	    "pnuts.io",
	    "pnuts.text"
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
