package org.pnuts.util;

import pnuts.lang.*;
import java.lang.Package;
import java.io.*;

/*
 * function versionInfo(class|moduleName)
 */
public class versionInfo extends PnutsFunction {

	public versionInfo(){
		super("versionInfo");
	}

	public boolean defined(int narg){
		return (narg == 1);
	}

	public Object exec(Object[] args, Context context){
		int nargs = args.length;
		if (nargs != 1){
			undefined(args, context);
			return null;
		}
		Object arg = args[0];
		Class cls = null;
		if (arg instanceof String){
			String name = (String)arg;
			String cname = name.replace('/', '.').replace('-', '_');
			try {
				cls = Pnuts.loadClass(cname + ".init", context);
			} catch (ClassNotFoundException e){
				return null;
			}
		} else if (arg instanceof Class){
			cls = (Class)arg;
		} else {
			return null;
		}
		return cls.getPackage();
	}

	public String toString(){
		return "function versionInfo(moduleName)";
	}
}
