package org.pnuts.lib;

import java.net.URL;
import java.net.URLClassLoader;
import pnuts.lang.PnutsFunction;
import pnuts.lang.Context;

public class getClassPath extends PnutsFunction {

	public getClassPath(){
		super("getClassPath");
	}

	public boolean defined(int nargs){
		return nargs == 0;
	}

	static URL[] getURLs(Context context){
		ClassLoader loader = context.getClassLoader();
		if (loader == null){
			loader = Thread.currentThread().getContextClassLoader();
		}
		if (loader instanceof URLClassLoader){
			return ((URLClassLoader)loader).getURLs();
		} else {
			return null;
		}
	}

	protected Object exec(Object[] args, Context context){
		if (args.length != 0){
			undefined(args, context);
			return null;
		}
		return getURLs(context);
	}

	public String toString(){
		return "function getClassPath()";
	}
}
