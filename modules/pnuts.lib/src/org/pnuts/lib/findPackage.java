package org.pnuts.lib;

import pnuts.lang.Context;
import pnuts.lang.Package;
import pnuts.lang.PnutsFunction;

public class findPackage extends PnutsFunction {

	public findPackage(){
		super("findPackage");
	}

	public boolean defined(int narg){
		return narg == 1;
	}

	protected Object exec(Object[] args, Context context){
		if (args.length == 1){
			return Package.find((String)args[0], context);
		} else {
			undefined(args, context);
			return null;
		}
	}

	public String toString(){
		return "findPackage(packageName)";
	}
}
