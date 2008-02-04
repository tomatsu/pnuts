package org.pnuts.lib;

import pnuts.lang.PnutsFunction;
import pnuts.lang.Context;
import pnuts.lang.Generator;
import pnuts.lang.Runtime;
import java.io.PrintWriter;
import java.util.Enumeration;

public class printAll extends PnutsFunction {

	public printAll(){
		super("printAll");
	}

	public boolean defined(int nargs){
		return nargs == 1;
	}

	public Object exec(Object[] args, Context context){
		final PrintWriter output = context.getWriter();
		if (output != null){
			Object target = args[0];
			Enumeration e = Runtime.toEnumeration(target, context);
			if (e != null){
				while (e.hasMoreElements()){
					output.println(e.nextElement());
				}
			} else if (target instanceof Generator){
				Generator g = (Generator)target;
				Runtime.applyGenerator(g, new PnutsFunction(){
						protected Object exec(Object[] args, Context ctx){
							output.println(args[0]);
							return null;
						}
					}, context);
			} else {
				throw new IllegalArgumentException(String.valueOf(target));
			}
		}
		return null;
	}

	public String toString(){
		return "function printAll(arg)";
	}
}
