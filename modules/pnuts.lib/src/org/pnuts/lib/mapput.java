package org.pnuts.lib;

import pnuts.lang.*;
import java.util.*;

public class mapput extends PnutsFunction {

	public mapput(){
		super("mapput");
	}

	public boolean defined(int nargs){
		return nargs == 3;
	}

	protected Object exec(Object[] args, Context context){
		if (args.length != 3){
			undefined(args, context);
		}
		Map map = (Map)args[0];
		return map.put(args[1], args[2]);
	}

	public String toString(){
		return "function mapput(map, key, value)";
	}
}
