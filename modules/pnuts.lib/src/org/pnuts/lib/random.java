package org.pnuts.lib;

import pnuts.lang.*;
import java.util.Random;

public class random extends PnutsFunction {

	final static String CONTEXT_SYMBOL = "pnuts.lib.random".intern();

	public random(){
		super("random");
	}

	public boolean defined(int narg){
		return (narg == 0 || narg == 1);
	}

	protected Object exec(Object[] args, Context context){
		int nargs = args.length;
		if (nargs == 0){
			Random r = (Random)context.get(CONTEXT_SYMBOL);
			if (r == null){
				context.set(CONTEXT_SYMBOL, r = new Random());
			}
			return new Integer(r.nextInt());
		} else if (nargs == 1){
			return new Integer(((Random)args[0]).nextInt());
		} else {
			undefined(args, context);
			return null;
		}
	}

	public String toString(){
		return "function random( { java.util.Random } )";
	}
}
