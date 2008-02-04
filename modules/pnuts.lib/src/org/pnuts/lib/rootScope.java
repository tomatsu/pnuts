package org.pnuts.lib;

import pnuts.lang.PnutsFunction;
import pnuts.lang.Package;
import pnuts.lang.Context;

public class rootScope extends PnutsFunction {

	public rootScope(){
		super("rootScope");
	}

	public boolean defined(int narg){
		return (narg == 0);
	}

	protected Object exec(Object args[], Context context){
		if (args.length != 0){
			undefined(args, context);
			return null;
		}
		Package p = context.getCurrentPackage();
		Package parent = p.getParent();
		while (parent != null){
			p = parent;
			parent = p.getParent();
		}
		return p;
	}

	public String toString(){
		return "function rootScope()";
	}
}
