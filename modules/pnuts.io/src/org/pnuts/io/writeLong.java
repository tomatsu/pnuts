package org.pnuts.io;

import pnuts.lang.*;
import java.io.*;

/*
 * writeLong(DataOutput dout, long value)
 * writeLong(DataOutput dout, long[] src, int offset, int len)
 */
public class writeLong extends PnutsFunction {

	public writeLong(){
		super("writeLong");
	}

	public boolean defined(int narg){
		return (narg == 2 || narg == 4);
	}

	protected Object exec(Object[] args, Context context){
		int nargs = args.length;
		try {
			if (nargs == 2){
				DataOutput dout = (DataOutput)args[0];
				long value = ((Long)args[1]).longValue();
				dout.writeLong(value);
				return new Integer(1);
			} else if (nargs == 4){
				DataOutput dout = (DataOutput)args[0];
				long[] src = (long[])args[1];
				int offset = ((Integer)args[2]).intValue();
				int n = ((Integer)args[3]).intValue();
				for (int i = 0; i < n; i++){
					dout.writeLong(src[offset + i]);
				}
				return new Integer(n);
			} else {
				undefined(args, context);
				return null;
			}
		} catch (IOException e){
			throw new PnutsException(e, context);
		}
	}

	public String toString(){
		return "function writeLong(DataOutput dout, {long value | long[] src, int offset, int len } )";
	}
}
