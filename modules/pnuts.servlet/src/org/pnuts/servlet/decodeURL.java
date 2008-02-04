package org.pnuts.servlet;

import pnuts.servlet.*;
import pnuts.lang.*;
import java.io.*;
import javax.servlet.*;
import javax.servlet.http.*;
import org.pnuts.net.URLEncoding;

/*
 * decodeURL(str {, encoding})
 */
public class decodeURL extends PnutsFunction {

	public decodeURL(){
		super("decodeURL");
	}

	public boolean defined(int narg){
		return (narg == 1 || narg == 2);
	}

	protected Object exec(Object[] args, Context context){
		int nargs = args.length;
		String str;
		String encoding;
		if (nargs == 1){
			str = (String)args[0];
			encoding = ServletEncoding.getDefaultInputEncoding(context);
		} else if (nargs == 2){
			str = (String)args[0];
			encoding = (String)args[1];
		} else {
			undefined(args, context);
			return null;
		}
		try {
			if (str == null){
				return "";
			} else {
				return URLEncoding.decode(str, encoding);
			}
		} catch (UnsupportedEncodingException e){
			throw new PnutsException(e, context);
		}
	}

	public String toString(){
		return "function decodeURL(str {, encoding})";
	}
}
