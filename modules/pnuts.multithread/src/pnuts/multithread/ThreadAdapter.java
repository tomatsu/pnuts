package pnuts.multithread;

import pnuts.lang.PnutsFunction;
import pnuts.lang.Context;
import pnuts.lang.Escape;

/**
 * An adapter class between PnutsFunction and Runnable
 */
public class ThreadAdapter implements Runnable {
	PnutsFunction func;
	Context context;
	
	public ThreadAdapter(PnutsFunction func){
		this(func, new Context());
	}

	public ThreadAdapter(PnutsFunction func, Context context){
		this.func = func;
		this.context = context;
	}

	public void run(){
		try {
			func.call(new Object[]{}, new Context(context));
		} catch (Escape e){
			// skip
		}
	}
}
