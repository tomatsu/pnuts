package pnuts.lang;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringReader;
import java.io.Serializable;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URL;
import java.net.URLConnection;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.text.DateFormat;
import java.text.MessageFormat;
import java.util.Calendar;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.MissingResourceException;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.Iterator;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeEvent;

import org.pnuts.lang.UnparseVisitor;
import org.pnuts.lang.NodeUtil;
import org.pnuts.lang.*;
import org.pnuts.util.Stack;
import org.pnuts.util.*;

/**
 * This class provides runtime supports for Pnuts compiler/interpreter. Most of
 * the methods are protected static, so that only subclasses can access them.
 */
public class Runtime implements Executable {

	private final static boolean DEBUG = false;

	private final static Object[] noarg = new Object[] {};

	protected static final String INT_SYMBOL = "int".intern();

	protected static final String SHORT_SYMBOL = "short".intern();

	protected static final String CHAR_SYMBOL = "char".intern();

	protected static final String BYTE_SYMBOL = "byte".intern();

	protected static final String LONG_SYMBOL = "long".intern();

	protected static final String FLOAT_SYMBOL = "float".intern();

	protected static final String DOUBLE_SYMBOL = "double".intern();

	protected static final String BOOLEAN_SYMBOL = "boolean".intern();

	protected static final String VOID_SYMBOL = "void".intern();

	protected static final String CLONE = "clone".intern();

	protected static final String EXCEPTOIN_FIELD_SYMBOL = "$exception$".intern();

	private final static String DEFAULT_PROPERTY_FILE = "runtime.properties";

	private final static Integer ZERO = new Integer(0);

	protected final static Object[] NO_PARAM = new Object[0];

	private static Properties defaultProperties;

	/**/
	static {
		try {
			AccessController.doPrivileged(new PrivilegedAction(){
					public Object run(){
						InputStream in =
							Runtime.class.getResourceAsStream(DEFAULT_PROPERTY_FILE);
						if (in != null){
							Properties prop = new Properties();
							try{
								prop.load(in);
								defaultProperties = prop;
							} catch (IOException ioe){
								// ignore
							}
						}
						return null;
					}
				});
		} catch (Exception e){
			// ignore
		}
	}
	/**/

	public static boolean getBoolean(final String key) {
	    Boolean b = (Boolean)AccessController.doPrivileged(new PrivilegedAction() {
			       public Object run(){
				       String sval = Runtime.getProperty(key);
				       if (sval != null && sval.equalsIgnoreCase("true")){
					       return Boolean.TRUE;
				       } else {
					       return Boolean.FALSE;
				       }
			       }
		});
		return b.booleanValue();
	}

	protected Runtime() {
	}

	public static Runtime getDefaultRuntime(){
	    if (getBoolean("pnuts.compiler.useDynamicProxy")){
		return new pnuts.compiler.DynamicRuntime();
	    } else {
		return new Runtime();
	    }
	}

	/**
	 * Executes a compiled script. Exceptions are checked if an exception
	 * handler is registered with the catch() function. Output stream of the
	 * specified context is flushed after the script is executed or an exception
	 * is thrown.
	 * 
	 * @param context
	 *            the context in which this object is executed
	 * @return the result of the execution
	 */
	public Object run(Context context) {
	    if (DEBUG){
		System.out.println("runtime is " + this.getClass().getSuperclass());
	    }
		context.runtime = this;
		try {
			return exec(context);
		} catch (Throwable t) {
			checkException(context, t);
			return null;
		} finally {
			PrintWriter pw = context.getWriter();
			if (pw != null) {
				pw.flush();
			}
		}
	}

	/**
	 * Executes a compiled script. Exceptions are checked if an exception
	 * handler is registered with the catch() function. Output stream of the
	 * specified context is flushed after the script is executed or an exception
	 * is thrown.
	 * 
	 * @param context
	 *            the context in which this object is executed
	 * @return the result of the execution
	 * 
	 * @deprecated replaced by run(Context)
	 */
	public Object execute(Context context) {
		return run(context);
	}

	/**
	 * This method is overrided by classes generated by the compiler.
	 * 
	 * @param context
	 *            the context in which this object is executed
	 * @return the result of the execution
	 */
	protected Object exec(Context context) {
		return null;
	}

	/**
	 * Call a method
	 * 
	 * @param context
	 *            the context in which the method is called
	 * @param c
	 *            the class of method
	 * @param name
	 *            the method name
	 * @param args
	 *            the paramters
	 * @param types
	 *            the types of the paramters
	 * @param target
	 *            the target object
	 * @return the return value of the call
	 */
	public static Object callMethod(Context context, Class c, String name,
			Object args[], Class types[], Object target) {
		return context.config.callMethod(context, c, name, args, types, target);
	}

	/**
	 * Call a constructor
	 * 
	 * @param context
	 *            the context in which the constructor is called
	 * @param c
	 *            the class of method
	 * @param args
	 *            the paramters
	 * @param types
	 *            the types of the formal arguments
	 * @return the created instance
	 */
	public static Object callConstructor(Context context, Class c,
			Object args[], Class types[]) {
		return context.config.callConstructor(context, c, args, types);
	}

	/**
	 * Call a method
	 * 
	 * @param context
	 *            the context
	 * @param c
	 *            the class of method
	 * @param name
	 *            the method name
	 * @param args
	 *            the paramters
	 * @param types
	 *            the types of the formal arguments
	 * @param target
	 *            the target object
	 * @return the return value
	 */
	protected Object _callMethod(Context context, Class c, String name,
			Object args[], Class types[], Object target) {
		Method method = null;
		boolean _static = (target == null);

		if (name == CLONE && args.length == 0) {
			if (target instanceof Object[]) {
				return ((Object[]) target).clone();
			} else if (target instanceof int[]) {
				return ((int[]) target).clone();
			} else if (target instanceof byte[]) {
				return ((byte[]) target).clone();
			} else if (target instanceof short[]) {
				return ((short[]) target).clone();
			} else if (target instanceof char[]) {
				return ((char[]) target).clone();
			} else if (target instanceof long[]) {
				return ((long[]) target).clone();
			} else if (target instanceof float[]) {
				return ((float[]) target).clone();
			} else if (target instanceof double[]) {
				return ((double[]) target).clone();
			} else if (target instanceof boolean[]) {
				return ((boolean[]) target).clone();
			}
		}
		Method m[] = context.config._getMethods(c, name);

		try {
			int count = 0;
			int min = Integer.MAX_VALUE;
			Stack methods = new Stack();
			cand: for (int i = 0; i < m.length; i++) {
				Method mi = m[i];
				Class p[] = mi.getParameterTypes();
				if (p.length != args.length) {
					continue;
				}
				count = 0;
				for (int j = 0; j < p.length; j++) {
					Class pj = p[j];
					if (types != null) {
						Class tj = types[j];
						if (tj != null && pj != tj && !pj.isAssignableFrom(tj)) {
							continue cand;
						}
					}
					int t = matchType(pj, args[j]);
					if (t < 0) {
						continue cand;
					}
					count += t;
				}
				if (count > min) {
					continue;
				}

				boolean st = Modifier.isStatic(mi.getModifiers());
				if (st != _static) {
					continue;
				}

				if (count < min) {
					methods.removeAllElements();
					methods.push(mi);
					min = count;
				} else if (count == min) {
					methods.push(mi);
				}
			}
			Class clazz = c;
			out: while (clazz != null) {
				int size = methods.size();
				for (int i = 0; i < size; i++) {
					method = (Method) methods.pop();
					if (!_static && method.getDeclaringClass() == clazz) {
						break out;
					}
				}
				clazz = clazz.getSuperclass();
			}

			if (method != null) {
				if (args.length > 0) {
					Class p[] = method.getParameterTypes();
					for (int j = 0; j < p.length; j++) {
						Class pj = p[j];
						if (args[j] != null &&
						    isArray(args[j]) &&
						    (pj.isArray() || List.class.isAssignableFrom(pj)))
						{
							if (!pj.isInstance(args[j])) {
								args[j] = transform(pj, args[j], context);
							}
						}
					}
				}
				try {
					return method.invoke(target, args);
				} catch (InvocationTargetException ita) {
					Throwable t = ita.getTargetException();
					if (t instanceof PnutsException){
						throw (PnutsException)t;
					} else {
						throw new PnutsException(t, context);
					}
				}
			} else {
				if (target instanceof Class) {
					Class cls = (Class) target;
					return _callMethod(context, (Class) target, name, args,
							types, null);
				}

				throw new PnutsException("method.notFound", new Object[] {
						name, "" + (target == null ? c : target),
						"" + Pnuts.format(args) }, context);
			}
		} catch (IllegalAccessException pe) {
			Class cls = method.getDeclaringClass();
			try {
				if (!Modifier.isPublic(cls.getModifiers())) {
					Method _m = findCallableMethod(cls, name, method
							.getParameterTypes());
					if (_m != null) {
						for (int i = 0; i < m.length; i++) {
							if (m[i] == method) {
								if (DEBUG) {
									System.out.println(_m + " <- " + method);
								}
								m[i] = _m;
								break;
							}
						}
						return _m.invoke(target, args);
					}
				}
				return Configuration.normalConfiguration.reInvoke(pe, method,
						target, args);
			} catch (IllegalAccessException iae) {
				throw new PnutsException(iae, context);
			} catch (InvocationTargetException ita) {
				Throwable t = ita.getTargetException();
				if (t instanceof PnutsException){
					throw (PnutsException)t;
				} else {
					throw new PnutsException(t, context);
				}
			}
		} catch (PnutsException pe) {
			throw pe;
		} catch (Exception e) {
			throw new PnutsException(e, context);
		}
	}

	protected static Method findCallableMethod(Class clazz, String name,
			Class args[]) {
		while (clazz != null) {
			Method method;
			if (Modifier.isPublic(clazz.getModifiers())) {
				try {
					method = clazz.getMethod(name, args);
					if (method != null
							&& Modifier.isPublic(method.getDeclaringClass()
									.getModifiers())) {
						return method;
					}
				} catch (NoSuchMethodException nme) {
				}
			}
			Class it[] = clazz.getInterfaces();
			for (int i = 0; i < it.length; i++) {
				Method m = findCallableMethod(it[i], name, args);
				if (m != null) {
					return m;
				}
			}
			clazz = clazz.getSuperclass();
		}
		return null;
	}

	/**
	 * Call a constructor
	 * 
	 * @param context
	 *            the context in which the constructor is called
	 * @param c
	 *            the class of method
	 * @param args
	 *            the paramters
	 * @param types
	 *            the types of the formal arguments
	 * @return the created instance
	 */
	protected Object _callConstructor(Context context, Class c, Object args[],
			Class types[]) {
		try {
			Constructor cs[] = context.config._getConstructors(c);

			Constructor cons = null;
			int count = 0;
			int min = Integer.MAX_VALUE;
			cand: for (int i = 0; i < cs.length; i++) {
				Class p[] = cs[i].getParameterTypes();
				if (p.length != args.length) {
					continue;
				}
				count = 0;
				for (int j = 0; j < p.length; j++) {
					Class pj = p[j];
					if (types != null) {
						Class tj = types[j];
						if (tj != null && pj != tj && !pj.isAssignableFrom(tj)) {
							continue cand;
						}
					}
					int t = matchType(pj, args[j]);
					if (t < 0) {
						continue cand;
					}
					count += t;
				}
				if (count < min) {
					min = count;
					cons = cs[i];
				}
			}
			if (cons != null) {
				Class p[] = cons.getParameterTypes();
				for (int j = 0; j < p.length; j++) {
					Class pj = p[j];
					if (args[j] != null &&
					    isArray(args[j]) && 
					    (pj.isArray() || List.class.isAssignableFrom(pj)))
					{
						if (!pj.isInstance(args[j])) {
							args[j] = transform(pj, args[j], context);
						}
					}
				}
				try {
					return cons.newInstance(args);
				} catch (InvocationTargetException ita) {
					Throwable t = ita.getTargetException();
					if (t instanceof PnutsException){
						throw (PnutsException)t;
					} else {
						throw new PnutsException(t, context);
					}
				}
			} else {
				throw new IllegalArgumentException();
			}
		} catch (IllegalArgumentException e2) {
			throw new PnutsException("constructor.notFound", new Object[] { c,
					Pnuts.format(args) }, context);
		} catch (PnutsException e3) {
			throw e3;
		} catch (Throwable e4) {
			throw new PnutsException(e4, context);
		}
	}

