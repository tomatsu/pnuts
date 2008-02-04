package org.pnuts.net;

import pnuts.lang.Context;
import pnuts.lang.PnutsFunction;
import pnuts.lang.PnutsException;
import java.net.InetAddress;
import java.net.UnknownHostException;

public class getLocalHost extends PnutsFunction {

	public getLocalHost(){
		super("getLocalHost");
	}

	public boolean defined(int nargs){
		return nargs == 0;
	}
	
	protected Object exec(Object[] args, Context context){
		if (args.length == 0){
			try {
				return InetAddress.getLocalHost();
			} catch (UnknownHostException e){
				throw new PnutsException(e, context);
			}
		} else {
			undefined(args, context);
			return null;
		}
	}

	public String toString(){
		return "function getLocalHost()";
	}
}
