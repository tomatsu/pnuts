package org.pnuts.lib;

import pnuts.lang.*;

public class size extends PnutsFunction {

	private static Counter counter;
	private static String stringRepresentation;
	static {
		try {
			Class.forName("java.lang.CharSequence");
			counter = new MerlinCounter();
			stringRepresentation =  "function size(CharSequence|File|Collection|array";
		} catch (Exception e){
			counter = new Counter();
			stringRepresentation =  "function size(String|File|Collection|array)";
		}
	}

	public size(){
		super("size");
	}

	public boolean defined(int nargs){
		return nargs == 1;
	}

	protected Object exec(Object[] args, Context context){
		if (args.length != 1){
			undefined(args, context);
			return null;
		}
		Object a = args[0];
		return counter.count(a, context);
	}

	public String toString(){
		return stringRepresentation;
	}
}
