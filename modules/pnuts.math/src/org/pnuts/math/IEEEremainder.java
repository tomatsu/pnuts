package org.pnuts.math;

import pnuts.lang.PnutsFunction;
import pnuts.lang.Context;

public class IEEEremainder extends PnutsFunction {

	public IEEEremainder(){
		super("IEEEremainder");
	}

	public boolean defined(int nargs){
		return nargs == 2;
	}

	protected Object exec(Object[] args, Context context){
		if (args.length != 2){
			undefined(args, context);
			return null;
		}
		return new Double(Math.IEEEremainder(((Number)args[0]).doubleValue(),
											 ((Number)args[1]).doubleValue()));
	}

	public String toString(){
		return "function IEEEremainder(x, y)";
	}
}
