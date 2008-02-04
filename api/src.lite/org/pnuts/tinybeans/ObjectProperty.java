package org.pnuts.tinybeans;

import java.lang.reflect.*;

class ObjectProperty {
	private String name;
	private boolean hasCanonicalName;
	Method r;
	Method w;
	Class type;

	ObjectProperty(String name, Class type, Method r, Method w, boolean hasCanonicalName){
	    this.name = name;
	    this.r = r;
	    this.w = w;
	    this.type = type;
	    this.hasCanonicalName = hasCanonicalName;
	}

	public String getName(){
	    return name;
	}

	public Class getType(){
	    return type;
	}

	public Method getReadMethod(){
	    return r;
	}

	public Method getWriteMethod(){
	    return w;
	}

	boolean hasCanonicalName(){
	    return hasCanonicalName;
	}
}
