package org.pnuts.lib;

import pnuts.lang.PnutsFunction;
import pnuts.lang.Context;
import pnuts.lang.Pnuts;

public class isJava2 extends PnutsFunction {

	public isJava2(){
		super("isJava2");
	}

	public boolean defined(int nargs){
		return nargs == 0;
	}

	protected Object exec(Object args[], Context context){
		if (args.length == 0){
			if (Pnuts.isJava2()){
				return Boolean.TRUE;
			} else {
				return Boolean.FALSE;
			}
		} else {
			undefined(args, context);
			return null;
		}
	}
	public String toString(){
		return "function isJava2()";
	}
}
