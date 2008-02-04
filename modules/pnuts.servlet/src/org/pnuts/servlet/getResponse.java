package org.pnuts.servlet;

import pnuts.servlet.*;
import pnuts.lang.*;
import javax.servlet.*;

/*
 * function getResponse()
 */
public class getResponse extends PnutsFunction {

	public getResponse(){
		super("getResponse");
	}

	public boolean defined(int nargs){
		return (nargs == 0);
	}

	protected Object exec(Object args[], Context context){
		int nargs = args.length;
		if (nargs == 0){
			return (ServletResponse)context.get(PnutsServlet.SERVLET_RESPONSE);
		} else {
			undefined(args, context);
			return null;
		}
	}

	public String toString(){
		return "function getResponse()";
	}
}
