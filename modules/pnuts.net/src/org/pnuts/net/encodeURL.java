package org.pnuts.net;

import pnuts.lang.*;
import java.io.*;
import java.net.*;

/*
 * encodeURL(str {, encoding})
 */
public class encodeURL extends PnutsFunction {

	public encodeURL(){
		super("encodeURL");
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
			encoding = null;
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
				if (encoding == null){
					return URLEncoder.encode(str);
				} else {
					return URLEncoder.encode(str, encoding);
				}
			}
		} catch (UnsupportedEncodingException e){
			throw new PnutsException(e, context);
		}
	}

	public String toString(){
		return "function encodeURL(str {, encoding})";
	}
}
