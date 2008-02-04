package org.pnuts.lib;

import pnuts.lang.Context;
import pnuts.lang.PnutsFunction;
import java.util.TimeZone;

public class setTimeZone extends PnutsFunction {

	public setTimeZone(){
		super("setTimeZone");
	}

	public boolean defined(int nargs){
		return nargs == 1;
	}
	
	protected Object exec(Object[] args, Context context){
		if (args.length == 1){
			Object arg = args[0];
			TimeZone tz;
			if (arg instanceof String){
				tz = TimeZone.getTimeZone((String)arg);
			} else if (arg instanceof TimeZone){
				tz = (TimeZone)arg;
			} else {
				throw new IllegalArgumentException(String.valueOf(arg));
			}
			date.setTimeZone(context, tz);
		} else {
			undefined(args, context);
		}
		return null;
	}

	public String toString(){
		return "function setTimeZone(String|TimeZone)";
	}
}
