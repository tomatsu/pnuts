package org.pnuts.io;

import pnuts.lang.*;
import java.io.*;

public class readUTF extends PnutsFunction {

	public readUTF(){
		super("readUTF");
	}

	public boolean defined(int narg){
		return (narg == 1);
	}

	protected Object exec(Object[] args, Context context){
		try {
			if (args.length == 1){
				return ((DataInput)args[0]).readUTF();
			} else {
				undefined(args, context);
				return null;
			}
		} catch (IOException e){
			throw new PnutsException(e, context);
		}
	}

	public String toString(){
		return "function readUTF(DataInput)";
	}
}
