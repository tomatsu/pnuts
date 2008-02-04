package org.pnuts.lib;

import pnuts.lang.*;
import java.io.*;

public class flush extends PnutsFunction {

	public flush(){
		super("flush");
	}

	public boolean defined(int narg){
		return narg == 0;
	}

	public Object exec(Object[] args, Context context){
		PrintWriter output = context.getWriter();
		if (output == null){
			return null;
		}
		if (args.length == 0){
			output.flush();
		} else {
			undefined(args, context);
		}
		return null;
	}

	public String toString(){
		return "function flush()";
	}
}
