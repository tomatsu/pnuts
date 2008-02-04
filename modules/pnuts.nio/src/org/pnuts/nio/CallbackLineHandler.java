package org.pnuts.nio;

import pnuts.lang.*;
import org.pnuts.text.*;
import java.nio.*;

class CallbackLineHandler implements LineHandler {

	protected PnutsFunction func;
	protected Context context;

	public CallbackLineHandler(PnutsFunction func, Context context) {
		this.func = func;
		this.context = context;
	}

	public void process(char[] cb, int offset, int length){
		func.call(new Object[]{CharBuffer.wrap(cb, offset, length)}, context);
	}

	public void process(byte[] bb, int offset, int length){
		func.call(new Object[]{new ByteArrayCharSequence(bb, offset, length)}, context);
	}
}
