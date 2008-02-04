package org.pnuts.lib;

import java.util.Calendar;
import java.util.Date;
import pnuts.lang.PnutsFunction;
import pnuts.lang.Context;

class DateElementAdd extends PnutsFunction {
	private int element;

	DateElementAdd(int element, String name){
		super(name);
		this.element = element;
	}

	public boolean defined(int nargs){
		return nargs == 2;
	}

	protected Object exec(Object[] args, Context context){
		if (args.length != 2){
			undefined(args, context);
			return null;
		}
		Date d = (Date)args[0];
		int n = ((Number)args[1]).intValue();
		Calendar c = date.getCalendar(context);
		synchronized (c){
			c.setTime(d);
			c.add(element, n);
			return c.getTime();
		}
	}

	public String toString(){
		return "function " + name + "(Date, int)";
	}
}
