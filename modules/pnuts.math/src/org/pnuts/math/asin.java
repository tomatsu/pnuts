package org.pnuts.math;

import pnuts.lang.PnutsFunction;
import pnuts.lang.Context;

public class asin extends PnutsFunction {

	public asin(){
		super("asin");
	}

	public boolean defined(int nargs){
		return nargs == 1;
	}

	protected Object exec(Object[] args, Context context){
		if (args.length != 1){
			undefined(args, context);
			return null;
		}
		return new Double(Math.asin(((Number)args[0]).doubleValue()));
	}

	public String toString(){
		return "function asin(number)";
	}
}
