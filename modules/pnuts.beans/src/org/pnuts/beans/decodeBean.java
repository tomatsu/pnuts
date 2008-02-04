package org.pnuts.beans;

import pnuts.lang.*;
import java.beans.*;
import java.io.*;

/*
 * decodeBean(inputStream)
 */
public class decodeBean extends PnutsFunction {

	public decodeBean(){
		super("decodeBean");
	}

	public boolean defined(int nargs){
		return nargs == 1;
	}

	protected Object exec(Object[] args, Context context){
		int nargs = args.length;
		if (nargs == 1){
			InputStream input = (InputStream)args[0];
			XMLDecoder dec = new XMLDecoder(input);
			return dec.readObject();
		} else {
			undefined(args, context);
			return null;
		}
	}

	public String toString(){
		return "function decodeBean(inputStream)";
	}
}
