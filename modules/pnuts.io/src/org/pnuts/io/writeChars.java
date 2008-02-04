package org.pnuts.io;

import pnuts.lang.*;
import java.io.*;

/*
 * writeChars(DataOutput dout, String str)
 */
public class writeChars extends PnutsFunction {

	public writeChars(){
		super("writeChars");
	}

	public boolean defined(int narg){
		return (narg == 2);
	}

	public Object exec(Object[] args, Context context){
		int nargs = args.length;
		try {
			if (nargs == 2){
				DataOutput dout = (DataOutput)args[0];
				String str = (String)args[1];
				dout.writeChars(str);
			} else {
				undefined(args, context);
			}
			return null;
		} catch (IOException e){
			throw new PnutsException(e, context);
		}
	}

	public String toString(){
		return "function writeChars(DataOutput dout, String str)";
	}
}
