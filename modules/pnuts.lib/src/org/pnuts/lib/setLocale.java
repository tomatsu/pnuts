package org.pnuts.lib;

import java.util.Locale;
import pnuts.lang.Context;
import pnuts.lang.PnutsFunction;

public class setLocale extends PnutsFunction {

	public setLocale(){
		super("setLocale");
	}

	public boolean defined(int nargs){
		return nargs == 0 || nargs == 1;
	}
	
	protected Object exec(Object[] args, Context context){
		int nargs = args.length;
		if (nargs == 0){
			locale.setLocale(Locale.getDefault(), context);
		} else if (nargs == 1){
			locale.setLocale(locale.toLocale(args[0]), context);
		} else {
			undefined(args, context);
		}
		return null;
	}

	public String toString(){
		return "function setLocale( { loc } )";
	}
}
