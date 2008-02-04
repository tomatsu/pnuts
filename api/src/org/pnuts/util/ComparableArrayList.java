package org.pnuts.util;

import java.util.*;
import pnuts.lang.Runtime;

public class ComparableArrayList extends ArrayList implements Comparable {

	public int compareTo(Object o) {
		return Runtime.compareObjects(this, o);
	}
}
