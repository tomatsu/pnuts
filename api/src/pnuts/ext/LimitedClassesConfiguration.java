package pnuts.ext;

import pnuts.lang.*;
import java.util.*;
import java.lang.reflect.*;

public class LimitedClassesConfiguration extends ConfigurationAdapter {

	private HashSet set = new HashSet();

	public LimitedClassesConfiguration() {
	}

	public LimitedClassesConfiguration(Configuration base) {
		super(base);
	}

	public void registerClass(Class cls) {
		set.add(cls);
	}

	public Method[] getMethods(Class cls) {
		if (set.contains(cls)) {
			return super.getMethods(cls);
		} else {
			return new Method[] {};
		}
	}

	public Constructor[] getConstructors(Class cls) {
		if (set.contains(cls)) {
			return super.getConstructors(cls);
		} else {
			return new Constructor[] {};
		}
	}
}