	/**
	 * Assign an object to a static field.
	 * 
	 * @param context
	 *            the context in which the field is accessed
	 * @param clazz
	 *            the class in which the static field is defined
	 * @param name
	 *            the name of the static field
	 * @param expr
	 *            the value to be assigned
	 */
	public static void putStaticField(Context context, Class clazz,
			String name, Object expr) {
		context.config.putStaticField(context, clazz, name, expr);
	}

	/**
	 * Get the value of a static field.
	 * 
	 * @param context
	 *            the context in which the field is accessed
	 * @param clazz
	 *            the class in which the static field is defined
	 * @param name
	 *            the name of the static field
	 * @return the value
	 */
	public static Object getStaticField(Context context, Class clazz,
			String name) {
		return context.config.getStaticField(context, clazz, name);
	}

	/**
	 * Assign an object to a instance field.
	 * 
	 * @param context
	 *            the context in which the field is accessed
	 * @param target
	 *            the target object of the field
	 * @param name
	 *            the name of the field
	 * @param expr
	 *            the value to be assigned
	 */
	public static void putField(Context context, Object target, String name,
			Object expr) {
		context.config.putField(context, target, name, expr);
	}

	/**
	 * Get the value of a instance field.
	 * 
	 * @param context
	 *            the context in which the field is accessed
	 * @param target
	 *            the target object of the field
	 * @param name
	 *            the name of the field
	 * @return the value
	 */
	public static Object getField(Context context, Object target, String name) {
		return context.config.getField(context, target, name);
	}

	protected Object _getField(Context context,
				   Class c,
				   String name,
				   Object target)
	{
		try {
			Field f = getField(target.getClass(), name);
			if (f == null){
			    throw new PnutsException("field.notFound", new Object[]{name, target}, context);
			}
			return f.get(target);
		} catch (PnutsException pe) {
			throw pe;
		} catch (Exception e) {
			throw new PnutsException(e, context);
		}	    
	}

    protected void _putField(Context context, Class cls, String name, Object target, Object value){
		try {
			Field f = getField(target.getClass(), name);
			if (f == null){
			    throw new PnutsException("field.notFound", new Object[]{name, target}, context);
			}
			f.set(target, value);
		} catch (PnutsException pe) {
			throw pe;
		} catch (Exception e) {
			throw new PnutsException(e, context);
		}	    
    }
	/**
	 * Get true component type from an array type. e.g. int[][] ==> int,
	 * String[] ==> String
	 * 
	 * @param clazz
	 *            An array type to be examined
	 * @return The component type of the array type.
	 */
	public static Class getBottomType(Class clazz) {
		while (clazz.isArray()) {
			clazz = clazz.getComponentType();
		}
		return clazz;
	}

	/**
	 * Creates an array type
	 * 
	 * @param c
	 *            the component type
	 * @param dim
	 *            the number of dimensions
	 */
	public static Class arrayType(Class c, int dim) {
		if (dim == 0) {
			return c;
		} else {
			return arrayType(Array.newInstance(c, 0).getClass(), dim - 1);
		}
	}

	protected static int arraydim(Object o) {
		if (isArray(o)) {
			int len = getArrayLength(o);
			int maxDim = 0;
			for (int i = 0; i < len; i++) {
				int dim = arraydim(Array.get(o, i));
				if (dim > maxDim) {
					maxDim = dim;
				}
			}
			return maxDim + 1;
		} else {
			return 0;
		}
	}

	public static Object transform(Class type, Object obj) {
		return transform(type, obj, null);
	}

	public static Object transform(Class type, Object obj, Context context) {
		if (type.isArray()){
			boolean isList = (obj instanceof List);
			boolean isArray = isArray(obj);
			if (obj != null && !(isList || isArray)){
			    return obj;
			}
			Class componentType = type.getComponentType();
			int len;
			if (isArray){
				len = getArrayLength(obj);
			} else if (isList){
				len = ((List)obj).size();
			} else {
				return obj;
			}
			Object result = Array.newInstance(componentType, len);
			for (int i = 0; i < len; i++){
				Object elem = null;
				if (context != null){
					elem = context.config.getElement(context, obj, new Integer(i));
				} else {
					if (isArray){
						elem = Array.get(obj, i);
					} else if (isList){
						elem = ((List)obj).get(i);
					}
				}
				Array.set(result, i, transform(componentType, elem, context));
			}
			return result;
		} else {
		    // TODO
			if (type == byte.class){
			    if (!(obj instanceof Byte) && obj instanceof Number){
				obj = new Byte(((Number)obj).byteValue());
			    }
			} else if (type == short.class){
			    if (!(obj instanceof Short) && obj instanceof Number){
				obj = new Short(((Number)obj).shortValue());
			    }
			} else if (type == char.class){
			    if (obj instanceof Number){
				obj = new Character((char)((Number)obj).intValue());
			    }
			} else if (List.class.isAssignableFrom(type)){
			    if (isArray(obj)){
				int len = getArrayLength(obj);
				try {
				    List list;
				    if (type.isInterface()){
					list = new ArrayList();
					if (!type.isInstance(list)){
					    throw new IllegalArgumentException();
					}
				    } else {
					list = (List)type.newInstance();
				    }
				    for (int i = 0; i < len; i++){
					list.add(Array.get(obj, i));
				    }
				    return list;
				} catch (Exception e){
				    throw new IllegalArgumentException(e);
				}
			    }
			}
			return obj;
		}
	}

	protected static int matchType(Class type, Object obj) {
		if (obj == null) {
			if (type.isPrimitive()) {
				return -1;
			}
			return 0;
		}
		Class clazz = obj.getClass();
		if (clazz == type) {
			return 0;
		}
		if (type == boolean.class) {
			return (clazz == Boolean.class) ? 0 : -1;
		}
		if (type == byte.class) {
			return distance(0, clazz);
		}
		if (type == char.class) {
			return distance(6, clazz);
		}
		if (type == short.class) {
			return distance(1, clazz);
		}
		if (type == int.class) {
			return distance(2, clazz);
		}
		if (type == long.class) {
			return distance(3, clazz);
		}
		if (type == float.class) {
			return distance(4, clazz);
		}
		if (type == double.class) {
			return distance(5, clazz);
		}
		if (type.isAssignableFrom(clazz)) {
			return 1;
		} else if (clazz.isArray()){
		    if (type.isArray()) {
			int j = 1;
			for (int i = 0; i < getArrayLength(obj); i++) {
				int s = matchType(type.getComponentType(), Array.get(obj, i));
				if (s < 0) {
					return -1;
				} else {
					j += s;
				}
			}
			return j;
		    } else if (List.class.isAssignableFrom(type)){
			return 1;
		    }
		} else if (type.isArray()){
		    if (obj instanceof List){
			int j = 1;
			List list = (List)obj;
			for (int i = 0; i < list.size(); i++) {
				int s = matchType(type.getComponentType(), list.get(i));
				if (s < 0) {
					return -1;
				} else {
					j += s;
				}
			}
			return j;
		    }
		}
		return -1;
	}

    private final static int[][] distance_table = {
	{
	    0,1,2,3,4,5,1
	},
	{
	    1,0,1,2,3,4,2
	},
	{
	    2,2,0,1,2,3,2
	},
	{
	    3,3,3,0,1,2,-1
	},
	{
	    4,4,4,4,0,1,-1
	},
	{
	    5,5,5,5,5,0,-1
	},
	{
	    1,2,2,-1,-1,-1,0
	},
    };

	private static int distance(int pos, Class clazz) {
		if (clazz == Double.class) {
		    return distance_table[5][pos];
		} else if (clazz == Float.class) {
		    return distance_table[4][pos];
		} else if (clazz == Long.class) {
		    return distance_table[3][pos];
		} else if (clazz == Integer.class) {
		    return distance_table[2][pos];
		} else if (clazz == Short.class) {
		    return distance_table[1][pos];
		} else if (clazz == Character.class) {
		    return distance_table[6][pos];
		} else if (clazz == Byte.class) {
		    return distance_table[0][pos];
		} else {
			return -1;
		}
	}

	/*
	 * class <-> public methods
	 */
	protected static Method[] getMethods(Context context, Class cls) {
		return context.config.getMethods(cls);
	}

	/*
	 * class <-> Constructors
	 */
	protected static Constructor[] getConstructors(Context context, Class cls) {
		return context.config._getConstructors(cls);
	}

	/**
	 * Parse an integer.
	 * 
	 * @return an array [Number number, int offset_of_unit_symbol]
	 */
	public static Object[] parseInt(String str) throws ParseException {
		char c1 = str.charAt(0);
		if (c1 == '#') {
			return parseInt(str.substring(1), 16, true);
		} else if (c1 == '0') {
			if (str.length() > 1) {
				char c2 = str.charAt(1);
				if (c2 == 'x' || c2 == 'X') {
					return parseInt(str.substring(2), 16, false);
				} else {
					return parseInt(str, 8, false);
				}
			} else {
				return new Object[] { new Integer(0), null };
			}
		} else {
			return parseInt(str, 10, false);
		}
	}

	static Object[] parseInt(String str, int radix, boolean shrink)
			throws ParseException {
		boolean overflow = false;
		long value = 0;
		int len = str.length();
		int i = 0;
		out: for (i = 0; i < len; i++) {
			int ch = str.charAt(i);
			switch (ch) {
			case '0':
			case '1':
			case '2':
			case '3':
			case '4':
			case '5':
			case '6':
			case '7':
				if (radix == 8) {
					value = (value << 3) + Character.toLowerCase((char) ch)
							- '0';
					if (value < 0) {
						overflow = true;
						break out;
					}
					break;
				}
			case '8':
			case '9':
				if (radix == 10) {
					int c = ch - '0';
					value = (value * 10) + c;
					if (value < 0) {
						overflow = true;
						break out;
					}
					break;
				} else if (radix == 16) {
					value = (value << 4) + Character.toLowerCase((char) ch)
							- '0';
					if (value < 0) {
						overflow = true;
						break out;
					}
					break;
				} else if (radix == 8) {
					throw new ParseException();
				}
			case 'a':
			case 'A':
			case 'b':
			case 'B':
			case 'c':
			case 'C':
			case 'd':
			case 'D':
			case 'e':
			case 'E':
			case 'f':
			case 'F':
				if (radix == 10) {
					break out;
				} else if (radix == 8) {
					throw new ParseException();
				}
				value = (value << 4) + 10 + Character.toLowerCase((char) ch)
						- 'a';
				if (value < 0) {
					overflow = true;
					break out;
				}
				break;
			default:
				break out;
			}
		}

		out2: for (; i < len; i++) {
			int ch = str.charAt(i);
			switch (ch) {
			case '0':
			case '1':
			case '2':
			case '3':
			case '4':
			case '5':
			case '6':
			case '7':
			case '8':
			case '9':
				break;
			case 'a':
			case 'A':
			case 'b':
			case 'B':
			case 'c':
			case 'C':
			case 'd':
			case 'D':
			case 'e':
			case 'E':
			case 'f':
			case 'F':
				if (radix == 10) {
					break out2;
				}
				break;
			default:
				break out2;
			}
		}

		Number number = null;

		if (overflow) {
			String s = str;
			if (str.length() > i) {
				s = str.substring(0, i);
			}
			number = decimalNumber(s, radix);
		} else {
			if (value >= 0 && value <= 255 && radix == 16) {
				if (shrink) {
					number = new Byte((byte) value);
				} else {
					number = new Integer((int) value);
				}
			} else if (value <= Integer.MIN_VALUE || value >= Integer.MAX_VALUE) {
				number = new Long(value);
			} else {
				number = new Integer((int) value);
			}
		}
		if (str.length() > i) {
			if (radix == 16) {
				i += 2;
			}
			return new Object[] { number, new int[] { i } };
		} else {
			return new Object[] { number, null };
		}
	}

