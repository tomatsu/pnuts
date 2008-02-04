package org.pnuts.xml;

import pnuts.lang.*;
import pnuts.xml.XMLConfiguration;

/*
 * function nodeAccess({boolean})
 */
public class nodeAccess extends PnutsFunction {

	public nodeAccess(){
		super("nodeAccess");
	}

	public boolean defined(int nargs){
		return (nargs == 0 || nargs == 1);
	}

	protected Object exec(Object[] args, Context context){
		int nargs = args.length;
		Configuration current = context.getConfiguration();
		if (nargs == 0){
			return (current instanceof XMLConfiguration) ? Boolean.TRUE : Boolean.FALSE;
		} else if (nargs == 1){
			boolean enabled = ((Boolean)args[0]).booleanValue();
			if (enabled){
				if (!(current instanceof XMLConfiguration)){
					context.setConfiguration(new XMLConfiguration(current));
				}
			} else {
				if (current instanceof XMLConfiguration){
					context.setConfiguration(((XMLConfiguration)current).getParent());
				}
			}
			return null;
		} else {
			undefined(args, context);
			return null;
		}
	}

	public String toString(){
		return "function nodeAccess( { boolean } )";
	}
}
