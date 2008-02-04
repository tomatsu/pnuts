package org.pnuts.lib;

import java.util.Comparator;
import pnuts.lang.Runtime;
import pnuts.lang.Context;

/**
 * A Comparator based on Pnuts comparison operators '<', '>', '==', '<=', and '>='.
 */
public class PnutsComparator implements Comparator {

	private boolean reverse;
	private Context context;

	public PnutsComparator(Context context){
		this(context, false);
	}

	/**
	 * @param reverse If true, sort in reverse order.
	 */
	public PnutsComparator(Context context, boolean reverse){
		this.context = context;
		this.reverse = reverse;
	}

	/**
	 * Compares its two arguments for order.
	 */
	public int compare(Object o1, Object o2){
		if (reverse){
			return Runtime.compareTo(o2, o1);
		} else {
			return Runtime.compareTo(o1, o2);
		}
	}
}