	/**
	 * Parse a floating point number.
	 * 
	 * @return an array [Number number, int offset_of_unit_symbol]
	 */
	public static Object[] parseFloat(String str) {
		int i = 0;
		int len = str.length();
		out1: for (i = 0; i < len; i++) {
			int ch = str.charAt(i);
			switch (ch) {
			case '0':
			case '1':
			case '2':
			case '3':
			case '4':
			case '5':
			case '6':
			case '7':
			case '8':
			case '9':
			case '.':
				break;
			default:
				break out1;
			}
		}
		if (i < len) {
			int ch = str.charAt(i);
			if (ch == 'e' || ch == 'E') {
				i++;
				out2: for (; i < len; i++) {
					ch = str.charAt(i);
					switch (ch) {
					case '+':
					case '-':
					case '0':
					case '1':
					case '2':
					case '3':
					case '4':
					case '5':
					case '6':
					case '7':
					case '8':
					case '9':
						break;
					default:
						break out2;
					}
				}
			}
		}
		String s = str;
		if (i < len) {
			s = str.substring(0, i);
		}
		Number n = null;
		if (s.length() > 16) {
			n = decimalNumber(s, 10);
		} else {
			n = Double.valueOf(s);
		}
		if (i < len) {
			return new Object[] { n, new int[] { i } };
		} else {
			return new Object[] { n, null };
		}
	}

	/**
	 * Parse a string literal.
	 * 
	 * @return the value
	 */
	public static String parseString(String str, int offset) throws ParseException {
		StringBuffer buf = new StringBuffer();
		int length = str.length();
		for (int i = offset; i < length - offset; i++) {
			char ch = str.charAt(i);
			if (ch == '\\') {
				i++;
				switch (str.charAt(i)) {
				case '"':
					buf.append('"');
					break;
				case 'b':
					buf.append('\b');
					break;
				case 'f':
					buf.append('\f');
					break;
				case 't':
					buf.append('\t');
					break;
				case 'r':
					buf.append('\r');
					break;
				case 'n':
					buf.append('\n');
					break;
				case '0':
					buf.append('\0');
					break;
				case '\\':
					buf.append('\\');
					break;
				case 'u':
				case 'U': {
					if (i + 6 > length) {
						throw new ParseException();
					}
					int value = 0;
					for (int j = 0; j < 4; j++) {
						int c = str.charAt(++i);
						switch (c) {
						case '0':
						case '1':
						case '2':
						case '3':
						case '4':
						case '5':
						case '6':
						case '7':
						case '8':
						case '9':
							value = (value << 4) + (c - '0');
							break;
						case 'a':
						case 'A':
						case 'b':
						case 'B':
						case 'c':
						case 'C':
						case 'd':
						case 'D':
						case 'e':
						case 'E':
						case 'f':
						case 'F':
							value = (value << 4) + 10
									+ Character.toLowerCase((char) c) - 'a';
							break;
						default:
							throw new ParseException();
						}
					}
					buf.append((char) value);
					break;
				}
				default:
					throw new ParseException();
				}
			} else {
				buf.append(ch);
			}
		}
		return buf.toString();
	}

	/**
	 * Parse a character literal.
	 * 
	 * @return the value
	 */
	public static Character parseChar(String str) throws ParseException {
		if (str.charAt(1) == '\\') {
			switch (str.charAt(2)) {
			case '\'':
				return new Character('\'');
			case 'b':
				return new Character('\b');
			case 'f':
				return new Character('\f');
			case 't':
				return new Character('\t');
			case 'r':
				return new Character('\r');
			case 'n':
				return new Character('\n');
			case '0':
				return new Character('\0');
			case '\\':
				return new Character('\\');
			case 'u':
			case 'U': {
				int len = str.length();
				if (len != 8) {
					throw new ParseException();
				}
				int value = 0;
				for (int p = 3; p < 7; p++) {
					int ch = str.charAt(p);
					switch (ch) {
					case '0':
					case '1':
					case '2':
					case '3':
					case '4':
					case '5':
					case '6':
					case '7':
					case '8':
					case '9':
						value = (value << 4) + (ch - '0');
						break;
					case 'a':
					case 'A':
					case 'b':
					case 'B':
					case 'c':
					case 'C':
					case 'd':
					case 'D':
					case 'e':
					case 'E':
					case 'f':
					case 'F':
						value = (value << 4) + 10
								+ Character.toLowerCase((char) ch) - 'a';
						break;
					default:
						throw new ParseException();
					}
				}
				return new Character((char) value);
			}
			default:
				throw new ParseException();
			}
		} else {
			if (str.length() > 3) {
				throw new ParseException();
			}
			return new Character(str.charAt(1));
		}
	}

	/**
	 * Creates an object from a number literal and a unit symbol
	 * 
	 * @param number
	 *            a number object
	 * @param numberString
	 *            a symbol of the number literal
	 * @param unit
	 *            a unit symbol
	 * @param context
	 *            a context in which the quantity is created
	 */
	public static Object quantity(Number number, String numberString,
			String unit, Context context) {
		Hashtable units = context.unitTable;
		if (units != null) {
			QuantityFactory factory = (QuantityFactory) units.get(unit);
			if (factory != null) {
				return factory.make(number, unit);
			}
		}
		if ("f".equals(unit) || "F".equals(unit)) {
			return new Float(number.floatValue());
		} else if ("d".equals(unit) || "D".equals(unit)) {
			return new Double(number.doubleValue());
		} else if ("l".equals(unit) || "L".equals(unit)) {
			return new Long(number.longValue());
		} else if ("b".equals(unit) || "B".equals(unit)) {
			return decimalNumber(numberString, 10);
		}
		throw new PnutsException("unitName.notDefined", new Object[] { unit },
				context);
	}

	/**
	 * This method is called by the syntax "primitiveType(object)" and
	 * "(primitiveType)object"
	 * 
	 * @param context
	 *            the context
	 * @param primitiveType
	 *            a primitive type
	 * @param param
	 *            the parameter
	 * @param flag
	 *            string <->number conversion
	 */
	public static Object primitive(Context context, Class primitiveType,
			Object param, boolean flag) {
		return primitive(primitiveType, param, flag, 10);
	}

	static Object primitive(Class primitiveType, Object param, boolean flag, int radix) {
		if (primitiveType == int.class) {
			if (param instanceof Byte) {
				byte b = ((Byte) param).byteValue();
				return new Integer((int) b);
			} else if (param instanceof Character) {
				return new Integer((int) ((Character) param).charValue());
			} else if (param instanceof Number) {
				return new Integer(((Number) param).intValue());
			} else if (flag) {
				if (param == null) {
					return null;
				} else {
					return new Integer(Integer.parseInt(
							param.toString().trim(), radix));
				}
			}
		} else if (primitiveType == byte.class) {
			if (param instanceof Character) {
				return new Byte((byte) ((Character) param).charValue());
			} else if (param instanceof Number) {
				return new Byte(((Number) param).byteValue());
			} else if (flag) {
				if (param == null) {
					return null;
				} else {
					return new Byte(Byte.parseByte(param.toString().trim(),
							radix));
				}
			}
		} else if (primitiveType == char.class) {
			if (param instanceof Number) {
				int i = ((Number) param).intValue();
				return new Character((char)(i & 0xffff));
			} else if (param instanceof Character) {
				return param;
			}
		} else if (primitiveType == long.class) {
			if (param instanceof Number) {
				return new Long(((Number) param).longValue());
			} else if (flag) {
				if (param == null) {
					return null;
				} else {
					return new Long(Long.parseLong(param.toString().trim(),
							radix));
				}
			}
		} else if (primitiveType == short.class) {
			if (param instanceof Character) {
				return new Short((short) ((Character) param).charValue());
			} else if (param instanceof Number) {
				return new Short(((Number) param).shortValue());
			} else if (flag) {
				if (param == null) {
					return null;
				} else {
					return new Short(Short.parseShort(param.toString().trim(),
							radix));
				}
			}
		} else if (primitiveType == double.class) {
			if (param instanceof Number) {
				return new Double(((Number) param).doubleValue());
			} else if (flag) {
				if (param == null) {
					return null;
				} else {
					return Double.valueOf(param.toString().trim());
				}
			}
		} else if (primitiveType == float.class) {
			if (param instanceof Number) {
				return new Float(((Number) param).floatValue());
			} else if (flag) {
				if (param == null) {
					return null;
				} else {
					return Float.valueOf(param.toString().trim());
				}
			}
		} else if (primitiveType == boolean.class) {
		    if (flag){
			return toBoolean(param);
		    } else {
			if (param instanceof Boolean) {
				return param;
			} else if (param == null) {
				return Boolean.FALSE;
			}
		    }
		}
		throw new ClassCastException("(" + primitiveType.getName() + ")"
				+ Pnuts.format(param));
	}

	/**
	 * Convert a given object to a boolean value
	 */
	public static Boolean toBoolean(Object param){
	    if (param instanceof Boolean) {
		return (Boolean)param;
	    } else if (param == null) {
		return Boolean.FALSE;
	    } else if (param instanceof Double){
		double value = ((Double)param).doubleValue();
		return Boolean.valueOf(value != 0.0 && !Double.isNaN(value));
	    } else if (param instanceof Float){
		float value = ((Float)param).floatValue();
		return Boolean.valueOf(value != 0.0f && !Float.isNaN(value));
	    } else if (param instanceof Number){
		return eq(ZERO, param) ? Boolean.FALSE : Boolean.TRUE;
	    } else if (param instanceof String){
		return ((String)param).length() > 0 ? Boolean.TRUE : Boolean.FALSE;
	    } else {
		return Boolean.TRUE;
	    }
	}

	/**
	 * This method is called by the syntax "(Class)object"
	 * 
	 * @param context
	 *            the context
	 * @param type
	 *            the type
	 * @param flag
	 *            object_array <->primitive_array conversion
	 */
	public static Object cast(Context context, Class type, Object object,
			boolean flag) {
		if (type.isPrimitive()) {
			return primitive(context, type, object, false);
		}
		if (object == null) {
			return null;
		} else if (type.isInstance(object)) {
			return object;
		} else if (type.isArray()) {
			if (flag) {
			    try {
				return transform(type, object, context);
			    } catch (IllegalArgumentException e){
				throw new ClassCastException("(" + getClassName(type) + ")"
							     + Pnuts.format(object));
			    }
			} else {
				return object;
			}
		}
		throw new ClassCastException("(" + getClassName(type) + ")"
				+ Pnuts.format(object));
	}

