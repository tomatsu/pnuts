package org.pnuts.util;

import pnuts.lang.*;
import pnuts.lang.PublicMemberAccessor;
import pnuts.ext.*;

/*
 * function publicMemberAccess({boolean})
 */
public class publicMemberAccess extends PnutsFunction {

	final static String LAST_CONFIGURATION = "pnuts.util.lastConfiguration".intern();

	public publicMemberAccess(){
		super("publicMemberAccess");
	}

	public boolean defined(int nargs){
		return (nargs == 0 || nargs == 1);
	}

	protected Object exec(Object[] args, Context context){
		int nargs = args.length;
		Configuration current = context.getConfiguration();
		if (nargs == 0){
			return (current instanceof PublicMemberAccessor) ? Boolean.TRUE : Boolean.FALSE;
		} else if (nargs == 1){
			boolean enabled = ((Boolean)args[0]).booleanValue();
			if (enabled){
				if (!(current instanceof PublicMemberAccessor)){
					context.set(LAST_CONFIGURATION, current);
					context.setConfiguration(new PublicMemberAccessor());
				}
			} else {
				if (current instanceof PublicMemberAccessor){
					context.setConfiguration((Configuration)context.get(LAST_CONFIGURATION));
				}
			}
			return null;
		} else {
			undefined(args, context);
			return null;
		}
	}

	public String toString(){
		return "function publicMemberAccess({boolean})";
	}
}
