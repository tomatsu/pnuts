package org.pnuts.lib;

import pnuts.lang.Context;
import pnuts.lang.PnutsFunction;
import pnuts.compiler.Compiler;

public class isCompiled extends PnutsFunction {

	public isCompiled(){
		super("isCompiled");
	}

	public boolean defined(int nargs){
		return nargs == 1;
	}

	protected Object exec(Object[] args, Context context){
		if (args.length != 1){
			undefined(args, context);
			return null;
		}
		if (Compiler.isCompiled(args[0])){
			return Boolean.TRUE;
		} else {
			return Boolean.FALSE;
		}
	}

	public String toString(){
		return "function isCompiled(target)";
	}
}