	/**
	 * Check if the parameter is an array
	 * 
	 * @param obj
	 *            the object to be checked
	 * @return true if the obj is an array, false otherwise.
	 */
	public final static boolean isArray(Object obj) {
		return (obj instanceof Object[] || obj instanceof int[]
				|| obj instanceof char[] || obj instanceof boolean[]
				|| obj instanceof byte[] || obj instanceof double[]
				|| obj instanceof long[] || obj instanceof short[] || obj instanceof float[]);
	}

	/**
	 * Gets an array's length
	 * 
	 * @param array
	 *            the array
	 * @return the length
	 */
	public final static int getArrayLength(Object array) {
		if (array instanceof Object[]) {
			return ((Object[]) array).length;
		} else if (array instanceof int[]) {
			return ((int[]) array).length;
		} else if (array instanceof byte[]) {
			return ((byte[]) array).length;
		} else if (array instanceof char[]) {
			return ((char[]) array).length;
		} else if (array instanceof float[]) {
			return ((float[]) array).length;
		} else if (array instanceof double[]) {
			return ((double[]) array).length;
		} else if (array instanceof boolean[]) {
			return ((boolean[]) array).length;
		} else if (array instanceof long[]) {
			return ((long[]) array).length;
		} else if (array instanceof short[]) {
			return ((short[]) array).length;
		} else {
			throw new IllegalArgumentException(String.valueOf(array));
		}
	}

	/**
	 * Range expression 'target[idx1..idx2]'.
	 * 
	 * @return the intersection of the whole range of the target object and the
	 *         range [idx1..idx2]. This method never throw
	 *         ArrayIndexOutOfBoundsException.
	 */
	public static Object getRange(Object target, Object idx1, Object idx2,
			Context context) {
		return context.config.getRange(context, target, idx1, idx2);
	}

	/**
	 * This method is called by the syntax "id[from..to] = sth"
	 */
	public static Object setRange(Object target, Object idx1, Object idx2,
			Object expr, Context context) {
		return context.config.setRange(context, target, idx1, idx2, expr);
	}

	public static String replaceChar(String str, Number n, Object expr){
	    int len = str.length();
	    int idx = ((Number)n).intValue();
	    if (idx < 0 && idx >= -len){
		idx += len;
	    }
	    StringBuffer sbuf = new StringBuffer(str);
	    if (expr instanceof Character){
		sbuf.setCharAt(idx, ((Character)expr).charValue());
	    } else if (expr instanceof String){
		sbuf.replace(idx, idx + 1, expr.toString());
	    } else if (expr instanceof char[]){
		sbuf.replace(idx, idx + 1, new String((char[])expr));
	    } else {
		throw new IllegalArgumentException(String.valueOf(expr));
	    }
	    return sbuf.toString();
	}

	protected static void checkException(Context context, Throwable throwable) {
		if (throwable instanceof ParseException) {
			context.beginLine = ((ParseException) throwable).getErrorLine();
		}
		StackFrame stackFrame = context.stackFrame;
		if (stackFrame != null) {
			StackFrame frame = stackFrame;
			if (stackFrame.parent != null) {
				NamedValue b = frame
						.lookup(Context.exceptionHandlerTableSymbol);
				if (b != null) {
					checkException(context, throwable, (TypeMap) b.get());
					return;
				}
			}
		}
		TypeMap tmap = (TypeMap) context
				.resolveSymbol(Context.exceptionHandlerTableSymbol);
		if (tmap != null) {
			checkException(context, throwable, tmap);
			return;
		}
		if (throwable instanceof Escape) {
			throw (Escape) throwable;
		} else if (throwable instanceof PnutsException) {
			throw (PnutsException) throwable;
		} else {
			throw new PnutsException(throwable, context);
		}
	}

	/**
	 * Check if any exception handler is registered to the specified exception.
	 * If any an exception handler is executed. If not, the exception is thrown.
	 * 
	 * @param context
	 *            the Context in which the exception is checked
	 * @param throwable
	 *            the exception
	 * @param tmap
	 *            the exception handler table
	 */
	protected static void checkException(Context context, Throwable throwable,
			TypeMap tmap) {
		if (DEBUG) {
			System.out.println("checkException " + throwable + ", " + tmap);
		}
		Throwable e = throwable;
		if (e instanceof PnutsException) {
			throwable = ((PnutsException) e).getThrowable();
		}
		if (throwable instanceof Escape) {
			if (DEBUG) {
				System.out.println(throwable);
			}
			throw (Escape) throwable;
		}
		if (throwable instanceof ThreadDeath) {
			throw (ThreadDeath) throwable;
		}
		PnutsFunction f = null;
		while (tmap != null) {
			Class type = tmap.type;
			if (type == null || type.isInstance(throwable)) {
				f = (PnutsFunction) tmap.value;
				break;
			}
			tmap = tmap.next;
		}
		if (f == null) {
			if (e instanceof PnutsException) {
				throw (PnutsException) e;
			} else {
				throw new PnutsException(throwable, context);
			}
		} else {
			Object ret = f.exec(new Object[] { throwable }, context);
			PrintWriter pw = context.getWriter();
			if (pw != null) {
				pw.flush();
			}
			throw new Jump(ret);
		}
	}

	/**
	 * This method is called when catch() function is called in a
	 * package(non-local) scope
	 * 
	 * @param type
	 *            the exception type of which an exception handler is registered
	 * @param func
	 *            the function to be registered as an exception handler
	 * @param context
	 *            the context in which the exception handler is registered
	 */
	protected static void catchException(Class type, PnutsFunction func,
			Context context) {
		if (DEBUG) {
			System.out.println("catchException " + type.getName() + ": "
					+ func.unparse(1));
		}
		Package pkg = context.getCurrentPackage();
		TypeMap typemap = null;
		if (type != Throwable.class) {
			typemap = (TypeMap) pkg.get(Context.exceptionHandlerTableSymbol,
					context);
		}
		pkg.set(Context.exceptionHandlerTableSymbol, new TypeMap(type, func,
				typemap), context);
	}

	public static void throwException(Object arg, Context context) {
		if (arg instanceof Throwable) {
			throw new PnutsException((Throwable) arg, context);
		} else {
			throw new PnutsException(String.valueOf(arg));
		}
	}

	public static void setExitHook(Context context, final PnutsFunction func) {
		context.setExitHook(new Executable() {
			public Object run(Context ctx) {
				return func.call(new Object[] {}, ctx);
			}
		});
	}

	/**
	 * This method is called by the syntax "target[key]"
	 */
	public static Object getElement(Object target, Object key, Context context) {
		return context.config.getElement(context, target, key);
	}

	public static Object getElementAt(Object target, int idx, Context context) {
	    try {
		if (target == null){
		    return null;
		} else {
		    return context.config.getElement(context, target, new Integer(idx));
		}
	    } catch (IndexOutOfBoundsException e){
		return null;
	    }
	}

	/**
	 * This method is called by the syntax "target[key] = value"
	 */
	public static void setElement(Object target, Object key, Object value,
			Context context) {
		context.config.setElement(context, target, key, value);
	}

	/*
	 * Converts an object to Enumeration This method is called by foreach
	 * statements.
	 * 
	 * @param target the target object to be converted to an Enumeration @param
	 * context the context in which the target object is converted to an
	 * Enumeration @return an Enumeration object converted from the target
	 * object. null if the target object could not be converted to an
	 * Enumeration.
	 */
	public static Enumeration toEnumeration(Object target, Context context) {
		return context.config.toEnumeration(target);
	}

	/**
	 * Call a function
	 * 
	 * @param context
	 *            the context in which the function is called
	 * @param func
	 *            the function to be called
	 * @param args
	 *            the arguments
	 */
	protected final static Object callFunction(Context context,
			PnutsFunction func, Object[] args) {
		return func.exec(args, context);
	}

	/**
	 * This method is called by the syntax "funcOrClass(args...)"
	 */
	public final static Object call(Context context, Object target,
			Object[] args, Class[] casts) {
		if (target instanceof PnutsFunction) {
			return ((PnutsFunction)target).exec(args, context);
		} else if (target instanceof Class) {
			Class c = (Class) target;
			if (c.isPrimitive()) {
				try {
					int nargs = args.length;
					switch (nargs) {
					case 1:
						return primitive(context, c, args[0], true);
					case 2:
						int radix = ((Integer) args[1]).intValue();
						return primitive(c, args[0], true, radix);
					default:
						throw new IllegalArgumentException(Pnuts.format(args));
					}
				} catch (NumberFormatException e) {
					return null;
				}
			} else if (c.isArray()) {
				return cast(context, c, args[0], true);
			} else {
				return context.config.callConstructor(context, c, args, casts);
			}
		} else if (target instanceof Callable) {
			return ((Callable) target).call(args, context);
		}
		throw new PnutsException("funcOrType.expected", new Object[] { Pnuts
				.format(target) }, context);
	}

	public final static Object call(Context context, Object target,
			Object[] args, Class[] casts, int line, int column) {
		Object ret;
		if (target instanceof PnutsFunction) {
			Function caller = context.frame;
			PnutsFunction f = (PnutsFunction)target;
			try {
				ret = f.exec(args, context);
			} catch (PnutsException p) {
				if (caller != null) {
					p.backtrace(new PnutsException.TraceInfo(f.name, args,
										 caller.file, line, column));
				} else {
					p.backtrace(new PnutsException.TraceInfo(f.name, args, null,
										 line, column));
				}
				throw p;
			}
		} else if (target instanceof Class) {
			Class c = (Class) target;
			if (c.isPrimitive()) {
				try {
					int nargs = args.length;
					switch (nargs) {
					case 1:
						ret = primitive(context, c, args[0], true);
						break;
					case 2:
						int radix = ((Integer) args[1]).intValue();
						ret = primitive(c, args[0], true, radix);
						break;
					default:
						throw new IllegalArgumentException(Pnuts.format(args));
					}
				} catch (NumberFormatException e) {
					context.updateLine(null, line, column);
					return null;
				}
			} else if (c.isArray()) {
				ret = cast(context, c, args[0], true);
			} else {
				ret = context.config.callConstructor(context, c, args, casts);
			}
		} else if (target instanceof Callable) {
			return ((Callable) target).call(args, context);
		} else {
			Callable c = context.config.toCallable(target);
			if (c != null){
				return c.call(args, context);
			} else {
				throw new PnutsException("funcOrType.expected",
							 new Object[] { Pnuts.format(target) }, context);
			}
		}
		context.updateLine(null, line, column);
		return ret;
	}

	protected static Object newInstance(Context context, Class c,
			Object[] args, Class[] casts) {
		return context.config.callConstructor(context, c, args, casts);
	}

	protected static Object makeArray(Object[] parameters, Context context) {
		return context.config.makeArray(parameters, context);
	}

	protected static Map createMap(int size, Context context) {
		return context.config.createMap(size, context);
	}

	protected static List createList(Context context) {
		return context.config.createList();
	}

	protected static void jump(Object v) {
		throw new Jump(v);
	}

	protected static void escape(Object v) {
		throw new Escape(v);
	}

	/**
	 * Set line number information for error reporting
	 *
	 * @deprecated
	 */
	protected static void setLine(Context context, int beginLine, int beginColumn) {
		context.updateLine(null, beginLine, beginColumn);
	}

	public static void setLine(Context context, int line) {
		context.updateLine(line);
	}

	protected static int getBeginLine(Context context) {
		return context.beginLine;
	}

	protected static int getBeginColumn(Context context) {
		return context.beginColumn;
	}

