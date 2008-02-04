package org.pnuts.nio;

import pnuts.lang.*;
import org.pnuts.text.*;
import java.util.*;
import java.nio.*;

class CollectionLineHandler implements LineHandler {

	Collection col;

	public CollectionLineHandler(Collection col) {
		this.col = col;
	}

	public void process(char[] cb, int offset, int length){
		col.add(CharBuffer.wrap(cb, offset, length));
	}

	public void process(byte[] bb, int offset, int length){
		col.add(ByteBuffer.wrap(bb, offset, length));
	}
}
