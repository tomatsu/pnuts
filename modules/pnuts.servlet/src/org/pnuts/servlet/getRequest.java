package org.pnuts.servlet;

import pnuts.servlet.*;
import pnuts.lang.*;
import javax.servlet.*;

/*
 * function getRequest()
 */
public class getRequest extends PnutsFunction {

	public getRequest(){
		super("getRequest");
	}

	public boolean defined(int nargs){
		return (nargs == 0);
	}

	protected Object exec(Object args[], Context context){
		int nargs = args.length;
		if (nargs == 0){
			return (ServletRequest)context.get(PnutsServlet.SERVLET_REQUEST);
		} else {
			undefined(args, context);
			return null;
		}
	}

	public String toString(){
		return "function getRequest()";
	}
}
