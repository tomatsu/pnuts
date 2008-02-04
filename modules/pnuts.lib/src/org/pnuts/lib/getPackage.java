package org.pnuts.lib;

import pnuts.lang.Context;
import pnuts.lang.Package;
import pnuts.lang.PnutsFunction;

public class getPackage extends PnutsFunction {

	public getPackage(){
		super("getPackage");
	}

	public boolean defined(int narg){
		return narg == 1;
	}

	protected Object exec(Object[] args, Context context){
		if (args.length == 1){
			return Package.getPackage((String)args[0], context);
		} else {
			undefined(args, context);
			return null;
		}
	}

	public String toString(){
		return "getPackage(packageName)";
	}
}
