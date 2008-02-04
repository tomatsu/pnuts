package org.pnuts.lib;

import pnuts.lang.Context;
import pnuts.lang.PnutsFunction;

public class isArray extends PnutsFunction {

	public isArray(){
		super("isArray");
	}

	public boolean defined(int narg){
		return (narg == 1);
	}

	protected Object exec(Object[] args, Context context){
		if (args.length == 1){
			Object obj = args[0];
			return new Boolean(obj instanceof Object[] ||
							   obj instanceof int[] ||
							   obj instanceof char[] ||
							   obj instanceof boolean[] ||
							   obj instanceof byte[] ||
							   obj instanceof double[] ||
							   obj instanceof long[] ||
							   obj instanceof short[] ||
							   obj instanceof float[]);
		} else {
			undefined(args, context);
			return null;
		}
	}

	public String toString(){
		return "function isArray(obj)";
	}
}
