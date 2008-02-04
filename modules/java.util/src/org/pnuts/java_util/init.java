package org.pnuts.java_util;

import pnuts.lang.Context;
import pnuts.ext.ModuleBase;

/**
 * Initialization of the java.util module
 */
public class init extends ModuleBase {

	static String[] classNames = {
		"Date",
		"BitSet",
		"Calendar",
		"Hashtable",
		"Vector",
		"Stack",
		"Map",
		"List",
		"Set",
		"Collection",
		"Collections",
		"Arrays",
		"Random",
		"Locale",
		"TimeZone",
		"Enumeration",
		"Iterator"
	};

	public Object execute(Context context){
		for (int i = 0; i < classNames.length; i++){
			autoloadClass("java.util", classNames[i], context);
		}
		return null;
	}
}