	protected static int getEndLine(Context context) {
		return context.endLine;
	}

	protected static Function getFunction(PnutsFunction pf, int nargs) {
		return pf.get(nargs);
	}

	protected static Enumeration getFunctions(PnutsFunction pf) {
		return pf.elements();
	}

	protected static Runtime getRuntime(Context context) {
		return context.runtime;
	}

	protected static Object getScriptSource(Context context) {
		return context.getScriptSource();
	}

	protected static Function getFunction(Context context) {
		return context.frame;
	}

	protected static void setPackage(Package pkg, Context context) {
		pkg.init(context);
	}

	private static char[] hexDigit = { '0', '1', '2', '3', '4', '5', '6', '7',
			'8', '9', 'A', 'B', 'C', 'D', 'E', 'F' };

	private final static int DEFAULT_FORMAT_MAX_LENGTH = 512;

	/**
	 * Get the String representation of the specified object.
	 * 
	 * @param object
	 *            the target object.
	 * @param maxArrayLength
	 *            When the target object is an array and maxArrayLength is
	 *            greater than zero, only the first maxArrayLength elements are
	 *            printed and the rest of the elements are omitted as "...".
	 */
	public static String format(Object object, int maxArrayLength) {
		return format(object, maxArrayLength, DEFAULT_FORMAT_MAX_LENGTH);
	}

	private final static char[] defaultParen = {'[', ']'};

	public static String format(Object object, int maxArrayLength, int maxFormatSize) {
		return format(object, maxArrayLength, maxFormatSize, defaultParen);
	}

	public static String format(Object object, int maxArrayLength,
			int maxFormatSize, char[] paren) {
		StringBuffer sb = new StringBuffer();
		try {
			format(object, maxArrayLength, maxFormatSize, paren, new HashSet(), sb);
		} catch (TooLongFormat toolong) {
			sb.append("....");
		}
		return sb.toString();
	}

	static class TooLongFormat extends RuntimeException {
	}

	static void format(Object object, int maxArrayLength, int maxFormatSize, char[] paren,
			Set formattedObjects, StringBuffer sb) {
		if (maxFormatSize < sb.length()) {
			throw new TooLongFormat();
		}
		if (object == null) {
			sb.append("null");
			return;
		} else if (!(isArray(object))) {
			if (object instanceof String) {
				String str = (String) object;
				int len = str.length();
				sb.append('"');
				for (int i = 0; i < len; i++) {
					char ch = str.charAt(i);
					switch (ch) {
					case '\\':
						sb.append("\\\\");
						break;
					case '\b':
						sb.append("\\b");
						break;
					case '\f':
						sb.append("\\f");
						break;
					case '\t':
						sb.append("\\t");
						break;
					case '\n':
						sb.append("\\n");
						break;
					case '\r':
						sb.append("\\r");
						break;
					case '"':
						sb.append("\\\"");
						break;
					default:
						char[] cbuf = new char[1];
						cbuf[0] = ch;
						byte[] conv = new String(cbuf).getBytes();
						if (ch == '?' || !Character.isISOControl(ch)
								&& !(conv.length == 1 && conv[0] == (byte) '?')) {
							sb.append(ch);
						} else {
							sb.append("\\u" + hexDigit[(ch >> 12) & 0xF]
									+ hexDigit[(ch >> 8) & 0xF]
									+ hexDigit[(ch >> 4) & 0xF]
									+ hexDigit[(ch >> 0) & 0xF]);
						}
						break;
					}
				}
				sb.append('"');
				return;
			} else if (object instanceof Character) {
				String s;
				char ch = ((Character) object).charValue();
				switch (ch) {
				case '\\':
					s = "'\\'";
				case '\b':
					s = "'\\b'";
				case '\f':
					s = "'\\f'";
				case '\t':
					s = "'\\t'";
				case '\n':
					s = "'\\n'";
				case '\r':
					s = "'\\r'";
				case '\'':
					s = "'\\''";
				case '?':
					s = "'?'";
					sb.append(s);
					return;
				default:
					char[] buf = new char[1];
					buf[0] = ch;
					byte[] conv = new String(buf).getBytes();
					if (!Character.isISOControl(ch)
							&& !(conv.length == 1 && conv[0] == (byte) '?')) {
						sb.append('\'');
						sb.append(ch);
						sb.append('\'');
						return;
					}
					sb.append("'\\u" + hexDigit[(ch >> 12) & 0xF]
							+ hexDigit[(ch >> 8) & 0xF]
							+ hexDigit[(ch >> 4) & 0xF]
							+ hexDigit[(ch >> 0) & 0xF] + "'");
					return;
				}
			} else if (object instanceof Class) {
				Class c = (Class) object;
				String suffix = null;
				if (c.isInterface()) {
					suffix = " interface";
				} else if (c.isPrimitive()) {
					suffix = " type";
				} else {
					suffix = " class";
				}
				sb.append(getClassName(c) + suffix);
				return;
			} else if (object instanceof Byte) {
				byte value = ((Byte) object).byteValue();
				sb.append("#" + hexDigit[(value >> 4) & 0x0f]
						+ hexDigit[value & 0x0f]);
				return;
			} else if (object instanceof Float) {
				sb.append(object.toString() + "f");
				return;
			} else if (object instanceof Calendar) {
				Calendar cal = (Calendar) object;
				DateFormat f = DateFormat.getDateTimeInstance();
				f.setTimeZone(cal.getTimeZone());
				sb.append(f.format(cal.getTime()));
				return;
			} else {
				sb.append(object.toString());
				return;
			}
		}
		int length = getArrayLength(object);
		int last = length;
		sb.append(paren[0]);

		if (length > 0) {
			Object obj = Array.get(object, 0);
			if (obj != object) {
				formatElement(obj, maxArrayLength, maxFormatSize, paren,
						formattedObjects, sb);
			} else {
				sb.append(String.valueOf(obj));
			}
		}
		if (maxArrayLength > 0 && maxArrayLength < length) {
			last = maxArrayLength;
		}
		for (int j = 1; j < last; j++) {
			Object obj = Array.get(object, j);
			sb.append(", ");
			if (obj != object) {
				formatElement(obj, maxArrayLength, maxFormatSize, paren,
						formattedObjects, sb);
			} else {
				sb.append(String.valueOf(obj));
			}
		}
		if (maxArrayLength > 0 && maxArrayLength < length) {
			sb.append(", ...");
		}
		sb.append(paren[1]);
	}

	static void formatElement(Object obj, int maxArrayLength,
			int maxFormatSize, char[] paren, Set formattedObjects, StringBuffer sb) {
		if (formattedObjects.contains(obj)) {
			if (obj == null) {
				sb.append("null");
			} else if (!(obj instanceof Object[])) {
				sb.append(obj.toString());
			} else {
				sb.append(obj.getClass().getName() + "@" + obj.hashCode());
			}
		} else {
			formattedObjects.add(obj);
			format(obj, maxArrayLength, maxFormatSize, paren, formattedObjects, sb);
			formattedObjects.remove(obj);
		}
	}

	static String getClassName(Class cls) {
		if (cls.isArray()) {
			StringBuffer buf = new StringBuffer();
			for (; cls.isArray(); cls = cls.getComponentType()) {
				buf.append("[]");
			}
			buf.insert(0, cls.getName());
			return buf.toString();
		} else {
			return cls.getName();
		}
	}

	/**
	 * Add 1 to an object (integer)
	 */
	public final static Object add1(Object n) {
		return UnaryOperator.Add1.instance.operateOn(n);
	}

	protected final static Object add1(Object n, Context context) {
		return context._add1.operateOn(n);
	}

	/**
	 * Subtracts 1 from a object (integer)
	 */
	public final static Object subtract1(Object n) {
		return UnaryOperator.Subtract1.instance.operateOn(n);
	}

	protected final static Object subtract1(Object n, Context context) {
		return context._subtract1.operateOn(n);
	}

	/**
	 * Negates an number
	 */
	public final static Object negate(Object n) {
		return UnaryOperator.Negate.instance.operateOn(n);
	}

	protected final static Object negate(Object n, Context context) {
		return context._negate.operateOn(n);
	}

	public final static Object not(Object n) {
		return UnaryOperator.Not.instance.operateOn(n);
	}

	protected final static Object not(Object n, Context context) {
		return context._not.operateOn(n);
	}

	/**
	 * + operation
	 */
	public final static Object add(Object n1, Object n2) {
		return BinaryOperator.Add.instance.operateOn(n1, n2);
	}

	protected final static Object add(Object n1, Object n2, Context context) {
		return context._add.operateOn(n1, n2);
	}

	/**
	 * - operation
	 */
	public final static Object subtract(Object n1, Object n2) {
		return BinaryOperator.Subtract.instance.operateOn(n1, n2);
	}

	protected final static Object subtract(Object n1, Object n2, Context context) {
		return context._subtract.operateOn(n1, n2);
	}

	/**
	 * * operation
	 */
	public final static Object multiply(Object n1, Object n2) {
		return BinaryOperator.Multiply.instance.operateOn(n1, n2);
	}

	protected final static Object multiply(Object n1, Object n2, Context context) {
		return context._multiply.operateOn(n1, n2);
	}

	/**
	 * / operation
	 */
	public final static Object divide(Object n1, Object n2) {
		return BinaryOperator.Divide.instance.operateOn(n1, n2);
	}

	protected final static Object divide(Object n1, Object n2, Context context) {
		try {
			return context._divide.operateOn(n1, n2);
		} catch (Exception e){
			throw new PnutsException(e, context);
		}
	}

	/**
	 * % operation
	 */
	public final static Object mod(Object n1, Object n2) {
		return BinaryOperator.Mod.instance.operateOn(n1, n2);
	}

	protected final static Object mod(Object n1, Object n2, Context context) {
		return context._mod.operateOn(n1, n2);
	}

	/**
	 * < < operation
	 */
	public final static Object shiftLeft(Object n1, Object n2) {
		return BinaryOperator.ShiftLeft.instance.operateOn(n1, n2);
	}

	protected final static Object shiftLeft(Object n1, Object n2,
			Context context) {
		return context._shiftLeft.operateOn(n1, n2);
	}

	/**
	 * >> operation
	 */
	public final static Object shiftRight(Object n1, Object n2) {
		return BinaryOperator.ShiftRight.instance.operateOn(n1, n2);
	}

	protected final static Object shiftRight(Object n1, Object n2,
			Context context) {
		return context._shiftRight.operateOn(n1, n2);
	}

	/**
	 * >>> operation
	 */
	public static Object shiftArithmetic(Object n1, Object n2) {
		return BinaryOperator.ShiftArithmetic.instance.operateOn(n1, n2);
	}

	protected final static Object shiftArithmetic(Object n1, Object n2,
			Context context) {
		return context._shiftArithmetic.operateOn(n1, n2);
	}

	/**
	 * | operation
	 */
	public final static Object or(Object n1, Object n2) {
		return BinaryOperator.Or.instance.operateOn(n1, n2);
	}

	protected final static Object or(Object n1, Object n2, Context context) {
		return context._or.operateOn(n1, n2);
	}

	/**
	 * & operation
	 */
	public final static Object and(Object n1, Object n2) {
		return BinaryOperator.And.instance.operateOn(n1, n2);
	}

	protected final static Object and(Object n1, Object n2, Context context) {
		return context._and.operateOn(n1, n2);
	}

	/**
	 * ^ operation
	 */
	public final static Object xor(Object n1, Object n2) {
		return BinaryOperator.Xor.instance.operateOn(n1, n2);
	}

	protected final static Object xor(Object n1, Object n2, Context context) {
		return context._xor.operateOn(n1, n2);
	}

