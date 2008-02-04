package org.pnuts.io;

import java.io.OutputStream;
import java.io.DataOutputStream;
import pnuts.lang.Context;
import pnuts.lang.PnutsFunction;

public class dataOutput extends PnutsFunction {

	public dataOutput(){
		super("dataOutput");
	}

	public boolean defined(int narg){
		return (narg == 1);
	}

	protected Object exec(Object[] args, Context context){
		if (args.length == 1){
			return new DataOutputStream((OutputStream)args[0]);
		} else {
			undefined(args, context);
			return null;
		}
	}

	public String toString(){
		return "function dataOutput(OutputStream)";
	}
}
