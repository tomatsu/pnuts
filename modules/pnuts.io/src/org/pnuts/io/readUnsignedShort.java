package org.pnuts.io;

import pnuts.lang.*;
import java.io.*;

/**
 * readUnsignedShort(DataInput)
 * readUnsignedShort(DataInput, int[] data, int offset, int size)
 */
public class readUnsignedShort extends PnutsFunction {

	public readUnsignedShort(){
		super("readUnsignedShort");
	}

	public boolean defined(int narg){
		return (narg == 1 || narg == 4);
	}

	protected Object exec(Object[] args, Context context){
		int nargs = args.length;
		try {
			if (nargs == 1){
				DataInput din = (DataInput)args[0];
				return new Integer(din.readUnsignedShort());
			} else if (nargs == 4){
				DataInput din = (DataInput)args[0];
				int[] dest = (int[])args[1];
				int offset = ((Integer)args[2]).intValue();
				int size = ((Integer)args[3]).intValue();
				for (int i = 0; i < size; i++){
					dest[offset + i] = din.readUnsignedShort();
				}
				return null;
			}
		} catch (IOException e){
			throw new PnutsException(e, context);
		}
		undefined(args, context);
		return null;
	}

	public String toString(){
		return "function readUnsignedShort(DataInput {, int[] data, int offset, int size })";
	}
}
