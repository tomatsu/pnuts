package org.pnuts.lib;

import pnuts.lang.*;
import java.util.*;

/*
 * function shift(linkedList)
 */
public class shift extends PnutsFunction {

	public shift(){
		super("shift");
	}

	public boolean defined(int nargs){
		return nargs == 1;
	}

	protected Object exec(Object[] args, Context context){
		if (args.length != 1){
			undefined(args, context);
			return null;
		}
		return ((LinkedList)args[0]).removeFirst();
	}

	public String toString(){
		return "function shift(linkedList)";
	}
}
