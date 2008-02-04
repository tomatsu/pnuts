package org.pnuts.lib;

import pnuts.lang.PnutsFunction;
import pnuts.lang.Context;

public class currentTimeMillis extends PnutsFunction {

	public currentTimeMillis(){
		super("currentTimeMillis");
	}

	public boolean defined(int nargs){
		return nargs == 0;
	}

	protected Object exec(Object[] args, Context context){
		if (args.length != 0){
			undefined(args, context);
			return null;
		}
		return new Long(System.currentTimeMillis());
	}

	public String toString(){
		return "function currentTimeMillis()";
	}
}
