package pnuts.text;

import pnuts.lang.Runtime;
import pnuts.lang.Package;
import pnuts.lang.Context;
import pnuts.lang.PnutsFunction;
import pnuts.ext.ModuleBase;

/**
 * Initialization of the pnuts.text module
 */
public class init extends ModuleBase {

	static String[] files  = {
		"pnuts/text/template"
	};

	static String[][] functions = {
		{
			"template"
		}
	};

	static String[] javaFunctions = {
		"formatTemplate",
		"applyTemplate",
		"textGrab",
		"printf",
		"sprintf",
		"readLines",
		"readLine"
	};

	static String[] requiredModules  = {
	    "pnuts.lib",
	    "pnuts.io",
	    "pnuts.regex"
	};

	protected String[] getRequiredModules(){
		return requiredModules;
	}

	protected String getPrefix(){
		return "org";
	}

	public Object execute(Context context){
		for (int i = 0; i < files.length; i++){
			autoload(functions[i], files[i], context);
		}
		for (int i = 0; i < javaFunctions.length; i++){
			autoloadFunction(javaFunctions[i], context);
		}
		return null;
	}
}
