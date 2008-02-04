package pnuts.net;

import pnuts.lang.Context;
import pnuts.lang.Package;
import pnuts.ext.ModuleBase;
import org.pnuts.net.*;

/**
 * Initialization of the pnuts.lib module.
 */
public class init extends ModuleBase {

	static String[] files  = {
		"pnuts/net/inet"
	};

	static String[][] functions = {
		{
			"isLocalHost"
		}
	};

	static String[] javaFunctions = {
		"getLocalHost",
		"getInetAddress",
		"decodeURL",
		"encodeURL",
		"makeQueryString"
	};

	protected String getPrefix(){
		return "org";
	}

	public Object execute(Context context){
		context.clearPackages();
		for (int i = 0; i < files.length; i++){
			autoload(functions[i], files[i], context);
		}
		for (int i = 0; i < javaFunctions.length; i++){
			autoloadFunction(javaFunctions[i], context);
		}
		return null;
	}
}
