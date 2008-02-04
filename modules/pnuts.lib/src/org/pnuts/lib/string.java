package org.pnuts.lib;

import pnuts.lang.*;

/*
 * function string(obj)
 */
public class string extends PnutsFunction {

	public string(){
		super("string");
	}

	public boolean defined(int narg){
		return (narg == 1);
	}

	protected Object exec(Object[] args, Context context){
		if (args.length == 1){
			Object arg = args[0];
			if (arg == null){
				return "";
			} else {
				return String.valueOf(arg);
			}
		} else {
			undefined(args, context);
			return null;
		}
	}

	public String toString(){
		return "function string(obj)";
	}
}
