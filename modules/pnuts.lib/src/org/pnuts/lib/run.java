package org.pnuts.lib;

import pnuts.lang.*;

public class run extends PnutsFunction {

	public run(){
		super("run");
	}

	public boolean defined(int nargs){
		return nargs == 1;
	}

	protected Object exec(Object args[], Context context){
		if (args.length == 1){
			return ((Executable)args[0]).run(context);
		} else {
			undefined(args, context);
			return null;
		}
	}

	public String toString(){
		return "function run(executable)";
	}
}
