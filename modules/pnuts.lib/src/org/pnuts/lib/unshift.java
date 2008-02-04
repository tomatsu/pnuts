package org.pnuts.lib;

import pnuts.lang.*;
import java.util.*;

/*
 * function unshift(linkedList, elem)
 */
public class unshift extends PnutsFunction {

	public unshift(){
		super("unshift");
	}

	public boolean defined(int nargs){
		return nargs == 2;
	}

	protected Object exec(Object[] args, Context context){
		if (args.length != 2){
			undefined(args, context);
			return null;
		}
		((LinkedList)args[0]).addFirst(args[1]);
		return null;
	}

	public String toString(){
		return "function unshift(linkedList, elem)";
	}
}
