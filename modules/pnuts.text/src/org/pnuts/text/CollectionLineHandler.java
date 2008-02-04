package org.pnuts.text;

import java.util.*;

class CollectionLineHandler implements LineHandler {
	Collection col;

	CollectionLineHandler(Collection col){
		this.col = col;
	}

	public void process(char[] c, int offset, int length){
		col.add(new String(c, offset, length));
	}
}
