package org.pnuts.beans;

class ByteCodeLoader extends ClassLoader {

	ByteCodeLoader(){
	}

	ByteCodeLoader(ClassLoader parent){
		super(parent);
	}

	Class define(String cname, byte[] bytecode, int offset, int size){
		Class c = defineClass(cname, bytecode, offset, size);
		resolveClass(c);
		return c;
	}
}
