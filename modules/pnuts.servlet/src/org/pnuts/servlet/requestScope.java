package org.pnuts.servlet;

import pnuts.servlet.*;
import pnuts.lang.Context;
import pnuts.lang.PnutsFunction;
import pnuts.lang.Package;
import javax.servlet.*;
import javax.servlet.http.*;

/*
 * function requestScope()
 */
public class requestScope extends PnutsFunction {
	public requestScope(){
		super("requestScope");
	}

	public boolean defined(int nargs){
		return (nargs == 0);
	}

	protected Object exec(Object args[], Context context){
		if (args.length != 0){
			undefined(args, context);
			return null;
		}
		Package requestPackage = (Package)context.get(PnutsServlet.REQUEST_SCOPE);
		if (requestPackage == null){
			throw new IllegalStateException();
		}
		return requestPackage;
	}

	public String toString(){
		return "function requestScope()";
	}
}
