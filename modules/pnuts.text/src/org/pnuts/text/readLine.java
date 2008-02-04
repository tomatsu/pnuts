package org.pnuts.text;

import pnuts.lang.*;
import pnuts.io.CharacterEncoding;
import java.io.*;

/**
 * readLine(Reader/InputStream)
 */
public class readLine extends PnutsFunction {

	public readLine(){
		super("readLine");
	}

	public boolean defined(int narg){
		return narg == 1;
	}

	protected Object exec(Object args[], Context context){
		if (args.length != 1){
			undefined(args, context);
			return null;
		}
		Object arg = args[0];
		try {
			if (arg instanceof BufferedReader){
				return ((BufferedReader)arg).readLine();
			} else if (arg instanceof Reader){
				BufferedReader br = new BufferedReader((Reader)arg);
				return br.readLine();
			} else if (arg instanceof InputStream){
				BufferedReader reader = new BufferedReader(CharacterEncoding.getReader((InputStream)arg, context));
				return reader.readLine();
			} else {
				throw new IllegalArgumentException();
			}
		} catch (IOException e){
			throw new PnutsException(e, context);
		}
	}

	public String toString(){
		return "function readLine(Reader/InputStream)";
	}
}
