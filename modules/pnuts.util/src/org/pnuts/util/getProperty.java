package org.pnuts.util;

import pnuts.lang.PnutsFunction;
import pnuts.lang.Context;

/*
 * function getProperty(propertyName)
 */
public class getProperty extends PnutsFunction {

	public getProperty(){
		super("getProperty");
	}

	public boolean defined(int nargs){
		return nargs == 1;
	}

	protected Object exec(Object[] args, Context context){
		if (args.length != 1){
			undefined(args, context);
			return null;
		}
		return System.getProperty((String)args[0]);
	}

	public String toString(){
		return "function getProperty(propertyName)";
	}
}
