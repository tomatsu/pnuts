package org.pnuts.lib;

import pnuts.lang.*;
import pnuts.lang.Runtime;
import java.io.*;
import java.util.*;
import java.lang.reflect.*;

public class count extends PnutsFunction {

	private static Counter counter;
	private static String stringRepresentation;
	static {
		try {
			Class.forName("java.lang.CharSequence");
			counter = new MerlinCounter();
			stringRepresentation =  "function count(InputStream|Reader|CharSequence|File|Collection|array|Enumeration|Iterator|Generator)";
		} catch (Exception e){
			counter = new Counter();
			stringRepresentation =  "function count(InputStream|Reader|String|File|Collection|array|Enumeration|Iterator|Generator)";
		}
	}

	public count(){
		super("count");
	}

	public boolean defined(int nargs){
		return nargs == 1;
	}

	protected Object exec(Object[] args, Context context){
		if (args.length != 1){
			undefined(args, context);
			return null;
		}
		Object a = args[0];
		return counter.count(a, context);
	}

	public String toString(){
		return stringRepresentation;
	}
}
