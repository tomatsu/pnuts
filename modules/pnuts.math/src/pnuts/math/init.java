package pnuts.math;

import pnuts.lang.Context;
import pnuts.ext.ModuleBase;

/**
 * Initialization of the pnuts.math module.
 */
public class init extends ModuleBase {

	static String[] javaFunctions = {
		"sin",
		"cos",
		"tan",
		"asin",
		"acos",
		"atan",
		"atan2",
		"exp",
		"log",
		"sqrt",
		"round",
		"pow",
		"ceil",
		"floor",
		"rint",
		"toRadians",
		"toDegrees",
		"IEEEremainder",
		"max",
		"min",
		"abs"
	};

	protected String getPrefix(){
		return "org";
	}

	public Object execute(Context context){
		context.clearPackages();
		for (int i = 0; i < javaFunctions.length; i++){
			autoloadFunction(javaFunctions[i], context);
		}
		return null;
	}
}
