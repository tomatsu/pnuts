package org.pnuts.multithread;

import pnuts.lang.Runtime;
import pnuts.lang.Context;

class PnutsThreadGroup extends ThreadGroup {
	Context context;

	public PnutsThreadGroup(String name) {
		super(name);
	}

	public PnutsThreadGroup(ThreadGroup parent, String name) {
		super(parent, name);
	}

	public PnutsThreadGroup(Context context){
		super("");
		this.context = context;
	}

	public void uncaughtException(Thread t, Throwable e) {
		Runtime.printError(e, context);
	}			
}
