package org.pnuts.xml;

import pnuts.lang.*;
import org.pnuts.util.*;
import javax.xml.parsers.*;

public class newDocument extends PnutsFunction {

	public newDocument(){
		super("newDocument");
	}

	public boolean defined(int nargs){
		return nargs == 0;
	}

	protected Object exec(Object[] args, Context context){
		if (args.length != 0){
			undefined(args, context);
			return null;
		}
		DocumentBuilder builder = Util.getDocumentBuilder(null, context);
		return builder.newDocument();
	}

	public String toString(){
		return "function newDocument()";
	}
}