	/**
	 * < operation
	 */
	public final static boolean lt(Object n1, Object n2) {
		return BooleanOperator.LT.instance.operateOn(n1, n2);
	}

	protected final static boolean lt(Object n1, Object n2, Context context) {
		return context._lt.operateOn(n1, n2);
	}

	/**
	 * > operation
	 */
	public final static boolean gt(Object n1, Object n2) {
		return BooleanOperator.GT.instance.operateOn(n1, n2);
	}

	protected final static boolean gt(Object n1, Object n2, Context context) {
		return context._gt.operateOn(n1, n2);
	}

	/**
	 * >= operation
	 */
	public final static boolean ge(Object n1, Object n2) {
		return BooleanOperator.GE.instance.operateOn(n1, n2);
	}

	protected final static boolean ge(Object n1, Object n2, Context context) {
		return context._ge.operateOn(n1, n2);
	}

	/**
	 * <= operation
	 */
	public final static boolean le(Object n1, Object n2) {
		return BooleanOperator.LE.instance.operateOn(n1, n2);
	}

	protected final static boolean le(Object n1, Object n2, Context context) {
		return context._le.operateOn(n1, n2);
	}

	/**
	 * == operation
	 */
	public final static boolean eq(Object n1, Object n2) {
		return BooleanOperator.EQ.instance.operateOn(n1, n2);
	}

	protected final static boolean eq(Object n1, Object n2, Context context) {
		return context._eq.operateOn(n1, n2);
	}

	/**
	 * != operation
	 */
	public final static boolean ne(Object n1, Object n2) {
		return !BooleanOperator.EQ.instance.operateOn(n1, n2);
	}

	protected final static boolean ne(Object n1, Object n2, Context context) {
		return !context._eq.operateOn(n1, n2);
	}

	/**
	 * Compares n1 with n2
	 */
	public final static int compareTo(Object n1, Object n2) {
		if (gt(n1, n2)) {
			return 1;
		} else if (lt(n1, n2)) {
			return -1;
		} else {
			return 0;
		}
	}

	protected final static int compareTo(Object n1, Object n2, Context context) {
		if (gt(n1, n2, context)) {
			return 1;
		} else if (lt(n1, n2, context)) {
			return -1;
		} else {
			return 0;
		}
	}

 	/**
	 * Compare two objects
	 * 
	 * Elements of List and array are recursively compared.
	 */
	public static int compareObjects(Object e1, Object e2){
		if (e1 == e2){
			return 0;
		}
		if (e1 instanceof List){
			if (e2 instanceof List){
				return compareLists((List)e1, (List)e2);
			} else if (Runtime.isArray(e2)){
				return compareListToArray((List)e1, e2);
			}
		} else if (Runtime.isArray(e1)){
			if (e2 instanceof List){
				return -compareListToArray((List)e2, e1);
			} else if (Runtime.isArray(e2)){
				return compareArrays(e1, e2);
			}
		}
		if (e1 instanceof Comparable){
			return ((Comparable)e1).compareTo(e2);
		} else if (e2 instanceof Comparable){
			return -((Comparable)e2).compareTo(e1);
		} else {
		    throw new IllegalArgumentException();
		}
	}

	static int compareLists(List ol1, List ol2){
		int sz2 = ol2.size();
		int sz1 = ol1.size();
		int min = (sz2 > sz1) ? sz1 : sz2;
		for (int i = 0; i < min; i++){
			Object e1 = ol1.get(i);
			Object e2 = ol2.get(i);
			int c = compareObjects(e1, e2);
			if (c != 0){
				return c;
			}
		}
		if (sz1 == sz2){
			return 0;
		} else {
			return (sz1 > sz2) ? 1 : -1;
		}
	}

	static int compareListToArray(List ol, Object array){
		int len = Array.getLength(array);
		int sz = ol.size();
		int min = (len > sz) ? sz : len;
		for (int i = 0; i < min; i++){
			Object e1 = ol.get(i);
			Object e2 = Array.get(array, i);
			int c = compareObjects(e1,e2);
			if (c != 0){
				return c;
			}
		}
		if (sz == len){
			return 0;
		} else {
			return (sz > len) ? 1 : -1;
		}
	}

	static int compareArrays(Object a1, Object a2){
		int len = Array.getLength(a1);
		int sz = Array.getLength(a2);
		int min = (len > sz) ? sz : len;
		for (int i = 0; i < min; i++){
			Object e1 = Array.get(a1, i);
			Object e2 = Array.get(a2, i);
			int c = compareObjects(e1, e2);
			if (c != 0){
				return c;
			}
		}
		if (sz == len){
			return 0;
		} else {
			return (sz > len) ? 1 : -1;
		}
	}



	/**
	 * Returns a URL of a script
	 */
	public static URL getScriptURL(String name, Context context) {
		URL url = null;
//		try {
			url = Pnuts.getResource(name, context);
			if (url == null) {
				url = Pnuts.getResource("/" + name, context);
			}
			if (url == null)
				return null;
//			URLConnection conn = url.openConnection();
//			conn.connect();
                        /*
			if (context.verbose) {
				PrintWriter out = context.getTerminalWriter();
				if (out != null) {
					out.println("[loading " + url + "]");
					out.flush();
				}
			}
                         */

//		} catch (IOException ioe) {
//			return null;
//		}
		return url;
	}

	/**
	 * This method is called by Pnuts.load() when the property
	 * "pnuts.compiled.script.prefix" is defined, to load pre-compiled scripts.
	 * 
	 * @param name
	 *            the script name
	 * @param context
	 *            the context in which the class is loaded.
	 * @return pnuts.lang.Runtime object if the class is found, otherwise null.
	 */
	public static Executable getCompiledScript(String name, Context context) {
		String prefix = Pnuts.getCompiledClassPrefix();
		Object rt = null;
		try {
			String cname = name.replace('/', '.').replace('-', '_');
			if (prefix != null) {
				cname = prefix + cname;
			}
			Class cls = Pnuts.loadClass(cname, context);
			rt = cls.newInstance();
			if (rt instanceof Executable) {
				return (Executable) rt;
			}
		} catch (ClassNotFoundException e0) { /* ignore */
		} catch (InstantiationException e1) { /* ignore */
		} catch (IllegalAccessException e2){ /* ignore */
		}
		return null;
	}

	/**
	 * Gets a URL from a File
	 * 
	 * @param file
	 *            the File object
	 * @return the resulting URL object
	 */
	public static URL fileToURL(File file) throws IOException {
		String path = new File(file.getCanonicalPath()).getAbsolutePath();
		if (File.separatorChar != '/') {
			path = path.replace(File.separatorChar, '/');
		}
		if (!path.startsWith("/")) {
			path = "/" + path;
		}
		if (!path.endsWith("/") && file.isDirectory()) {
			path = path + "/";
		}
		return new URL("file", "", path);
	}

	/**
	 * Gets a Reader to read script files
	 * 
	 * If context.getScriptEncoding() is non-null, it would be used as the
	 * encoding. Otherwise, the platform encoding is used.
	 * 
	 * @param in
	 *            the input stream
	 * @param context
	 *            the executing context
	 * @return the resulting reader object
	 */
	public static Reader getScriptReader(InputStream in, Context context) {
		String encoding = context.encoding;
		if (encoding == null) {
			return new InputStreamReader(in);
		} else {
			try {
				return new InputStreamReader(in, encoding);
			} catch (UnsupportedEncodingException e) {
				throw new PnutsException(e, context);
			}
		}
	}

	public static void printError(Throwable t, Context context) {
		context.onError(t);
		PrintWriter err = context.getErrorWriter();
		if (err != null) {
			if (context.verbose) {
				t.printStackTrace(err);
			} else {
				err.println(t);
			}
			err.flush();
		} else {
			t.printStackTrace();
		}
	}

	protected static String getMessage(String bundleName, String key, Object a[]) {
		try {
			ResourceBundle bundle = ResourceBundle.getBundle(bundleName);
			String fmt = bundle.getString(key);
			if (fmt == null) {
				return null;
			}
			return MessageFormat.format(fmt, a);
		} catch (MissingResourceException e) {
			return bundleName + ":" + key + Runtime.format(a, 64);
		}
	}

	static Number compress(Number n) {
		if (n instanceof BigInteger) {
			BigInteger b = (BigInteger) n;
			if (b.compareTo(BinaryOperator.maxLong) > 0
					|| b.compareTo(BinaryOperator.minLong) < 0) {
				return n;
			} else {
				long l = b.longValue();
				if (l <= Integer.MAX_VALUE && l >= Integer.MIN_VALUE) {
					return new Integer((int) l);
				} else {
					return new Long(l);
				}
			}
		} else if (n instanceof BigDecimal) {
			int j = 0;
			String f = n.toString();
			int p = f.indexOf((int) '.');
			if (p > 0) {
				char ca[] = f.toCharArray();
				for (int i = 0; i < ca.length - 2 && i < ca.length - p - 2; i++) {
					if (ca[ca.length - i - 1] == '0') {
						j++;
					} else {
						break;
					}
				}
				f = f.substring(0, ca.length - j);
			}
			if (f.length() < 16) {
				return new Double(n.doubleValue());
			}
			if (j > 0) {
				return new BigDecimal(f);
			} else {
				return n;
			}
		} else if (n instanceof Long) {
			long l = ((Long) n).longValue();
			if (l <= Integer.MAX_VALUE && l >= Integer.MIN_VALUE) {
				return new Integer((int) l);
			} else {
				return new Long(l);
			}
		} else {
			return n;
		}
	}

	static Number decimalNumber(String s, int radix) {
		if (radix == 16 || radix == 8) {
			return new BigInteger(s, radix);
		} else {
			return new BigDecimal(deNormalize(s));
		}
	}

	private static String deNormalize(String fmt) {
		int pt = fmt.indexOf((int) '.');
		int exp = fmt.indexOf((int) 'E');
		if (exp < 0) {
			return fmt;
		}
		int e = Integer.parseInt(fmt.substring(exp + 1));
		int i = 0;
		int k = 0;
		char b[];
		if (e < 0) {
			b = new char[exp - e];
			i = 0;
			if (fmt.charAt(k) == '-') {
				b[i++] = '-';
				k++;
			}
			b[i++] = '0';
			b[i++] = '.';
			for (int j = 0; j < -e - 1; j++) {
				b[i++] = '0';
			}
			b[i++] = fmt.charAt(k++);
			k++;
			for (int j = 0; j < exp - pt - 1; j++) {
				b[i++] = fmt.charAt(k++);
			}
		} else {
			if (exp - pt - 1 > e) {
				b = new char[exp];
				i = 0;
				if (fmt.charAt(k) == '-') {
					b[i++] = '-';
					k++;
				}
				b[i++] = fmt.charAt(k++);
				k++;
				for (int j = 0; j < e; j++) {
					b[i++] = fmt.charAt(k++);
				}
				b[i++] = '.';
				for (int j = 0; j < exp - pt - 1 - e; j++) {
					b[i++] = fmt.charAt(k++);
				}
			} else {
				b = new char[exp - 1 + (e - (exp - pt - 1))];
				i = 0;
				if (fmt.charAt(k) == '-') {
					b[i++] = '-';
					k++;
				}
				b[i++] = fmt.charAt(k++);
				k++;
				for (int j = 0; j < exp - pt - 1; j++) {
					b[i++] = fmt.charAt(k++);
				}
				for (int j = 0; j < e - (exp - pt - 1); j++) {
					b[i++] = '0';
				}
			}
		}
		return new String(b);
	}

