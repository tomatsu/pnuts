package org.pnuts.lib;

import pnuts.lang.*;

public class exit extends PnutsFunction {

	public exit(){
		super("exit");
	}

	public boolean defined(int narg){
		return (narg == 0 || narg == 1);
	}

	protected Object exec(Object args[], Context context){
		int nargs = args.length;
		if (nargs > 1){
			undefined(args, context);
			return null;
		}
		int exitStatus = 0;
		if (nargs == 1){
			exitStatus = ((Integer)args[0]).intValue();
		}
		System.exit(exitStatus);
		return null;
	}

	public String toString(){
		return "function exit( { exitCode } )";
	}
}
