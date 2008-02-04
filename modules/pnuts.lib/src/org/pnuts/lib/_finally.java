package org.pnuts.lib;

import pnuts.lang.*;

/*
 * function finally( { func1,} func2)
 */
public class _finally extends PnutsFunction {

	private final static Object[] noarg = new Object[0];

	public _finally(){
		super("finally");
	}

	public boolean defined(int nargs){
		return nargs == 1 || nargs == 2;
	}

	protected Object exec(final Object args[], Context context){
		int nargs = args.length;
		if (nargs == 1){
			context.setExitHook(new Executable(){
					public Object run(Context ctx){
						return ((PnutsFunction)args[0]).call(noarg, ctx);
					}
				});
			return null;
		} else if (nargs == 2){
			PnutsFunction f1 = (PnutsFunction)args[0];
			PnutsFunction f2 = (PnutsFunction)args[1];
			try {
				return f1.call(noarg, context);
			} finally {
				f2.call(noarg, context);
			}
		} else {
			undefined(args, context);
			return null;
		}
	}

	public String toString(){
		return "function finally( { tryFunc(), } finallyFunc())";
	}
}