	static class AutoloadScript implements AutoloadHook, Serializable {
		String file;

		ModuleList moduleList;

		Package pkg;

		String encoding;

		Configuration config;

		AutoloadScript(String file, Context context) {
			this.file = file.intern();
			this.pkg = context.rootPackage;
			this.moduleList = context.moduleList;
			this.encoding = context.encoding;
			this.config = context.config;
		}

		public synchronized void load(String name, Context context) {
			try {
				Context ctx = (Context) context.clone();
				ctx.moduleList = this.moduleList;
				ctx.setCurrentPackage(this.pkg);
				ctx.encoding = this.encoding;
				ctx.config = this.config;
				Pnuts.require(file, ctx);
			} catch (Escape esc) {
				throw esc;
			} catch (FileNotFoundException e) {
				throw new PnutsException(e, context);
			}
		}
	}

	protected static class TypeMap {
		Class type;

		Object value;

		TypeMap next;

		public TypeMap(Class type, Object value, TypeMap next) {
			this.type = type;
			this.value = value;
			this.next = next;
		}
	}

	protected static class Accessor {
		protected Class beanClass;

		protected Class stopClass;

		HashMap readMethods;

		HashMap writeMethods;

		HashMap types;

		protected Accessor(Class cls, Class stopClass) {
			init(cls, stopClass);
		}

		void init(Class cls, Class stopClass) {
			this.beanClass = cls;
			this.stopClass = stopClass;
			this.readMethods = new HashMap();
			this.writeMethods = new HashMap();
			this.types = new HashMap();

			ObjectDescFactory.getDefault().create(cls, stopClass).handleProperties(new PropertyHandler(){
				public void handle(String propertyName, Class type, Method readMethod, Method writeMethod){
				    if (readMethod != null){
					addReadMethod(propertyName, readMethod);
				    }
				    if (writeMethod != null){
					addWriteMethod(propertyName, writeMethod);
				    }
				    types.put(propertyName, type);
				}
			    });
		}

		public void addReadMethod(String name, Object m) {
			readMethods.put(name, m);
		}

		public void addWriteMethod(String name, Object m) {
			writeMethods.put(name, m);
		}

		public Object findReadMethod(String name) {
			return readMethods.get(name);
		}

		public Object findWriteMethod(String name) {
			return writeMethods.get(name);
		}

		public Class getType(String name) {
			return (Class) types.get(name);
		}
	}

	public static String getProperty(final String key) {
		Properties p = Pnuts.defaultSettings;
		if (p != null) {
			String v = p.getProperty(key);
			if (v != null) {
				return v;
			}
		}
		try {
			String v = (String)AccessController
					.doPrivileged(new PrivilegedAction() {
						public Object run() {
							return System.getProperty(key);
						}
					});
			if (v != null){
				return v;
			}
		} catch (Exception e) {
			// skip
		}
		if (defaultProperties != null){
			return defaultProperties.getProperty(key);
		} else {
			return null;
		}
	}

	private Map beanAccessors = createWeakMap();

	static class BeanInfoParam {
		Class targetClass;

		Class stopClass;

		BeanInfoParam(Class targetClass, Class stopClass) {
			this.targetClass = targetClass;
			this.stopClass = stopClass;
		}

		public int hashCode() {
			return targetClass.hashCode() ^ stopClass.hashCode();
		}

		public boolean equals(Object that) {
			if (that instanceof BeanInfoParam) {
				BeanInfoParam p = (BeanInfoParam) that;
				return p.targetClass == this.targetClass
						&& p.stopClass == this.stopClass;
			}
			return false;
		}
	}

	private Accessor getAccessor(Class cls, Class stopClass) {
		Object key;
		if (stopClass == null) {
			key = cls;
		} else {
			key = new BeanInfoParam(cls, stopClass);
		}
		java.lang.ref.SoftReference ref = (java.lang.ref.SoftReference) beanAccessors
				.get(key);
		if (ref == null) {
			Accessor a = createBeanAccessor(cls, stopClass);
			beanAccessors.put(key, new java.lang.ref.SoftReference(a));
			return a;
		} else {
			Accessor a = (Accessor) ref.get();
			if (a == null) {
				a = createBeanAccessor(cls, stopClass);
				beanAccessors.put(key, new java.lang.ref.SoftReference(a));
			}
			return a;
		}
	}

	private Accessor createBeanAccessor(Class cls, Class stopClass) {
		return new Accessor(cls, stopClass);
	}

	/**
	 * Sets a Bean property of the specified bean.
	 * 
	 * @param context
	 *            the context
	 * @param target
	 *            the target bean
	 * @param name
	 *            the Bean property name
	 * @param value
	 *            the value of the Bean property
	 */
	public static void setBeanProperty(Context context, Object target,
			String name, Object value) {
		try {
			context.runtime.setBeanProperty(target, name, value, null);
		} catch (IllegalAccessException e1) {
			throw new PnutsException(e1, context);
		} catch (InvocationTargetException e2) {
			throw new PnutsException(e2.getTargetException(), context);
		}
	}

	/**
	 * Gets a Bean property of the specified bean.
	 * 
	 * @param context
	 *            the context
	 * @param target
	 *            the target bean
	 * @param name
	 *            the Bean property name
	 */
	public static Object getBeanProperty(Context context, Object target,
			String name) {
		try {
			return context.runtime.getBeanProperty(target, name, null);
		} catch (IllegalAccessException e1) {
			throw new PnutsException(e1, context);
		} catch (InvocationTargetException e2) {
			throw new PnutsException(e2.getTargetException(), context);
		}
	}

	/**
	 * Gets a Bean property of the specified bean.
	 * 
	 * @param target
	 *            the target bean
	 * @param name
	 *            the Bean property name
	 */
	public Object getBeanProperty(Object target, String name)
			throws IllegalAccessException, InvocationTargetException {
		return getBeanProperty(target, name, null);
	}

	/**
	 * Gets a Bean property of the specified bean.
	 * 
	 * @param target
	 *            the target bean
	 * @param name
	 *            the Bean property name
	 * @param stopClass
	 *            the Introspector's "stopClass"
	 */
	protected Object getBeanProperty(Object target, String name, Class stopClass)
			throws IllegalAccessException, InvocationTargetException {
		Accessor a = getAccessor(target.getClass(), stopClass);
		Method readMethod = (Method) a.findReadMethod(name);
		if (readMethod != null) {
			try {
				return readMethod.invoke(target, noarg);
			} catch (IllegalAccessException e1) {
				Class cls = readMethod.getDeclaringClass();
				if (!Modifier.isPublic(cls.getModifiers())) {
					Method _m = findCallableMethod(cls, readMethod.getName(),
							readMethod.getParameterTypes());
					if (_m != null) {
						a.addReadMethod(name, _m);
						if (DEBUG) {
							System.out.println("addReadMethod " + _m);
						}
						try {
							return _m.invoke(target, noarg);
						} catch (IllegalAccessException iae) {
						}
					}
				}
				return Configuration.normalConfiguration.reInvoke(e1,
						readMethod, target, noarg);
			}
		} else {
			if (target instanceof Class) {
				Class cls = (Class) target;
				Field field = getField(cls, name);
				if (field != null && Modifier.isStatic(field.getModifiers())) {
					return field.get(null);
				}
			}
			if (a.findWriteMethod(name) == null){
			    Class cls = target.getClass();
			    Field field = getField(cls, name);
			    if (field != null && !Modifier.isStatic(field.getModifiers())) {
				return field.get(target);
			    }
			}
			throw new IllegalArgumentException("not readable property: target="
					+ target + ", fieldName=" + name);
		}
	}

	protected Cache fieldCache = createCache();

	protected Field getField(final Class cls, final String name) {
	    Cache fc = (Cache)fieldCache.get(cls);
	    if (fc == null){
		    fc = createCache();
		    fieldCache.put(cls, fc);
	    }
	    Field field = (Field)fc.get(name);
	    if (field == null){
		try {
		    field = (Field) AccessController
					.doPrivileged(new PrivilegedExceptionAction() {
						public Object run() throws Exception {
							return cls.getField(name);
						}
					});
		    fc.put(name, field);
		} catch (PrivilegedActionException e) {
		    return null;
		}
	    }
	    return field;
	}

	/**
	 * Sets a Bean property of the specified bean.
	 * 
	 * @param target
	 *            the target bean
	 * @param name
	 *            the Bean property name
	 * @param value
	 *            the new property value
	 */
	public void setBeanProperty(Object target, String name, Object value)
			throws IllegalAccessException, InvocationTargetException {
		setBeanProperty(target, name, value, null);
	}

	/**
	 * Sets a Bean property of the specified bean.
	 * 
	 * @param target
	 *            the target bean
	 * @param name
	 *            the Bean property name
	 * @param value
	 *            the new property value
	 * @param stopClass
	 *            the Introspector's "stopClass"
	 */
	protected void setBeanProperty(Object target, String name, Object value,
			Class stopClass) throws IllegalAccessException,
			InvocationTargetException {
		Accessor a = getAccessor(target.getClass(), stopClass);
		Method writeMethod = (Method) a.findWriteMethod(name);
		if (writeMethod != null) {
			Class type = writeMethod.getParameterTypes()[0];
			Object[] arg = null;
			try {
				if (type.isArray() && !type.isInstance(value)) {
					value = transform(type, value, null);
				} else if (type.isPrimitive()){
					value = primitive(type, value, false, 10);
				} else if (List.class.isAssignableFrom(type)){
					value = transform(type, value, null);
				}
				arg = new Object[] { value };
				writeMethod.invoke(target, arg);
				return;
			} catch (IllegalArgumentException iae){
				String msg = Runtime.getMessage("pnuts.lang.pnuts", 
								"type.mismatch",
								new Object[] {
									type,
									target,
									name,
									value,
									value.getClass().getName()
								});
				throw new IllegalArgumentException(msg);
			} catch (ClassCastException cce){
				String msg = Runtime.getMessage("pnuts.lang.pnuts", 
								"type.mismatch",
								new Object[] {
									type,
									target,
									name,
									value,
									value.getClass().getName()
								});
				throw new IllegalArgumentException(msg);
			} catch (IllegalAccessException e1) {
				Class cls = writeMethod.getDeclaringClass();
				if (!Modifier.isPublic(cls.getModifiers())) {
					Method _m = findCallableMethod(cls, writeMethod.getName(),
							writeMethod.getParameterTypes());
					if (_m != null) {
						a.addWriteMethod(name, _m);
						if (DEBUG) {
							System.out.println("addWriteMethod " + _m);
						}
						try {
							_m.invoke(target, arg);
							return;
						} catch (IllegalAccessException iae) {
						}
					}
				}
				Configuration.normalConfiguration.reInvoke(e1, writeMethod,
						target, arg);
				return;
			}
		} else {
			if (target instanceof Class) {
				Class cls = (Class) target;
				Field field = getField(cls, name);
				if (field != null && Modifier.isStatic(field.getModifiers())) {
					field.set(null, value);
					return;
				}
			}
			if (a.findReadMethod(name) == null){
			    Class cls = target.getClass();
			    Field field = getField(cls, name);
			    if (field != null && !Modifier.isStatic(field.getModifiers())) {
				field.set(target, value);
				return;
			    }
			}
			throw new IllegalArgumentException("not writable property: target="
					+ target + ", fieldName=" + name);
		}
	}

	/**
	 * Gets the type of a bean property
	 * 
	 * @param cls
	 *            the class of the bean
	 * @param name
	 *            the property name of the bean property
	 * @return the type of the property
	 */
	public Class getBeanPropertyType(Class cls, String name) {
		return getAccessor(cls, null).getType(name);
	}

