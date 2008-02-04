package org.pnuts.io;

import pnuts.io.CharacterEncoding;
import pnuts.lang.Context;
import pnuts.lang.PnutsFunction;

public class setCharacterEncoding extends PnutsFunction {

	public setCharacterEncoding(){
		super("setCharacterEncoding");
	}

	public boolean defined(int narg){
		return (narg == 1 || narg == 2);
	}

	protected Object exec(Object[] args, Context context){
		if (args.length == 1){
			CharacterEncoding.setCharacterEncoding((String)args[0], (String)args[0], context);
		} else if (args.length == 2){
			CharacterEncoding.setCharacterEncoding((String)args[0], (String)args[1], context);
		} else {
			undefined(args, context);
		}
		return null;
	}

	public String toString(){
		return "function setCharacterEncoding(input_encoding {, output_encoding } )";
	}
}
