package org.pnuts.lib;

import pnuts.lang.*;
import java.io.*;

public class println extends PnutsFunction {

	public println(){
		super("println");
	}

	public boolean defined(int narg){
		return true;
	}

	public Object exec(Object[] args, Context context){
		PrintWriter output = context.getWriter();
		if (output != null){
			for (int i = 0; i < args.length; i++){
				output.print(args[i]);
			}
			output.println();
		}
		return null;
	}

	public String toString(){
		return "function println(args[])";
	}
}
