package org.pnuts.io;

import pnuts.lang.*;
import java.io.*;

/**
 * readDouble(DataInput)
 * readDouble(DataInput, double[] data, int offset, int size)
 */
public class readDouble extends PnutsFunction {

	public readDouble(){
		super("readDouble");
	}

	public boolean defined(int narg){
		return (narg == 1 || narg == 4);
	}

	protected Object exec(Object[] args, Context context){
		int nargs = args.length;
		try {
			if (nargs == 1){
				DataInput din = (DataInput)args[0];
				return new Double(din.readDouble());
			} else if (nargs == 4){
				DataInput din = (DataInput)args[0];
				double[] dest = (double[])args[1];
				int offset = ((Integer)args[2]).intValue();
				int size = ((Integer)args[3]).intValue();
				for (int i = 0; i < size; i++){
					dest[offset + i] = din.readDouble();
				}
				return null;
			} else {
				undefined(args, context);
				return null;
			}
		} catch (IOException e){
			throw new PnutsException(e, context);
		}
	}

	public String toString(){
		return "function readDouble(DataInput {, double[] data, int offset, int size })";
	}
}
