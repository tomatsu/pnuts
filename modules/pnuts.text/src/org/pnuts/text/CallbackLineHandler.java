package org.pnuts.text;

import pnuts.lang.*;
import java.lang.reflect.*;

/**
 * A LineHandler implementation that calls a PnutsFunction.
 */
class CallbackLineHandler implements LineHandler {

	protected PnutsFunction func;
	protected Context context;
	protected AbstractLineReader lineReader;

	public CallbackLineHandler(PnutsFunction func, Context context){
		this.func = func;
		this.context = context;
	}

	public void process(char[] cb, int offset, int length){
		func.call(new Object[]{new String(cb, offset, length)}, context);
	}
}