	public static class Break extends RuntimeException {
		Object value;

		public Break(Object value) {
			this.value = value;
		}

		public Object getValue() {
			return value;
		}
	}

	public static class Continue extends RuntimeException {
	}

	public static boolean isGenerator(SimpleNode node) {
		int n = node.jjtGetNumChildren();
		for (int i = 0; i < n; i++) {
			SimpleNode c = node.jjtGetChild(i);
			if (c.id == PnutsParserTreeConstants.JJTFUNCTIONSTATEMENT) {
				continue;
			}
			if (c.id == PnutsParserTreeConstants.JJTYIELD) {
				return true;
			} else {
				if (isGenerator(c)) {
					return true;
				}
			}
		}
		return false;
	}

	static class RangeGenerator extends Generator {
		Generator gen;

		int pos = 0;

		int start, end;

		RangeGenerator(Generator gen, int start) {
			this(gen, start, -1);
		}

		RangeGenerator(Generator gen, int start, int end) {
			this.gen = gen;
			this.start = start;
			this.end = end;
		}

		public Object apply(final PnutsFunction closure, Context context) {
			return gen.apply(new PnutsFunction() {
				protected Object exec(Object[] args, Context c) {
					if (pos < start) {
						pos++;
						return null;
					} else if (start < 0 || end >= 0 && pos > end) {
						throw new Generator.Break(null);
					} else {
						pos++;
						return closure.call(args, c);
					}
				}
			}, context);
		}
	}

	public static Object applyGenerator(Generator g, PnutsFunction closure,
			Context context) {
		try {
			return g.apply(closure, context);
		} catch (Generator.Break brk) {
			return brk.getValue();
		}
	}

	public static void addImport(Context context, String name) {
		if (name.endsWith(".*")) {
			name = name.substring(0, name.length() - 2);
			context.addPackageToImport(name);
		} else if ("*".equals(name)) {
			context.addPackageToImport("");
		} else {
			context.addClassToImport(name);
		}
	}

	public static void addStaticMembers(Context context, String name,
			boolean wildcard) {
		context.addStaticMembers(name, wildcard);
	}

	protected static PnutsFunction defineUnboundFunction(Function f,
			String symbol, Package pkg) {
		Value v = pkg.lookup(symbol);
		if (v != null) {
			Object o = v.get();
			if (o instanceof PnutsFunction) {
				return f.register((PnutsFunction) o, true);
			}
		}
		return f.register(null);
	}

	static Object lookupTopLevelValue(String symbol, Context context) {
		Value v;
		ModuleList moduleList = context.moduleList;
		if (moduleList != null) {
			v = moduleList.resolve(symbol, context);
			if (v != null) {
				return v.get();
			}
		}
		Package parent = context.currentPackage.getParent();
		while (parent != null) {
			v = parent.lookup(symbol);
			if (v != null) {
				return v.get();
			}
			parent = parent.getParent();
		}
		return null;
	}

	protected static PnutsFunction defineTopLevelFunction(Function f,
			String symbol, Package pkg, Context context) {
		Value value = pkg.lookup(symbol);
		Object o = null;
		if (value != null) {
			o = value.get();
		}
		PnutsFunction pf;
		if (o instanceof PnutsFunction) {
			pf = f.register((PnutsFunction) o);
		} else {
			o = lookupTopLevelValue(symbol, context);
			if (o instanceof PnutsFunction) {
				pf = f.register((PnutsFunction) o, true);
			} else {
				pf = f.register(null);
			}
		}
		pkg.set(symbol, pf, context);
		return pf;
	}

	public static String unparse(SimpleNode node, Context context) {
		StringBuffer sbuf = new StringBuffer();
		Visitor unparseVisitor = new UnparseVisitor(sbuf);
		node.accept(unparseVisitor, context);
		return sbuf.toString();
	}

	static void recoverParseError(PnutsParser parser, int tokenType) {
		try {
		    Token t;
		    do {
			t = parser.getNextToken();
		    } while (t.kind != tokenType && t.kind != PnutsParserConstants.EOF);
		} catch (Exception e){
		    /* skip */
		}
	}

	static class ThreadLocalContext extends ThreadLocal {}
	static ThreadLocal threadContext = new ThreadLocalContext();

	/**
	 * Gets the context bound to the current thread
	 *
	 * @param context the context
	 */
	public static void setThreadContext(Context context){
		threadContext.set(context);
	}

	/**
	 * Sets the context bound to the current thread
	 *
	 * @return the context
	 */
	public static Context getThreadContext(){
		return (Context)threadContext.get();
	}

	static ImportEnv getDefaultImports(Context context){
		ImportEnv importEnv = new ImportEnv();
		String[] array = context.config.getDefaultImports();
		for (int i = 0; i < array.length; i++){
			String name = array[i];
			if (name.startsWith("static ")){
				if (name.endsWith(".*")) {
					name = name.substring(0, name.length() - 2);
					importEnv.addStaticMembers(name, true, context);
				} else {
					importEnv.addStaticMembers(name, false, context);
				}
			} else {
				if (name.endsWith(".*")) {
					name = name.substring(0, name.length() - 2);
					importEnv.addPackage(name);
				} else if ("*".equals(name)) {
					importEnv.addPackage("");
				} else {
					importEnv.addClass(name);
				}
			}
		}
		return importEnv;
	}

	public static interface FunctionSerializer {
		void serialize(PnutsFunction pnutsFunction, ObjectOutputStream s) throws IOException;
		void deserialize(PnutsFunction pnutsFunction, ObjectInputStream s) throws IOException, ClassNotFoundException;
	}

	static FunctionSerializer functionSerializer;
	static {
		try {
			Class cls = Class.forName("pnuts.lang.DefaultFunctionSerializer");
			functionSerializer = (FunctionSerializer)cls.newInstance();
		} catch (Exception e){
			try {
				Class cls = Class.forName("pnuts.lang.SimpleFunctionSerializer");
				functionSerializer = (FunctionSerializer)cls.newInstance();
			} catch (Exception e2) {
				// skip
			}
		}
	}

	static void serializePnutsFunction(PnutsFunction pnutsFunction, ObjectOutputStream s)
		throws IOException {
		functionSerializer.serialize(pnutsFunction, s);
	}

	static void deserializePnutsFunction(PnutsFunction pnutsFunction, ObjectInputStream s)
		throws IOException, ClassNotFoundException {
		functionSerializer.deserialize(pnutsFunction, s);
	}

	public static String saveNode(SimpleNode node){
		return NodeUtil.saveNode(node);
	}

	public static SimpleNode loadNode(String str) {
		return NodeUtil.loadNode(str);
	}

	private static boolean useCacheCleanerThread;
	static {
		String prop = getProperty("pnuts.lang.useCacheCleanerThread");
		useCacheCleanerThread = (prop == null || !prop.toLowerCase().equals("false"));
	}

	public static Map createWeakMap(){
		if (useCacheCleanerThread){
			return new org.pnuts.util.RefMap();
		} else {
			return new WeakHashMap();
		}
	}

	public static Cache createCache(){
		if (useCacheCleanerThread){
			return new MemoryCache(createWeakMap());
		} else {
			return new MemoryCache();
		}
	}

	/**
	public static String unparseNode(SimpleNode node){
		StringBuffer sbuf = new StringBuffer();
		node.accept(new org.pnuts.lang.UnparseVisitor(sbuf), null);
		return sbuf.toString();
	}
	**/

    /* experimental BIND feature */

    protected static void setupPropertyChangeListeners(Map table, Context context){
	for (Iterator it = table.entrySet().iterator(); it.hasNext(); ){
	    Map.Entry memberTargetEntry = (Map.Entry)it.next();
	    Object memberTarget = memberTargetEntry.getKey();
	    Map memberNameTable = (Map)memberTargetEntry.getValue(); // memberName -> list of [obj, property, rhs]
	    setupPropertyChangeListeners(memberTarget, memberNameTable, context);

	}
    }

    private static void setupPropertyChangeListeners(Object memberTarget, Map memberNameTable, Context context){
	PropertyChangeListener listener = new PropertyWatcher(memberNameTable, context);
	addPropertyChangeListener(memberTarget, listener, context);
    }

    private static void addPropertyChangeListener(Object obj, PropertyChangeListener listener, Context context){
	/*
	Class cls = obj.getClass();
	try {
	    Method addMethod = cls.getMethod("addPropertyChangeListener", new Class[]{PropertyChangeListener.class});
	    addMethod.invoke(obj, new Object[]{listener});
	} catch (Exception e){
	    throw new PnutsException(e, context);
	}
	*/
	callMethod(context,
		   obj.getClass(),
		   "addPropertyChangeListener",
		   new Object[]{listener},
		   new Class[]{PropertyChangeListener.class}, obj);
    }

    /*
     * Whenever memberTarget.memberName changes, rhs.accept(this, context) is evaluated and assigned to obj.property
     *
     * table = Map of (memberTarget -> Map of (memberName -> [obj, property, rhs])) is maintained somewhere,
     * and this method does the followings.
     * 1. if (table.memberTarget) == null, then table.memberTareget = map()
     * 2. if (table.memberTareget.memberName == null), then table.memberTareget.memberName = list()
     * 3. table.memberTareget.memberName.add([obj, property, rhs])
     *
     */
    protected static void watchProperty(Map table, Object obj, String property, Object memberTarget, String memberName, Callable rhs){
	if (canAcceptPropertyChangeListener(memberTarget)){
	    Map memberNameTable = (Map)table.get(memberTarget);
	    if (memberNameTable == null){
		memberNameTable = new HashMap();
		table.put(memberTarget, memberNameTable);
	    }
	    List tupleList = (List)memberNameTable.get(memberName);
	    if (tupleList == null){
		tupleList = new ArrayList();
		memberNameTable.put(memberName, tupleList);
	    }
	    tupleList.add(new Object[]{obj, property, rhs});
	}
    }


    private static boolean canAcceptPropertyChangeListener(Object obj){
	try {
	    Method addMethod = obj.getClass().getMethod("addPropertyChangeListener", new Class[]{PropertyChangeListener.class});
	    return true;
	} catch (Exception e){}
	return false;
    }

    protected static SimpleNode toFunctionNode(SimpleNode body){
	SimpleNode bodyClone = loadNode(saveNode(body));
	SimpleNode f = new SimpleNode(PnutsParserTreeConstants.JJTFUNCTIONSTATEMENT);
	SimpleNode param = new SimpleNode(PnutsParserTreeConstants.JJTLISTELEMENTS);
	f.jjtAddChild(bodyClone, 1);
	f.jjtAddChild(param, 0);
	bodyClone.jjtSetParent(f);
	param.jjtSetParent(f);
	return f;
    }
    

    static class PropertyWatcher implements PropertyChangeListener {
	private Map memberNameTable;
	private Context context;

	PropertyWatcher(Map memberNameTable, Context context){
	    this.memberNameTable = memberNameTable;
	    this.context = context;
	}

	public void propertyChange(PropertyChangeEvent e){
	    String name = e.getPropertyName();
	    List tuples = (List)memberNameTable.get(name);
	    if (tuples != null){
		int size = tuples.size();
		for (int i = 0; i < size; i++){
		    Object[] tuple = (Object[])tuples.get(i);
		    Object target = tuple[0];
		    String targetProperty = (String)tuple[1];
		    Callable rhs = (Callable)tuple[2];
		    Object value = rhs != null ? rhs.call(NO_PARAM, context) : e.getNewValue();
		    context.config.putField(context, target, targetProperty, value);
		}
	    }
	}
    }
}
