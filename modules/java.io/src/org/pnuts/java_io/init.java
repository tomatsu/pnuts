package org.pnuts.java_io;

import pnuts.lang.Context;
import pnuts.ext.ModuleBase;

/**
 * Initialization of the java.util module
 */
public class init extends ModuleBase {

	static String[] classNames = {
		"File",
		"OutputStream",
		"InputStream",
		"IOException",
		"Reader",
		"Writer"
	};

	public Object execute(Context context){
		for (int i = 0; i < classNames.length; i++){
			autoloadClass("java.io", classNames[i], context);
		}
		return null;
	}
}

