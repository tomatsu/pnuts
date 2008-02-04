package org.pnuts.lib;

import java.util.*;

class MapEnumeration implements Enumeration {
	private Enumeration en;

	protected MapEnumeration(){
	}

	public MapEnumeration(Enumeration en){
		this.en = en;
	}

	protected Object map(Object obj){
		return obj;
	}

	public boolean hasMoreElements(){
		return en.hasMoreElements();
	}

	public Object nextElement(){
		return map(en.nextElement());
	}
}
