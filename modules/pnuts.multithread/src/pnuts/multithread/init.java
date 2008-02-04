package pnuts.multithread;

import org.pnuts.multithread._threadPool;
import pnuts.lang.Package;
import pnuts.lang.Context;
import pnuts.ext.ModuleBase;

/**
 * Initialization of the pnuts.multithread module
 */
public class init extends ModuleBase {

	static String[] files  = {
		"pnuts/multithread/thread",
		"pnuts/multithread/threadGroup",
	};

	static String[][] functions = {
		// pnuts/multithread/thread
		{
			"getPriority",
			"setPriority",
			"sleep",
			"mutex"
		},
		{
			"threadGroup"
		}
	};

	static String[] javaFunctions = {
		"async",
		"currentThread",
		"sync",
		"threadLocal",
		"fork",
		"createThread"
	};

	static String[] requiredModules  = {
	    "pnuts.lib"
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
		Package pkg = getPackage(context);
		String THREADPOOL = "threadPool".intern();
		pkg.set(THREADPOOL, new _threadPool(), context);
		pkg.export(THREADPOOL);
		return null;
	}
}
