package org.pnuts.beans;

import pnuts.lang.*;
import pnuts.lang.Runtime;

/*
 * getBeanProperty(bean, property_name)
 */
public class getBeanProperty extends PnutsFunction {

	public getBeanProperty(){
		super("getBeanProperty");
	}

	public boolean defined(int narg){
		return (narg == 2);
	}

	protected Object exec(Object[] args, Context context){
		int nargs = args.length;
		if (nargs == 2){
			Object bean = args[0];
			String property = (String)args[1];
			return Runtime.getBeanProperty(context, bean, property);
		} else {
			undefined(args, context);
			return null;
		}
	}

	public String toString(){
		return "function getBeanProperty(bean, property_name)";
	}
}
