package org.pnuts.math;

import pnuts.lang.PnutsFunction;
import pnuts.lang.Context;

public class log extends PnutsFunction {

	public log(){
		super("log");
	}

	public boolean defined(int nargs){
		return nargs == 1;
	}

	protected Object exec(Object[] args, Context context){
		if (args.length != 1){
			undefined(args, context);
			return null;
		}
		return new Double(Math.log(((Number)args[0]).doubleValue()));
	}

	public String toString(){
		return "function log(number)";
	}
}
