package org.pnuts.io;

import pnuts.io.CharacterEncoding;
import pnuts.lang.Context;
import pnuts.lang.PnutsFunction;

public class getCharacterEncoding extends PnutsFunction {

	public getCharacterEncoding(){
		super("getCharacterEncoding");
	}

	public boolean defined(int narg){
		return narg == 0;
	}

	protected Object exec(Object[] args, Context context){
		if (args.length == 0){
			return CharacterEncoding.getCharacterEncoding(context);
		} else {
			undefined(args, context);
			return null;
		}
	}

	public String toString(){
		return "function getCharacterEncoding()";
	}
}
