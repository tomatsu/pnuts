package org.pnuts.lib;

import pnuts.lang.*;
import java.io.*;

public class print extends PnutsFunction {

	public print(){
		super("print");
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
		}
		return null;
	}

	public String toString(){
		return "function print(args[])";
	}
}
