package org.pnuts.lib;

import pnuts.lang.Context;
import pnuts.lang.PnutsFunction;
import java.util.TimeZone;

public class getTimeZone extends PnutsFunction {

	private final static String TIMEZONE = "pnuts$lib$timezone".intern();

	public getTimeZone(){
		super("getTimeZone");
	}

	public boolean defined(int nargs){
		return nargs == 0 || nargs == 1;
	}
	
	protected Object exec(Object[] args, Context context){
		if (args.length == 0){
			TimeZone zone = (TimeZone)context.get(TIMEZONE);
			if (zone == null){
				context.set(TIMEZONE, zone = TimeZone.getDefault());
			}
			return zone;
		} else if (args.length == 1){
			return TimeZone.getTimeZone((String)args[0]);
		} else {
			undefined(args, context);
			return null;
		}
	}

	public String toString(){
		return "function getTimeZone( { id } )";
	}
}
