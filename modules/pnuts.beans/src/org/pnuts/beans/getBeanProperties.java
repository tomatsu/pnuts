package org.pnuts.beans;

import pnuts.lang.*;
import pnuts.lang.Runtime;
import java.beans.*;
import java.util.*;

/*
 * getBeanProperties(bean)
 */
public class getBeanProperties extends PnutsFunction {

	public getBeanProperties(){
		super("getBeanProperties");
	}

	public boolean defined(int narg){
		return (narg == 1);
	}

	protected Object exec(Object[] args, Context context){
		if (args.length != 1){
			undefined(args, context);
			return null;
		}
		Object bean = args[0];
		try {
			BeanInfo info = Introspector.getBeanInfo(bean.getClass());
			PropertyDescriptor[] props = info.getPropertyDescriptors();
			HashMap map = new HashMap();
			for (int i = 0; i < props.length; i++){
				PropertyDescriptor p = props[i];
				if (p.getReadMethod() != null){
					String key = p.getName();
					map.put(key, Runtime.getBeanProperty(context, bean, key));
				}
			}
			return map;
		} catch (IntrospectionException e){
			throw new PnutsException(e, context);
		}
	}

	public String toString(){
		return "function getBeanProperties(bean)";
	}
}
