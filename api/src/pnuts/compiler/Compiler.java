package pnuts.compiler;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URL;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.pnuts.lang.ConstraintsTransformer;
import org.pnuts.lang.GeneratorHelper;
import org.pnuts.lang.NodeUtil;
import org.pnuts.lang.Signature;
import org.pnuts.lang.DefaultParseEnv;

import pnuts.lang.PnutsParser;
import pnuts.lang.Context;
import pnuts.lang.Function;
import pnuts.lang.Generator;
import pnuts.lang.ParseException;
import pnuts.lang.Pnuts;
import pnuts.lang.PnutsException;
import pnuts.lang.PnutsFunction;
import pnuts.lang.PnutsInterpreter;
import pnuts.lang.Runtime;
import pnuts.lang.SimpleNode;
import pnuts.lang.Visitor;
import pnuts.lang.PnutsParserTreeConstants;
import pnuts.lang.ParseEnvironment;

/**
 * Pnuts to JVM bytecode compiler
 */
public class Compiler extends Runtime implements Visitor {

	private final static boolean DEBUG = false;

	private final static boolean PROFILE = false;

	final static String SUPER = "super".intern();

	final static String THIS = "this".intern();

	final static String YIELD = "yield".intern();

	final static String STAR = "*".intern();

	final static String METHOD_FACTORY_FUNCTION = "$methodFactoryFunction";

	private static PnutsInterpreter interpreter = new PnutsInterpreter(); // fallback

	private static Preprocessor preproc = new Preprocessor();

	private static boolean optimize = getBoolean("pnuts.compiler.optimize");

	static boolean hasBootClassLoader = false;
	static {
		try {
			Class cls = ClassLoader.class;
			cls.getDeclaredConstructor(new Class[] { cls });
			hasBootClassLoader = true;
		} catch (Exception e) {
			/* skip */
		}
	}

	static boolean hasJava2Security = false;
	static {
		try {
			Class.class.getMethod("getProtectionDomain", new Class[] {});
			hasJava2Security = true;
		} catch (Exception e) {
			/* skip */
		}
	}

	static boolean hasValueOfPrimitive = false;
	static {
		try {
			Integer.class.getMethod("valueOf", new Class[]{int.class});
			hasValueOfPrimitive = true;
		} catch (Exception e){
			/* skip */
		}
	}

	static CodeLoaderFactory codeLoaderFactory;

	static CodeLoaderFactory privilegedCodeLoaderFactory;
	static {
		codeLoaderFactory = new CodeLoaderFactory();
		if (hasJava2Security) {
			privilegedCodeLoaderFactory = new PrivilegedCodeLoaderFactory();
		}
	}

	private static boolean proxyConf = getBoolean("pnuts.compiler.useDynamicProxy");

	private long s; // for profiling

	boolean _includeLineNo = !optimize;

	boolean _includeColumnNo = false;

	boolean _constantFolding = false;

	boolean traceMode = getBoolean("pnuts.compiler.traceMode");
	
	boolean includeMainMethod = false;

	private long classCount = 0L;

	boolean automatic;

	String className;

	String runtimeClassName;

	boolean useDynamicProxy = proxyConf;

	String sourceFile;

	public Compiler() {
		this(null);
	}

	public Compiler(String className) {
		this(className, true);
	}

	public Compiler(String className, boolean automatic) {
		this(className, automatic, proxyConf);
	}

	public Compiler(String className, boolean automatic, boolean useDynamicProxy) {
		if (className == null) {
			this.className = "_pnuts_";
		} else {
			this.className = className;
		}
		this.automatic = automatic;
		useDynamicProxy(useDynamicProxy);
	}

	public void includeLineNo(boolean flag) {
		this._includeLineNo = flag;
	}

	public void includeColumnNo(boolean flag) {
		this._includeColumnNo = flag;
	}

	public void setConstantFolding(boolean flag) {
		this._constantFolding = flag;
	}

	public void includeMainMethod(boolean flag) {
		this.includeMainMethod = flag;
	}

	public void setTraceMode(boolean mode){
		this.traceMode = mode;
	}
	
	public void useDynamicProxy(boolean flag) {
		this.useDynamicProxy = flag;
		if (flag) {
			runtimeClassName = "pnuts.compiler.DynamicRuntime";
		} else {
			runtimeClassName = "pnuts.lang.Runtime";
		}
	}

	void addLineInfo(CompileContext cc, int ctx, SimpleNode node){
		if (_includeLineNo || traceMode) {
			int line = node.beginLine;
			int column = node.beginColumn;
			ClassFile cf = cc.cf;
			if ((_includeColumnNo && (cc.line != line || cc.column != column)) || traceMode){
				cf.loadLocal(ctx);
				cf.pushInteger(line);
				cf.add(Opcode.INVOKESTATIC, "pnuts.lang.Runtime",
				       "setLine", "(Lpnuts/lang/Context;I)", "V");
				cc.line = line;
				cc.column = column;
				cf.addLineNumber(line);
			} else if (cc.line != line) {
				cf.loadLocal(ctx);
				cf.pushInteger(line);
				cf.add(Opcode.INVOKESTATIC, "pnuts.lang.Runtime",
				       "setLine", "(Lpnuts/lang/Context;I)", "V");
				cc.line = line;
				cf.addLineNumber(line);
			}
		}
	}

	protected Object execute(CompileContext cc, Context context,
			boolean catchJump) {
		if (DEBUG) {
			cc.debug();
		}
		try {
			Runtime rt;
			if (PROFILE) {
				s = System.currentTimeMillis();
				CodeLoader loader = createCodeLoader(context.getClassLoader(),
						true);
				Class cls = cc.loadClasses(loader);
				rt = (Runtime) cls.newInstance();
				System.out.println("load: " + (System.currentTimeMillis() - s));
			} else {
				CodeLoader loader = createCodeLoader(context.getClassLoader(),
						true);
				Class cls = cc.loadClasses(loader);
				rt = (Runtime) cls.newInstance();
			}
			return rt.run(context);
		} catch (IOException io) {
			throw new PnutsException(io, context);
		} catch (InstantiationException ie) {
			throw new PnutsException(ie, context);
		} catch (IllegalAccessException iae) {
			throw new PnutsException(iae, context);
		}
	}

	void preprocess(SimpleNode node) {
		node.accept(preproc, new TranslateContext());
	}

	public Object startSet(SimpleNode node, Context context) {
		if (!_includeLineNo && context.isVerbose()) {
			_includeLineNo = true;
		}

		preprocess(node);

		return start(node, context);
	}

	public Object start(SimpleNode node, Context context) {
		try {
			preprocess(node);
			return _start(node, context, true);
		} catch (LinkageError e) {
			if (context.isVerbose()){
				e.printStackTrace();
			} else {
				System.out.println(e);
			}
			if (automatic) {
				return node.accept(interpreter, context);
			} else {
				throw e;
			}
		}
	}

	Object _start(SimpleNode node, Context context, boolean catchJump) {

		if (PROFILE) {
			s = System.currentTimeMillis();
		}

		CompileContext cc;
		Object src = null;
		if (automatic) {
			cc = new CompileContext(context);
			src = getScriptSource(context);

		} else {
			cc = (CompileContext) context;

			src = cc.scriptSource;
		}
		if (src instanceof URL){
			String s = ((URL)src).toString();
			int idx = s.lastIndexOf('/');
			if (idx > 0){
				this.sourceFile = s.substring(idx + 1);
			}
		}
		cc.constClassName = className;
		cc.cf = new ClassFile(className, runtimeClassName, sourceFile,
				Constants.ACC_PUBLIC);
		ClassFile cf = cc.cf;
		cf.addInterface("pnuts.compiler.Compiled");

		/*
		 * constructor
		 */
		cf.openMethod("<init>", "()V", Constants.ACC_PUBLIC);
		cf.add(Opcode.ALOAD_0);
		cf.add(Opcode.INVOKESPECIAL, runtimeClassName, "<init>", "()", "V");
		cf.add(Opcode.RETURN);
		cf.closeMethod();

		/*
		 * public static void main(String[])
		 */
		if (includeMainMethod) {
			cf.openMethod("main", "([Ljava/lang/String;)V",
					(short) (Constants.ACC_PUBLIC | Constants.ACC_STATIC));
			cf.add(Opcode.NEW, className);
			cf.add(Opcode.DUP);
			cf.add(Opcode.INVOKESPECIAL, className, "<init>", "()", "V");

			Label catchStart = cf.getLabel(true);
			cf.add(Opcode.NEW, "pnuts.lang.Context");
			cf.add(Opcode.DUP);
			cf.add(Opcode.INVOKESPECIAL, "pnuts.lang.Context", "<init>", "()",
					"V");
			cf.add(Opcode.INVOKEINTERFACE, "pnuts.lang.Executable", "run",
					"(Lpnuts/lang/Context;)", "Ljava/lang/Object;");
			cf.add(Opcode.POP);
			cf.add(Opcode.RETURN);

			Label catchEnd = cf.getLabel(true);
			Label catchTarget = cf.getLabel(true);
			cf.reserveStack(1);
			cf.add(Opcode.INVOKEVIRTUAL, "pnuts.lang.Jump", "getValue", "()",
					"Ljava/lang/Object;");
			cf.add(Opcode.POP);
			cf.add(Opcode.RETURN);
			cf.addExceptionHandler(catchStart, catchEnd, catchTarget,
					"pnuts.lang.Jump");

			cf.closeMethod();
		}

		/*
		 * protected Object exec(Context context);
		 */
		cf.openMethod("exec", "(Lpnuts/lang/Context;)Ljava/lang/Object;",
				Constants.ACC_PROTECTED);

		int ctx = 1;
		cc.setContextIndex(ctx);

		int n = node.jjtGetNumChildren();
		if (n > 0) {
			int m = n - 1;
			for (int i = 0; i < m; i++) {
				accept(node, i, cc);
				cf.add(Opcode.POP);
			}
			accept(node, m, cc);
		} else {
			cf.add(Opcode.ACONST_NULL);
		}
		cf.add(Opcode.ARETURN);
		cf.closeMethod();

		/*
		 * static {} clause
		 */
		staticBlock(cf, null, cc);

		if (PROFILE) {
			System.out.println(className + " classFile: "
					+ (System.currentTimeMillis() - s));
		}

		if (automatic) {
			return execute(cc, context, catchJump);
		} else {
			return null;
		}
	}

	void staticBlock(ClassFile cf, SimpleNode closureNode, CompileContext cc){
		String className = cf.getClassName();

		cf.openMethod("<clinit>", "()V", Constants.ACC_STATIC);
		cf.addField("NO_PARAM", "[Ljava/lang/Object;", Constants.ACC_STATIC);
		cf.add(Opcode.ICONST_0);
		cf.add(Opcode.ANEWARRAY, "java.lang.Object");
		cf.add(Opcode.PUTSTATIC, className, "NO_PARAM", "[Ljava/lang/Object;");

                /**/
		if (cc.hasAttachMethod){
		    cf.add(Opcode.INVOKESTATIC, "pnuts.lang.Runtime", "getThreadContext", "()", "Lpnuts/lang/Context;");
		    int tmp = cf.getLocal();
		    cf.storeLocal(tmp);
		    cf.loadLocal(tmp);
		    Label end = cf.getLabel();
		    cf.add(Opcode.IFNULL, end);
		    cf.loadLocal(tmp);
		    cf.add(Opcode.INVOKESTATIC, className, "attach", "(Lpnuts/lang/Context;)", "V");
		    end.fix();
		}
                 /**/
		cf.addField(METHOD_FACTORY_FUNCTION, "Lpnuts/lang/PnutsFunction;", Constants.ACC_STATIC);

                if (closureNode != null){
                    closureNode.accept(this, cc);
		    cf.add(Opcode.PUTSTATIC, className, METHOD_FACTORY_FUNCTION, "Lpnuts/lang/PnutsFunction;");
		}

		for (Iterator it = cc.constants.keySet().iterator(); it.hasNext();) {
			Object obj = it.next();
			String k = (String) cc.constants.get(obj);
			if (DEBUG) {
				System.out.println(k + " : " + obj);
			}

			if (obj instanceof Integer) {
				int value = ((Integer) obj).intValue();
				cf.addField(k, "Ljava/lang/Integer;", Constants.ACC_STATIC);
				cf.add(Opcode.NEW, "java.lang.Integer");
				cf.add(Opcode.DUP);
				cf.pushInteger(value);
				cf.add(Opcode.INVOKESPECIAL, "java.lang.Integer", "<init>",
						"(I)", "V");
				cf.add(Opcode.PUTSTATIC, className, k, "Ljava/lang/Integer;");
			} else if (obj instanceof Byte) {
				byte value = ((Byte) obj).byteValue();
				cf.addField(k, "Ljava/lang/Byte;", Constants.ACC_STATIC);
				cf.add(Opcode.NEW, "java.lang.Byte");
				cf.add(Opcode.DUP);
				cf.pushInteger((int) value);
				cf.add(Opcode.INVOKESPECIAL, "java.lang.Byte", "<init>", "(B)",
						"V");
				cf.add(Opcode.PUTSTATIC, className, k, "Ljava/lang/Byte;");
			} else if (obj instanceof Character) {
				char value = ((Character) obj).charValue();
				cf.addField(k, "Ljava/lang/Character;", Constants.ACC_STATIC);
				cf.add(Opcode.NEW, "java.lang.Character");
				cf.add(Opcode.DUP);
				cf.pushInteger((int) value);
				cf.add(Opcode.INVOKESPECIAL, "java.lang.Character", "<init>",
						"(C)", "V");
				cf.add(Opcode.PUTSTATIC, className, k, "Ljava/lang/Character;");
			} else if (obj instanceof Long) {
				long value = ((Long) obj).longValue();
				cf.addField(k, "Ljava/lang/Long;", Constants.ACC_STATIC);
				cf.add(Opcode.NEW, "java.lang.Long");
				cf.add(Opcode.DUP);
				cf.pushLong(value);
				cf.add(Opcode.INVOKESPECIAL, "java.lang.Long", "<init>", "(J)",
						"V");
				cf.add(Opcode.PUTSTATIC, className, k, "Ljava/lang/Long;");
			} else if (obj instanceof Float) {
				float value = ((Float) obj).floatValue();
				cf.addField(k, "Ljava/lang/Float;", Constants.ACC_STATIC);
				cf.add(Opcode.NEW, "java.lang.Float");
				cf.add(Opcode.DUP);
				cf.pushFloat(value);
				cf.add(Opcode.INVOKESPECIAL, "java.lang.Float", "<init>",
						"(F)", "V");
				cf.add(Opcode.PUTSTATIC, className, k, "Ljava/lang/Float;");
			} else if (obj instanceof Double) {
				double value = ((Double) obj).doubleValue();
				cf.addField(k, "Ljava/lang/Double;", Constants.ACC_STATIC);
				cf.add(Opcode.NEW, "java.lang.Double");
				cf.add(Opcode.DUP);
				cf.pushDouble(value);
				cf.add(Opcode.INVOKESPECIAL, "java.lang.Double", "<init>",
						"(D)", "V");
				cf.add(Opcode.PUTSTATIC, className, k, "Ljava/lang/Double;");
			} else if (obj instanceof BigDecimal) {
				cf.addField(k, "Ljava/math/BigDecimal;", Constants.ACC_STATIC);
				cf.add(Opcode.NEW, "java.math.BigDecimal");
				cf.add(Opcode.DUP);
				cf.pushString(obj.toString());
				cf.add(Opcode.INVOKESPECIAL, "java.math.BigDecimal", "<init>",
						"(Ljava/lang/String;)", "V");
				cf.add(Opcode.PUTSTATIC, className, k,
				       "Ljava/math/BigDecimal;");
			} else if (obj instanceof BigInteger) {
				cf.addField(k, "Ljava/math/BigInteger;", Constants.ACC_STATIC);
				cf.add(Opcode.NEW, "java.math.BigInteger");
				cf.add(Opcode.DUP);
				cf.pushString(obj.toString());
				cf.add(Opcode.INVOKESPECIAL, "java.math.BigInteger", "<init>",
						"(Ljava/lang/String;)", "V");
				cf.add(Opcode.PUTSTATIC, className, k,
				       "Ljava/math/BigInteger;");
			}
		}

		cf.add(Opcode.RETURN);
		cf.closeMethod();
	}

	public Object expressionList(SimpleNode node, Context context) {
		CompileContext cc = (CompileContext) context;
		ClassFile cf = cc.cf;

		int n = node.jjtGetNumChildren();
		if (n > 0) {
			int m = n - 1;
			for (int i = 0; i < m; i++) {
				accept(node, i, context);
				cf.add(Opcode.POP);
			}
			accept(node, m, context);
		} else {
			cf.add(Opcode.ACONST_NULL);
		}
		return null;
	}

	public Object integerNode(SimpleNode node, Context context) {
		CompileContext cc = (CompileContext) context;
		ClassFile cf = cc.cf;
		String str = node.str;
		Object p[] = (Object[]) node.info;
		Number n = (Number) p[0];

		String assoc = (String) cc.constants.get(n);
		if (assoc == null) {
			assoc = gensym(context);
			cc.constants.put(n, assoc);
		}

		if (n instanceof Integer) {
			cf.add(Opcode.GETSTATIC, cc.constClassName, assoc,
					"Ljava/lang/Integer;");
		} else if (n instanceof Long) {
			cf.add(Opcode.GETSTATIC, cc.constClassName, assoc,
					"Ljava/lang/Long;");
		} else if (n instanceof Byte) {
			cf.add(Opcode.GETSTATIC, cc.constClassName, assoc,
					"Ljava/lang/Byte;");
		} else if (n instanceof BigInteger) {
			cf.add(Opcode.GETSTATIC, cc.constClassName, assoc,
					"Ljava/math/BigInteger;");
		} else if (n instanceof BigDecimal) {
			cf.add(Opcode.GETSTATIC, cc.constClassName, assoc,
					"Ljava/math/BigDecimal;");
		} else {
			throw new InternalError("compiler error");
		}

		if (p[1] != null) {
			addLineInfo(cc, cc.getContextIndex(), node);
			
			int offset = ((int[]) p[1])[0];
			cf.add(Opcode.CHECKCAST, "java.lang.Number");

			cf.pushString(str.substring(0, offset));
			cf.add(Opcode.LDC, cf.addConstant(str.substring(offset)));
			cf.loadLocal(cc.getContextIndex());
			cf.add(Opcode.INVOKESTATIC,
			       "pnuts.lang.Runtime",
			       "quantity",
			       "(Ljava/lang/Number;Ljava/lang/String;Ljava/lang/String;Lpnuts/lang/Context;)",
			       "Ljava/lang/Object;");
		}
		return null;
	}

	public Object floatingNode(SimpleNode node, Context context) {
		CompileContext cc = (CompileContext) context;
		ClassFile cf = cc.cf;
		String str = node.str;
		Object p[] = (Object[]) node.info;
		Number n = (Number) p[0];

		String assoc = (String) cc.constants.get(n);
		if (assoc == null) {
			assoc = gensym(context);
			cc.constants.put(n, assoc);
		}
		if (n instanceof Float) {
			cf.add(Opcode.GETSTATIC, cc.constClassName, assoc,
					"Ljava/lang/Float;");
		} else if (n instanceof Double) {
			cf.add(Opcode.GETSTATIC, cc.constClassName, assoc,
					"Ljava/lang/Double;");
		} else if (n instanceof BigDecimal) {
			cf.add(Opcode.GETSTATIC, cc.constClassName, assoc,
					"Ljava/math/BigDecimal;");
		} else {
			throw new InternalError("compiler error");
		}
		if (p[1] != null) {
			addLineInfo(cc, cc.getContextIndex(), node);
			
			int offset = ((int[]) p[1])[0];
			cf.add(Opcode.CHECKCAST, "java.lang.Number");

			cf.add(Opcode.LDC, cf.addConstant(str.substring(0, offset)));
			cf.add(Opcode.LDC, cf.addConstant(str.substring(offset)));
			cf.loadLocal(cc.getContextIndex());
			cf.add(
			       Opcode.INVOKESTATIC,
			       "pnuts.lang.Runtime",
			       "quantity",
			       "(Ljava/lang/Number;Ljava/lang/String;Ljava/lang/String;Lpnuts/lang/Context;)",
			       "Ljava/lang/Object;");
		}
		return null;
	}

	public Object stringNode(SimpleNode node, Context context) {
		CompileContext cc = (CompileContext) context;
		ClassFile cf = cc.cf;
		String str = node.str;
		if (str != null){
		    cf.pushString(node.str);
		} else {
		    cf.add(Opcode.NEW, "java.lang.StringBuffer");
		    cf.add(Opcode.DUP);
		    cf.add(Opcode.INVOKESPECIAL, "java.lang.StringBuffer", "<init>", "()", "V");
		    int n = node.jjtGetNumChildren();
		    for (int i = 0; i < n; i++){
			SimpleNode c = node.jjtGetChild(i);
			if (c.id == PnutsParserTreeConstants.JJTSTRINGNODE){
			    cf.pushString(c.str);
			} else {
			    c.accept(this, context);
			    cf.add(Opcode.INVOKESTATIC,
				   "java.lang.String",
				   "valueOf",
				   "(Ljava/lang/Object;)",
				   "Ljava/lang/String;");
			}
			cf.add(Opcode.INVOKEVIRTUAL,
			       "java.lang.StringBuffer",
			       "append",
			       "(Ljava/lang/String;)",
			       "Ljava/lang/StringBuffer;");
		    }
		    cf.add(Opcode.INVOKEVIRTUAL, "java.lang.StringBuffer", "toString", "()", "Ljava/lang/String;");
		}
		return null;
	}

	public Object characterNode(SimpleNode node, Context context) {
		CompileContext cc = (CompileContext) context;
		ClassFile cf = cc.cf;
		Character ch = (Character) node.info;
		String assoc = (String) cc.constants.get(ch);
		if (assoc == null) {
			assoc = gensym(context);
			cc.constants.put(ch, assoc);
		}
		cf.add(Opcode.GETSTATIC, cc.constClassName, assoc,
				"Ljava/lang/Character;");
		return null;
	}

	public Object classNode(SimpleNode node, Context context) {
		CompileContext cc = (CompileContext) context;
		ClassFile cf = cc.cf;
		int ctx = cc.getContextIndex();
		addLineInfo(cc, ctx, node);

		SimpleNode c = node.jjtGetChild(0);
		if (c.id == PnutsParserTreeConstants.JJTCLASSNAME){
		    StringBuffer sbuf = new StringBuffer();
		    sbuf.append(c.jjtGetChild(0).str);
		    int n = c.jjtGetNumChildren();
		    for (int i = 1; i < n; i++) {
			SimpleNode ch = c.jjtGetChild(i);
			sbuf.append('.');
			sbuf.append(ch.str);
		    }
		    String name = sbuf.toString();
		    cf.add(Opcode.LDC, cf.addConstant(name));
		} else {
		    c.accept(this, context);
		    cf.add(Opcode.CHECKCAST, "java.lang.String");
		}
		cf.loadLocal(ctx);
		cf.add(Opcode.INVOKESTATIC, "pnuts.lang.Pnuts", "loadClass",
		       "(Ljava/lang/String;Lpnuts/lang/Context;)",
		       "Ljava/lang/Class;");

		return null;
	}

	static class MethodSignatureInfo {
		String methodName;
		SimpleNode returnTypeNode;
		SimpleNode[] paramTypeNodes;
		SimpleNode fnode;
                Map/*<String,Collection<SimpleNode>>*/ thisReferences;
                Map/*<String,SimpleNode>*/ paramReferences;
	}

	static class ConstructorSignatureInfo extends MethodSignatureInfo{
	}

	static class FieldSignatureInfo {
		String fieldName;
		SimpleNode typeNode;
		//SimpleNode lhs;
		SimpleNode rhs;
	}

	/*
	 * Transform methodef to function
	 * Also, set attributes of MethodSignatureInfo
	 */
	static SimpleNode buildMethodFunction(SimpleNode methodDef,
					      MethodSignatureInfo siginfo,
                                                Set /**/ superMethodNames)
	{
		SimpleNode n1 = methodDef.jjtGetChild(0);
		SimpleNode param;
		SimpleNode block;
		SimpleNode returnTypeNode = null;
		String symbol = methodDef.str;
		siginfo.methodName = symbol;

		if (n1.id == PnutsParserTreeConstants.JJTCLASSNAME ||
		    n1.id == PnutsParserTreeConstants.JJTARRAYTYPE){
			returnTypeNode = n1;
			param = methodDef.jjtGetChild(1);
			block = methodDef.jjtGetChild(2);
		} else {
			param = methodDef.jjtGetChild(0);
			block = methodDef.jjtGetChild(1);
		}

		SimpleNode newParam = new SimpleNode(
				PnutsParserTreeConstants.JJTPARAMLIST);

		int nargs = param.jjtGetNumChildren();
		SimpleNode[] paramTypeNodes = new SimpleNode[nargs];
                siginfo.paramReferences = new HashMap();
		for (int k = 0; k < nargs; k++) {
			SimpleNode c = param.jjtGetChild(k);
			SimpleNode cp = param.jjtGetChild(k);
			if (c.jjtGetNumChildren() > 1){
				cp = c.jjtGetChild(1);
				paramTypeNodes[k] = c.jjtGetChild(0);
			} else {
				cp = c.jjtGetChild(0);
			}
                        siginfo.paramReferences.put(cp.str, cp);
			newParam.jjtAddChild(cp, k);
		}
		siginfo.paramTypeNodes = paramTypeNodes;
		siginfo.returnTypeNode = returnTypeNode;
		SimpleNode fnode = new SimpleNode(
				PnutsParserTreeConstants.JJTFUNCTIONSTATEMENT);
		fnode.setAttribute("isMethod", Boolean.TRUE);
		fnode.info = methodDef.info;
		fnode.setAttribute("frameInfo", methodDef.getAttribute("frameInfo"));
		fnode.str = symbol;
		fnode.jjtAddChild(newParam, 0);
		fnode.jjtAddChild(block, 1);
		fnode.jjtSetParent(methodDef.jjtGetParent());
                Map/*<String,Collection<SimpleNode>>*/ refs = collectThisReferences(block, superMethodNames);
                if (DEBUG){
                    System.out.println("ref = " + refs);
                }
                siginfo.thisReferences = refs;
		return fnode;
	}

        static Map/*<String,SimpleNode>*/ collectThisReferences(SimpleNode node, Set superMethodNames){
                Map refs = new HashMap();
                collectThisReferences(node, refs, superMethodNames);
		return refs;
        }
        
        static void collectThisReferences(SimpleNode node, Map/*<String,SimpleNode>*/ frefs, Set superMethodNames){
            int n = node.jjtGetNumChildren();
            for (int i = 0; i < n; i++){
                SimpleNode c = node.jjtGetChild(i);
                if (c.id == PnutsParserTreeConstants.JJTMEMBERNODE){
                    SimpleNode lhs = c.jjtGetChild(0);
                    if ((lhs.id == PnutsParserTreeConstants.JJTIDNODE) &&
                        lhs.str == THIS){
                        Collection refset = (Collection)frefs.get(c.str);
                        if (refset == null){
                            frefs.put(c.str, refset = new ArrayList());
                        }
                        refset.add(c);
                    }
                } else if (c.id == PnutsParserTreeConstants.JJTMETHODNODE){
                    SimpleNode target = c.jjtGetChild(0);
                    if (target.id == PnutsParserTreeConstants.JJTIDNODE && target.str == "super"){
                        SimpleNode args = c.jjtGetChild(1);
                        superMethodNames.add(c.str);
                    }
                } else if (c.id != PnutsParserTreeConstants.JJTCLASSDEFBODY){
                    collectThisReferences(c, frefs, superMethodNames);
                }
            }
        }
        
	/*
	 * Examine the methodDef node and returns MethodSignatureInfo
	 * Also, define a function in the Package object stored at pkg
	 */
    static MethodSignatureInfo handleMethodDef(SimpleNode methodDef, Set/**/ superMethodNames)
	{
		MethodSignatureInfo siginfo = new MethodSignatureInfo();
		SimpleNode fnode = buildMethodFunction(methodDef, siginfo, superMethodNames);
		siginfo.fnode = fnode;
                if (DEBUG){
                    System.out.println(siginfo.thisReferences);
                    System.out.println(siginfo.paramReferences);
                }
		return siginfo;
	}

    static ConstructorSignatureInfo handleConstructorDef(SimpleNode defNode, Set superMethodNames)
    {
	ConstructorSignatureInfo siginfo = new ConstructorSignatureInfo();
	SimpleNode param = defNode.jjtGetChild(0);
	SimpleNode block = defNode.jjtGetChild(1);
	SimpleNode first = null;

	SimpleNode newParam = new SimpleNode(PnutsParserTreeConstants.JJTPARAMLIST);

	int nargs = param.jjtGetNumChildren();
	SimpleNode[] paramTypeNodes = new SimpleNode[nargs];
	siginfo.paramReferences = new HashMap();
	for (int k = 0; k < nargs; k++) {
	    SimpleNode c = param.jjtGetChild(k);
	    SimpleNode cp = param.jjtGetChild(k);
	    if (c.jjtGetNumChildren() > 1){
		cp = c.jjtGetChild(1);
		paramTypeNodes[k] = c.jjtGetChild(0);
	    } else {
		cp = c.jjtGetChild(0);
	    }
	    siginfo.paramReferences.put(cp.str, cp);
	    newParam.jjtAddChild(cp, k);
	}
	siginfo.paramTypeNodes = paramTypeNodes;

	SimpleNode fnode = new SimpleNode(PnutsParserTreeConstants.JJTFUNCTIONSTATEMENT);
	fnode.setAttribute("isMethod", Boolean.TRUE);
	fnode.info = defNode.info;
	fnode.setAttribute("frameInfo", defNode.getAttribute("frameInfo"));
	siginfo.fnode = fnode;

	fnode.jjtAddChild(newParam, 0);
	fnode.jjtAddChild(block, 1);

        Map refs = collectThisReferences(block, superMethodNames);
        if (DEBUG){
            System.out.println("ref = " + refs);
         }
         siginfo.thisReferences = refs;        
        
	return siginfo;
    }

	static FieldSignatureInfo handleFieldDef(SimpleNode defNode){
		FieldSignatureInfo finfo = new FieldSignatureInfo();
		finfo.fieldName = defNode.str;
		boolean hasTypeNode = (defNode.jjtGetChild(0).id == PnutsParserTreeConstants.JJTCLASSNAME);
		if (hasTypeNode){
		    finfo.typeNode = defNode.jjtGetChild(0);
		}
		if (defNode.jjtGetNumChildren() > 1){
			finfo.rhs = defNode.jjtGetChild(1);
		} else if (!hasTypeNode){
			finfo.rhs = defNode.jjtGetChild(0);
		}
		//finfo.lhs = defNode.jjtGetChild(0);
		return finfo;
	}

	static void handleClassDefBody(SimpleNode classDefBody,
				List/*<MethodSignatureInfo>*/ methodSigs,
				List/*<ConstructorSignatureInfo>*/ constructorSigs,
				List/*<FieldSignatureInfo>*/ fieldSigs,
                                Set superMethodNames)
	{
		for (int i = 0; i < classDefBody.jjtGetNumChildren(); i++) {
			SimpleNode defNode = classDefBody.jjtGetChild(i);
			if (defNode.id == PnutsParserTreeConstants.JJTFIELDDEF){
			    fieldSigs.add(handleFieldDef(defNode));
			} else {
			    SimpleNode n0 = defNode.jjtGetChild(0);
			    if (n0.id != PnutsParserTreeConstants.JJTCLASSNAME &&
				n0.id != PnutsParserTreeConstants.JJTARRAYNODE)
			    {
				SimpleNode parent = classDefBody.jjtGetParent();
				if (parent.id == PnutsParserTreeConstants.JJTCLASSSCRIPT ||
				    parent.id == PnutsParserTreeConstants.JJTCLASSDEF)
				{
				    if (defNode.str.equals(getClassName(parent.jjtGetChild(0)))){
					/* constructor */
					constructorSigs.add(handleConstructorDef(defNode, superMethodNames));
					continue;
				    }
				}
			    }
			    methodSigs.add(handleMethodDef(defNode, superMethodNames));
			}
		}
	}
    
	static void buildFieldDeclaration(FieldSignatureInfo fsig, List assignmentNodes)
	{
		if (fsig.rhs != null){
		    /*
		     * this.fieldName = rhs
		     */
		    SimpleNode assign = new SimpleNode(PnutsParserTreeConstants.JJTASSIGNMENT);
		    SimpleNode member = new SimpleNode(PnutsParserTreeConstants.JJTMEMBERNODE);
		    SimpleNode id = new SimpleNode(PnutsParserTreeConstants.JJTIDNODE);
		    id.str = THIS;
		    member.str = fsig.fieldName;
		    member.jjtAddChild(id, 0);
		    assign.jjtAddChild(fsig.rhs, 1);
		    assign.jjtAddChild(member, 0);
		    assignmentNodes.add(assign);
		}
	}
    

	/**
	 * Convert 'new type(){...}' to 'class name extends type {...}'
	 */
	SimpleNode getClassDefNode(SimpleNode node, Context cc){
		SimpleNode n0 = node.jjtGetChild(0);
		String superclassName = getClassName(n0);
		Class[] interfaces = null;
		Class superclass = null;

                ClassSpec supertypeSpec = ClassSpec.create(superclassName, cc);
		Class supertype = supertypeSpec.compileTimeClass;
                superclassName = supertypeSpec.className;

		String className = superclassName.replace('.', '_') + "__adapter" + "$" + (classCount++ & 0x7fffffffffffffffL);
		SimpleNode classDefBody = node.jjtGetChild(2);
                
		List/*<MethodSignatureInfo>*/ methodSigs = new ArrayList();
		List/*<ConstructorSignatureInfo>*/ constructorSigs = new ArrayList();
		List/*<FieldSignatureInfo>*/ fieldSigs = new ArrayList();
                Set superMethodNames = new HashSet();
		handleClassDefBody(classDefBody, methodSigs, constructorSigs, fieldSigs, superMethodNames);
		
		return getClassDefNode(className,
				       supertype != null && supertype.isInterface(),
				       n0,
				       classDefBody);
	}

	static SimpleNode getClassDefNode(String className,
					  boolean isInterface,
					  SimpleNode typeNode,
					  SimpleNode classDefBody)
	{
		SimpleNode def = new SimpleNode(PnutsParserTreeConstants.JJTCLASSDEF);
		SimpleNode classNameNode = new SimpleNode(PnutsParserTreeConstants.JJTCLASSNAME);
		SimpleNode _p = new SimpleNode(PnutsParserTreeConstants.JJTPACKAGE);
		_p.str = className;
		classNameNode.jjtAddChild(_p, 0);
		_p.jjtSetParent(classNameNode);
		SimpleNode ext = new SimpleNode(PnutsParserTreeConstants.JJTEXTENDS);
		SimpleNode imp = new SimpleNode(PnutsParserTreeConstants.JJTIMPLEMENTS);
		def.jjtAddChild(classNameNode, 0);
		classNameNode.jjtSetParent(def);
		if (isInterface){
			imp.jjtAddChild(typeNode, 0);
			typeNode.jjtSetParent(imp);
		} else {
			ext.jjtAddChild(typeNode, 0);
			typeNode.jjtSetParent(ext);
		}
		def.jjtAddChild(ext, 1);
		ext.jjtSetParent(def);
		def.jjtAddChild(imp, 2);
		imp.jjtSetParent(def);
		def.jjtAddChild(classDefBody, 3);
		classDefBody.jjtSetParent(def);
		return def;
	}

	public Object newNode(SimpleNode node, Context context) {
		CompileContext cc = (CompileContext) context;
		ClassFile cf = cc.cf;
		int ctx = cc.getContextIndex();
		addLineInfo(cc, ctx, node);
		
		SimpleNode n0 = node.jjtGetChild(0);

		int n = node.jjtGetNumChildren();
		if (n == 3 && node.jjtGetChild(2).id == PnutsParserTreeConstants.JJTCLASSDEFBODY) { // subclass
		    String encodedNode = NodeUtil.saveNode(node);
		    cf.add(Opcode.LDC, cf.addConstant(encodedNode));
		    cf.loadLocal(ctx);
		    cf.add(Opcode.INVOKESTATIC,
			   "pnuts.compiler.Compiler",
			   "buildSubclassInstance",
			   "(Ljava/lang/String;Lpnuts/lang/Context;)",
			   "Ljava/lang/Object;");

		} else if (n0.id == PnutsParserTreeConstants.JJTINDEXNODE) { // instantiation
			Object[] idx = parseIndex(n0);
			Object[] dim = (Object[]) idx[1];
			SimpleNode nameNode = (SimpleNode) idx[0];

			if (node.getAttribute("hasTryStatement") != null){
				int[] vars = new int[dim.length];
				for (int i = 0; i < dim.length; i++) {
					int var = cf.getLocal();
					((SimpleNode) dim[i]).accept(this, context);
					cf.storeLocal(var);
					vars[i] = var;
				}
				resolveClassName(nameNode, cc, ctx);
				cf.pushInteger(dim.length);
				cf.add(Opcode.NEWARRAY, Opcode.T_INT);
				for (int i = 0; i < dim.length; i++) {
					cf.add(Opcode.DUP);
					cf.pushInteger(i);
					cf.loadLocal(vars[i]);
					cf.add(Opcode.CHECKCAST, "java.lang.Number");
					cf.add(Opcode.INVOKEVIRTUAL, "java.lang.Number",
							"intValue", "()", "I");
					cf.add(Opcode.IASTORE);
				}
				for (int i = 0; i < dim.length; i++) {
					cf.freeLocal(vars[i]);
				}
			} else {

				resolveClassName(nameNode, cc, ctx);

				cf.pushInteger(dim.length);
				cf.add(Opcode.NEWARRAY, Opcode.T_INT);
				for (int i = 0; i < dim.length; i++) {
					cf.add(Opcode.DUP);
					cf.pushInteger(i);
					((SimpleNode) dim[i]).accept(this, context);

					cf.add(Opcode.CHECKCAST, "java.lang.Number");
					cf.add(Opcode.INVOKEVIRTUAL, "java.lang.Number",
							"intValue", "()", "I");
					cf.add(Opcode.IASTORE);
				}
			}
			cf.add(Opcode.INVOKESTATIC, "java.lang.reflect.Array",
					"newInstance", "(Ljava/lang/Class;[I)",
					"Ljava/lang/Object;");

		} else if (n0.id == PnutsParserTreeConstants.JJTARRAYTYPE) {
			arrayType(n0, context);

		} else if (n0.id == PnutsParserTreeConstants.JJTARRAYNODE) {
			cf.loadLocal(ctx);
			accept(n0, 0, context);
			cf.add(Opcode.CHECKCAST, "java.lang.Class");
			//	    accept(n0, 1, context);
			_listElements(n0.jjtGetChild(1), context);
			cf.add(Opcode.ICONST_1);
			cf.add(Opcode.INVOKESTATIC,
			       "pnuts.lang.Runtime",
			       "cast",
			       "(Lpnuts/lang/Context;Ljava/lang/Class;Ljava/lang/Object;Z)",
			       "Ljava/lang/Object;");

		} else { // instantiation

			if (node.getAttribute("hasTryStatement") != null){
				int arg = cf.getLocal();
				//		accept(node, 1, context);
				_listElements(node.jjtGetChild(1), context);
				cf.storeLocal(arg);
				cf.loadLocal(ctx);
				SimpleNode nameNode = node.jjtGetChild(0);
				resolveClassName(nameNode, cc, ctx);
				cf.loadLocal(arg);
				cf.freeLocal(arg);
			} else {
				cf.loadLocal(ctx);
				SimpleNode nameNode = node.jjtGetChild(0);
				resolveClassName(nameNode, cc, ctx);
				//		accept(node, 1, context);
				_listElements(node.jjtGetChild(1), context);
			}
			cf.add(Opcode.CHECKCAST, "[Ljava/lang/Object;");
			cf.add(Opcode.ACONST_NULL);
			cf.add(Opcode.INVOKESTATIC,
			       "pnuts.lang.Runtime",
			       "callConstructor",
			       "(Lpnuts/lang/Context;Ljava/lang/Class;[Ljava/lang/Object;[Ljava/lang/Class;)",
			       "Ljava/lang/Object;");
		}

		return null;
	}

	public Object classDef(SimpleNode node, Context context){
		CompileContext cc = (CompileContext)context;
		ClassFile cf = cc.cf;
		int ctx = cc.getContextIndex();
                ClassGenerationResult r = generateClass(node, cc);
                ByteArrayOutputStream bout = new ByteArrayOutputStream();
                try {
                        r.classFile.write(bout);
                } catch (IOException ioe){
                        ioe.printStackTrace();
                }
                byte[] bcode = bout.toByteArray();
                String encodedBytecode = NodeUtil.byteArrayToString(bcode, bcode.length);
		if (DEBUG){
		        System.out.println(encodedBytecode.length());
		}
		String className = r.classFile.getClassName();
		if (className != null){
		    cf.add(Opcode.LDC, cf.addConstant(className));
		} else {
		    cf.add(Opcode.ACONST_NULL);
		}
                cf.add(Opcode.LDC, cf.addConstant(encodedBytecode));
                r.closureNode.accept(this, cc);
                cf.loadLocal(ctx);
                cf.add(Opcode.INVOKESTATIC,
                        "pnuts.compiler.Compiler",
                        "loadBytecode",
                        "(Ljava/lang/String;Ljava/lang/String;Lpnuts/lang/PnutsFunction;Lpnuts/lang/Context;)",
                        "Ljava/lang/Class;");
                return null;
	}

        static CodeLoader getCodeLoader(Context context){
	    ClassLoader cl = context.getCodeLoader();
	    if (cl instanceof CodeLoader){
		return (CodeLoader)cl;
	    }
	    ClassLoader classLoader = context.getClassLoader();
	    if (classLoader == null) {
		classLoader = Thread.currentThread().getContextClassLoader();
	    }
	    cl = createCodeLoader(classLoader, true);
	    context.setCodeLoader(cl);
	    Thread.currentThread().setContextClassLoader(cl);
	    return (CodeLoader)cl;
        }

        /*
         * classDef/buildSubclassInstance
         */
	public ClassGenerationResult generateClass(SimpleNode node, Context context){
		String className = getClassName(node.jjtGetChild(0));
		SimpleNode extendsNode = node.jjtGetChild(1);
                ClassSpec superclassSpec;
		String superclassName = null;
		if (extendsNode.jjtGetNumChildren() == 1){
			SimpleNode n = extendsNode.jjtGetChild(0);
			superclassSpec = ClassSpec.create(getClassName(n), context);
			superclassName = superclassSpec.className;
		} else {
			superclassSpec = ClassSpec.create(Object.class);
			superclassName = "java.lang.Object";
		}

		Class compileTimeClass = superclassSpec.compileTimeClass;

		SimpleNode implementsNode = node.jjtGetChild(2);
		SimpleNode classDefBody = node.jjtGetChild(3);

		List interfaces = null;
		int nc = implementsNode.jjtGetNumChildren();
		if (nc > 0){
			interfaces = new ArrayList();
			for (int i = 0; i < nc; i++){
				SimpleNode n = implementsNode.jjtGetChild(i);
				interfaces.add(getClassName(n));
			}
		}

		ClassSpec[] compileTimeInterfaces = null;
		if (interfaces != null){
		    compileTimeInterfaces = new ClassSpec[interfaces.size()];
		    for (int i = 0, n = interfaces.size(); i < n; i++){
			String name = (String)interfaces.get(i);
			ClassSpec s = ClassSpec.create(name, context);
			compileTimeInterfaces[i] = s;
		    }
		}

		CompileContext cc = new CompileContext(context);
		ClassFile cf = cc.cf;

		if (compileTimeClass != null){
		    ClassGenerator.transformClassDefBody(classDefBody, compileTimeClass);
		}

	       CompileContext cc2 = (CompileContext)cc.clone(false, true);
	       cc2.constClassName = className;
	       
	       return generateClass(className, null, classDefBody,
						 superclassSpec, compileTimeInterfaces,
						 null, this, cc2);
               
        }

        public static Class attachClosure(Class cls, PnutsFunction closure, Context context){
                try {
                        Method method = cls.getMethod("attach", new Class[]{Context.class, PnutsFunction.class});
                        method.invoke(null, new Object[]{context, closure});
                } catch (Exception e){
                        e.printStackTrace();
                }
                return cls;
        }
        
	public static Class loadBytecode(String name, String encodedBytecode, PnutsFunction closure, Context context){
                CodeLoader loader = getCodeLoader(context);
                byte[] bcode = NodeUtil.stringToByteArray(encodedBytecode);
		System.out.println(name);
                Class cls = loader.define(name, bcode, 0, bcode.length);
		return attachClosure(cls, closure, context);
        }

	/*
	 * Helper method for interpreter
	 */
	public static Object buildSubclassInstance(String encodedNode, Context context){
	        CompilerPnutsImpl impl = (CompilerPnutsImpl)context.getImplementation();
		return impl.compiler.buildSubclassInstance(NodeUtil.loadNode(encodedNode), context);
	}

	public Object buildSubclassInstance(SimpleNode node, Context context){
	        SimpleNode def = getClassDefNode(node, context);
		Class cls = defineClass(def, context);
		Object[] arg = interpreter._listElements(node.jjtGetChild(1), context);
		return Runtime.callConstructor(context, cls, arg, null);
	}

    
	public Class defineClass(String encodedNode, Context context){
 		return defineClass(NodeUtil.loadNode(encodedNode), context);
	}

	public Class defineClass(SimpleNode node, Context context){

 		String className = getClassName(node.jjtGetChild(0));
		SimpleNode extendsNode = node.jjtGetChild(1);
		String superclass = null;
		if (extendsNode.jjtGetNumChildren() == 1){
			SimpleNode n = extendsNode.jjtGetChild(0);
			superclass = getClassName(n);
		} else {
			superclass = "java.lang.Object";
		}

		SimpleNode implementsNode = node.jjtGetChild(2);
		SimpleNode classDefBody = node.jjtGetChild(3);

		List interfaces = null;
		int nc = implementsNode.jjtGetNumChildren();
		if (nc > 0){
			interfaces = new ArrayList();
			for (int i = 0; i < nc; i++){
				SimpleNode n = implementsNode.jjtGetChild(i);
				interfaces.add(getClassName(n));
			}
		}

                ClassSpec superclassSpec = ClassSpec.create(superclass, context);
		Class compileTimeClass = superclassSpec.compileTimeClass;

		ClassSpec[] compileTimeInterfaces = null;
		if (interfaces != null){
		    compileTimeInterfaces = new ClassSpec[interfaces.size()];
		    for (int i = 0, n = interfaces.size(); i < n; i++){
			String name = (String)interfaces.get(i);
			ClassSpec s = ClassSpec.create(name, context);
			compileTimeInterfaces[i] = s;
		    }
		}

		CompileContext cc = new CompileContext(context);
		ClassFile cf = cc.cf;

	       ClassGenerator.transformClassDefBody(classDefBody, compileTimeClass);

	       CompileContext cc2 = (CompileContext)cc.clone(false, true);
	       cc2.constClassName = className;
	       
	       ClassGenerationResult cg = generateClass(className, null, classDefBody,
						 superclassSpec, compileTimeInterfaces,
						 null, this, cc2);
                 
               ClassFile cf2 = cg.classFile;
               PnutsFunction closure = (PnutsFunction)cg.closureNode.accept(interpreter, context);

	       try {
		   CodeLoader loader = getCodeLoader(context);
                   
		   Class cls = cc2.loadClasses(loader);
		   Method m = cls.getMethod("attach", new Class[]{Context.class, PnutsFunction.class});
		   m.invoke(null, new Object[]{new Context(context), closure});
		   return cls;
	       } catch (LinkageError e){
		   ClassLoader cl = compileTimeClass.getClassLoader();
		   if (cl instanceof CodeLoader){
		       try {
			   Class cls = cc2.loadClasses((CodeLoader)cl);
			   Method m = cls.getMethod("attach", new Class[]{Context.class, PnutsFunction.class});
			   m.invoke(null, new Object[]{new Context(context), closure});
			   return cls;
		       } catch (Exception e2){
		       }
		   }
		   throw new PnutsException(e, context);
	       } catch (Exception ex){
		   throw new PnutsException(ex, context);
	       }
	}

       public Object methodDef(SimpleNode node, Context context){
 		return null;
       }

       public Object classDefBody(SimpleNode node, Context context){
           return null;
       }

	static Signature resolveSignature(MethodSignatureInfo methodSig, Context context){
	    Class returnType;
	    if (methodSig.returnTypeNode != null){
		returnType = interpreter.resolveType(methodSig.returnTypeNode, context);
	    } else {
		returnType = null;
	    }
	    SimpleNode[] parameterTypeNodes = methodSig.paramTypeNodes;
	    Class[] parameterTypes = null;
	    if (parameterTypeNodes != null){
		parameterTypes = new Class[parameterTypeNodes.length];
		for (int i = 0; i < parameterTypes.length; i++){
		    SimpleNode typeNode = parameterTypeNodes[i];
		    if (typeNode != null){
			parameterTypes[i] = interpreter.resolveType(typeNode, context);
		    }
		}
	    }
	    return new Signature(methodSig.methodName, returnType, parameterTypes, null, methodSig.fnode);
	}

	static Signature resolveSignature(ConstructorSignatureInfo constructorSig, Context context){
		SimpleNode[] parameterTypeNodes = constructorSig.paramTypeNodes;
		Class[] parameterTypes = null;
		if (parameterTypeNodes != null){
			parameterTypes = new Class[parameterTypeNodes.length];
			for (int i = 0; i < parameterTypes.length; i++){
				SimpleNode typeNode = parameterTypeNodes[i];
				if (typeNode != null){
					parameterTypes[i] = interpreter.resolveType(typeNode, context);
				}
			}
		}
		return new Signature(null, null, parameterTypes, null, constructorSig.fnode);
	}

       static String[] split(String s, char delim){
	   ArrayList list = new ArrayList();
	   int offset = 0;
	   int idx = 0;
	   while (idx >= 0){
	       idx = s.indexOf(delim, offset);
	       if (idx > 0){
		   list.add(s.substring(offset, idx));
		   offset = idx + 1;
	       }
	   }
	   list.add(s.substring(offset));
	   return (String[])list.toArray(new String[list.size()]);
       }

       
       public static class ClassGenerationResult {
                public ClassFile classFile;
                public SimpleNode closureNode;
       }
       
       static ClassGenerationResult generateClass(String className,
				   String scriptFile,
				   SimpleNode classDefBody,
				   ClassSpec superclassSpec,
				   ClassSpec[] interfaces,
				   List importNodes,
				   Compiler compiler,
				   CompileContext cc)
       {
	       List/*<MethodSignatureInfo>*/ methodSigs = new ArrayList();
	       List/*<ConstructorSignatureInfo>*/ constructorSigs = new ArrayList();
	       List/*<FieldSignatureInfo>*/ fieldSigs = new ArrayList();
               Set superMethodNames = new HashSet();
	       handleClassDefBody(classDefBody, methodSigs, constructorSigs, fieldSigs, superMethodNames);
               return generateClass(className, scriptFile, methodSigs, constructorSigs, fieldSigs,
                       superMethodNames, superclassSpec, interfaces, importNodes, compiler, cc);
       } 

       static void updateMethodSignatureInfo(MethodSignatureInfo sig, String fieldName){
            SimpleNode fnode = sig.fnode;
            Map thisReferences = sig.thisReferences;
            Collection refs = (Collection)thisReferences.get(fieldName);
            if (refs != null){
                Map params = sig.paramReferences;
                SimpleNode paramNode = (SimpleNode)params.get(fieldName);
                if (paramNode != null){
                    paramNode.str = (" " + paramNode.str).intern();
                    renameIDs(fnode, fieldName);
                }
                for (Iterator it = refs.iterator(); it.hasNext();){
                    SimpleNode node = (SimpleNode)it.next();
                    node.id = PnutsParserTreeConstants.JJTIDNODE;
                    node.clearChildren();
                }
            }
       }
       
       static void renameIDs(SimpleNode node, String name){
           int id = node.id;
           if (id == PnutsParserTreeConstants.JJTIDNODE){
                if (node.str == name){
                    SimpleNode parent = node.jjtGetParent();
                    if (parent == null || parent.id != PnutsParserTreeConstants.JJTMEMBERNODE){
                        node.str = (" " + name).intern();
                    }
                }
           } else if (id != PnutsParserTreeConstants.JJTCLASSDEF){
                int n = node.jjtGetNumChildren();
                for (int i = 0; i < n; i++){
                    renameIDs(node.jjtGetChild(i), name);
                }
           }
       }
       
       static void updateMethodSignatureInfo(List/*<MethodSignatureInfo>*/ sigs, String fieldName){
           for (int i = 0, n = sigs.size(); i < n; i++){
            updateMethodSignatureInfo((MethodSignatureInfo)sigs.get(i), fieldName);
           }
       }
       
       static SimpleNode buildClosureNode(List/*<SimpleNode>*/ nodes,
               List/*<FieldSignatureInfo>*/ fieldSigs,
               Map typeMap
               ){
		SimpleNode block = new SimpleNode(PnutsParserTreeConstants.JJTBLOCK);

		SimpleNode listElements = new SimpleNode(PnutsParserTreeConstants.JJTLISTELEMENTS);
		block.jjtAddChild(listElements, fieldSigs.size() + 1);
		listElements.jjtSetParent(block);
		for (int i = nodes.size() - 1; i >= 0; i--){
		    SimpleNode n = (SimpleNode)nodes.get(i);
		    listElements.jjtAddChild(n, i);
		    n.jjtSetParent(listElements);
		}
	       for (int i = 0, n = fieldSigs.size(); i < n; i++){
		   FieldSignatureInfo finfo = (FieldSignatureInfo)fieldSigs.get(i);
		   String fieldName = finfo.fieldName;
		   SimpleNode rhs = finfo.rhs;
		   SimpleNode var = new SimpleNode(PnutsParserTreeConstants.JJTIDNODE);
		   var.str = fieldName;
		   SimpleNode assignment = new SimpleNode(PnutsParserTreeConstants.JJTASSIGNMENT);
		   if (rhs == null){
		       Class type = (Class)typeMap.get(fieldName);
		       if (type.isPrimitive()){
			   if (type == boolean.class){
			       rhs = new SimpleNode(PnutsParserTreeConstants.JJTFALSENODE);
			   } else {
			       rhs = new SimpleNode(PnutsParserTreeConstants.JJTINTEGERNODE);
			       rhs.info = new Object[]{new Integer(0), null};
			   }
		       } else {
			   rhs = new SimpleNode(PnutsParserTreeConstants.JJTNULLNODE);
		       }
		   }
		   assignment.jjtAddChild(rhs, 1);
		   rhs.jjtSetParent(assignment);
		   assignment.jjtAddChild(var, 0);
		   block.jjtAddChild(assignment, i + 1);
	       }
                SimpleNode superAssignment = new SimpleNode(PnutsParserTreeConstants.JJTASSIGNMENT);
                SimpleNode superID = new SimpleNode(PnutsParserTreeConstants.JJTIDNODE);
                superID.str = SUPER;
                block.jjtAddChild(superAssignment, 0);
		superAssignment.jjtSetParent(superAssignment);

		SimpleNode closure = new SimpleNode(PnutsParserTreeConstants.JJTFUNCTIONSTATEMENT);
		SimpleNode paramList = new SimpleNode(PnutsParserTreeConstants.JJTPARAMLIST);

		SimpleNode thisParam = new SimpleNode(PnutsParserTreeConstants.JJTPARAM);
		thisParam.str = THIS;
                SimpleNode superCreation = new SimpleNode(PnutsParserTreeConstants.JJTNEW);
		superAssignment.jjtAddChild(superCreation, 1);
		superAssignment.jjtAddChild(superID, 0);
                SimpleNode typeNode = new SimpleNode(PnutsParserTreeConstants.JJTCLASSNAME);
                SimpleNode pkg0 = new SimpleNode(PnutsParserTreeConstants.JJTPACKAGE);
                pkg0.str = "pnuts";
                SimpleNode pkg1 = new SimpleNode(PnutsParserTreeConstants.JJTPACKAGE);
                pkg1.str = "compiler";
                SimpleNode pkg2 = new SimpleNode(PnutsParserTreeConstants.JJTPACKAGE);
                pkg2.str = "ClassGenerator$SuperCallProxy";
                typeNode.jjtAddChild(pkg2, 2);
                typeNode.jjtAddChild(pkg1, 1);
                typeNode.jjtAddChild(pkg0, 0);
                SimpleNode arg = new SimpleNode(PnutsParserTreeConstants.JJTLISTELEMENTS);
                SimpleNode thisNode = new SimpleNode(PnutsParserTreeConstants.JJTIDNODE);
                thisNode.str = THIS;
                arg.jjtAddChild(thisNode, 0);
                superCreation.jjtAddChild(arg, 1);
                superCreation.jjtAddChild(typeNode, 0);
                
		paramList.jjtAddChild(thisParam, 0);
		thisParam.jjtSetParent(paramList);

		closure.jjtAddChild(block, 1);
		closure.jjtAddChild(paramList, 0);
		block.jjtSetParent(closure);
		paramList.jjtSetParent(closure);

		if (DEBUG){
		    System.out.println(closure.unparse());
		}
                return closure;
               
       }
       
    /*
     * intermediate results:
     *   methodIDs
     *   signatureList
     *   closure
     */
       static ClassGenerationResult generateClass(String className,
				   String scriptFile,
				   List/*<MethodSignatureInfo>*/ methodSigs,
				   List/*<ConstructorSignatureInfo>*/ constructorSigs,
				   List/*<FieldSignatureInfo>*/ fieldSigs,
				   Set superMethodNames,
                                   ClassSpec superclassSpec,
				   ClassSpec[] interfaces,
				   List importNodes,
				   Compiler compiler,
				   CompileContext cc)
       {
               Class superclass = superclassSpec.compileTimeClass;
               String superclassName = superclassSpec.className;
	       /*
		* instance fields
		*/
	       Map typeMap = new HashMap();
	       for (int i = 0, n = fieldSigs.size(); i < n; i++){
		   FieldSignatureInfo finfo = (FieldSignatureInfo)fieldSigs.get(i);
		   String fieldName = finfo.fieldName;
                   
                   updateMethodSignatureInfo(methodSigs, fieldName);
                   updateMethodSignatureInfo(constructorSigs, fieldName);
		   SimpleNode rhs = finfo.rhs;

		   Class type = (finfo.typeNode != null) ? interpreter.resolveType(finfo.typeNode, cc) : Object.class;
		   typeMap.put(fieldName, type);

	       }

		Set/*<Signature>*/ signatureSet = new HashSet();
		for (int i = 0, n = methodSigs.size(); i < n; i++){
		       MethodSignatureInfo sig = (MethodSignatureInfo)methodSigs.get(i);
		       Signature s = resolveSignature(sig, cc);
		       Class[] parameterTypes;
		       Class[] exceptionTypes;
		       Class returnType;
		       int modifiers;
		       boolean resolved = s.isResolved();
		       if (!resolved){
			   String methodName = s.getMethodName();
			   int n_types = s.getParameterTypes().length;
			   String prop = null;
			   if (n_types == 0){
			       if (methodName.startsWith("get")){
				   prop = decapitalize(methodName.substring(3));
				   Class t = (Class)typeMap.get(prop);
				   Class t2 = s.getReturnType();
				   if (t != null && (t2 == null || t2.equals(t))){
				       returnType = t;
				       s.setReturnType(returnType);
				       s.setModifiers(Modifier.PUBLIC);
				   }
			       } else if (methodName.startsWith("is")){
				   prop = decapitalize(methodName.substring(2));
				   Class t = (Class)typeMap.get(prop);
				   Class t2 = s.getReturnType();
				   if (t != null && t2 == null){
				       returnType = t;
				       s.setReturnType(returnType);
				       s.setModifiers(Modifier.PUBLIC);
				   }
			       }
			   } else if (n_types == 1 && methodName.startsWith("set")){
			       prop = decapitalize(methodName.substring(3));
			       Class p = s.getParameterTypes()[0];
			       Class t = (Class)typeMap.get(prop);
			       Class t2 = s.getReturnType();
			       if (t != null && p == null && (t2 == null || t2.equals(void.class))){
				   parameterTypes = new Class[]{t};
				   returnType = void.class;
				   s.setReturnType(returnType);
				   s.setParameterTypes(parameterTypes);
				   s.setModifiers(Modifier.PUBLIC);				   
			       }
			   }
		       }

		       List/*<Method>*/ methods = new ArrayList();
                       Class[] interfaceTypes = null;
		       if (interfaces != null){
			   interfaceTypes = new Class[interfaces.length];
			   for (int j = 0; j < interfaces.length; j++){
                               interfaceTypes[j] = interfaces[j].compileTimeClass;
			   }
		       }
		       if (superclass != null && s.resolve(superclass, interfaceTypes, methods)){
			   for (int j = 0, n2 = methods.size(); j < n2; j++){
			       Method first = (Method)methods.get(j);
			       parameterTypes = first.getParameterTypes();
			       returnType = first.getReturnType();
			       exceptionTypes = first.getExceptionTypes();
			       modifiers = Constants.ACC_PUBLIC;

			       Signature s2 = (Signature)s.clone();
			       s2.setParameterTypes(parameterTypes);
			       s2.setReturnType(returnType);
			       s2.setExceptionTypes(exceptionTypes);
			       s2.setModifiers(Constants.ACC_PUBLIC);

			       signatureSet.add(s2);
			   }

		       } else {
			   parameterTypes = s.getParameterTypes();
			   returnType = s.getReturnType();
			   if (returnType == null){
			       returnType = Object.class;
			   }
			   exceptionTypes = new Class[0];
			   for (int j = 0; j < parameterTypes.length; j++){
			       if (parameterTypes[j] == null){
				   parameterTypes[j] = Object.class;
			       }
			   }
			   modifiers = Constants.ACC_PUBLIC;

			   Signature s2 = (Signature)s.clone();
			   s2.setParameterTypes(parameterTypes);
			   s2.setReturnType(returnType);
			   s2.setExceptionTypes(exceptionTypes);
			   s2.setModifiers(Constants.ACC_PUBLIC);			   

			   signatureSet.add(s2);
		       }
		}

                List/*<String>*/ methodIDs = new ArrayList();
		List/*<Signature>*/ signatureList = new ArrayList();
                List/*<Signature>*/ constructorSignatureList = new ArrayList();
		List/*<SimpleNode>*/ nodes = new ArrayList();

		for (int i = 0, n = constructorSigs.size(); i < n; i++){
		    ConstructorSignatureInfo constructorSig = (ConstructorSignatureInfo)constructorSigs.get(i);
		    Signature sig = resolveSignature(constructorSig, cc);
                    constructorSignatureList.add(sig);
		    List constructors = new ArrayList();
		    Class[] parameterTypes;
		    Class[] exceptionTypes;
		    if (superclass != null && sig.resolveAsConstructor(superclass, constructors)){
			for (int j = 0, n2 = constructors.size(); j < n2; j++){
			    Constructor cons = (Constructor)constructors.get(j);
			    parameterTypes = cons.getParameterTypes();
			    exceptionTypes = cons.getExceptionTypes();
			    String constructorSignature = ClassFile.signature(parameterTypes);

			    Signature s2 = (Signature)sig.clone();
			    s2.setParameterTypes(parameterTypes);
			    s2.setReturnType(void.class);
			    s2.setExceptionTypes(exceptionTypes);
			    s2.setModifiers(Constants.ACC_PUBLIC);
			    signatureSet.add(s2);
			}
		    } else {
			   parameterTypes = sig.getParameterTypes();
			   exceptionTypes = new Class[0];
			   for (int j = 0; j < parameterTypes.length; j++){
			       if (parameterTypes[j] == null){
				   parameterTypes[j] = Object.class;
			       }
			   }

			   Signature s2 = (Signature)sig.clone();
			   s2.setParameterTypes(parameterTypes);
			   s2.setReturnType(void.class);
			   s2.setExceptionTypes(exceptionTypes);
			   s2.setModifiers(Constants.ACC_PUBLIC);			   

			   signatureSet.add(s2);
		    }
		}

		HashMap readableAttributes = new HashMap();
		HashMap writableAttributes = new HashMap();

		for (Iterator it = typeMap.entrySet().iterator(); it.hasNext();){
		       Map.Entry entry = (Map.Entry)it.next();
		       String name = (String)entry.getKey();
		       Class type = (Class)entry.getValue();
		       Signature s = getterSignature(type, name);
		       if (!signatureSet.contains(s)){
			   readableAttributes.put(name, type);
		       }

		       s = setterSignature(type, name);
		       if (!signatureSet.contains(s)){
			   writableAttributes.put(name, type);
		       }
		}
		if (DEBUG){
		    System.out.println("readableAttributes are " + readableAttributes);
		    System.out.println("writableAttributes are " + writableAttributes);
		}
		for (Iterator it = signatureSet.iterator(); it.hasNext(); ){
		    Signature sig = (Signature)it.next();
		    SimpleNode node = (SimpleNode)sig.nodeInfo;
		    nodes.add(loadNode(saveNode(node)));
                    String mid = sig.toJavaIdentifier();
		    signatureList.add(sig);
                    methodIDs.add(mid);
		}
		for (Iterator it = readableAttributes.entrySet().iterator(); it.hasNext();){
		    Map.Entry entry = (Map.Entry)it.next();
		    String name = (String)entry.getKey();
		    Class type = (Class)entry.getValue();
                    SimpleNode getterNode = buildGetterNode(type, name);
		    nodes.add(getterNode);
                    Signature sig = new Signature(getterNode.str, type, new Class[0], new Class[0], Constants.ACC_PUBLIC);
                    String mid = sig.toJavaIdentifier();
		    signatureList.add(sig);
                    methodIDs.add(mid);
		}
		for (Iterator it = writableAttributes.entrySet().iterator(); it.hasNext();){
		    Map.Entry entry = (Map.Entry)it.next();
		    String name = (String)entry.getKey();
		    Class type = (Class)entry.getValue();
                    SimpleNode setterNode = buildSetterNode(type, name);
		    nodes.add(setterNode);
		    Signature sig = new Signature(setterNode.str, void.class, new Class[]{type}, new Class[0], Constants.ACC_PUBLIC);
                    String mid = sig.toJavaIdentifier();
		    signatureList.add(sig);
                    methodIDs.add(mid);
		}
		if (DEBUG){
		    System.out.println("methodIDs = " + methodIDs);
		    System.out.println("signatureList = " + signatureList);
                }
                
                SimpleNode closure = buildClosureNode(nodes, fieldSigs, typeMap);

                /*
                 * className, superclass, interfaces, superMethodNames,signatureList, constructorSignatureList, cc, methodIDs
                 */
	       cc.cf = ClassGenerator.createClassFile(className, superclassSpec, interfaces, superMethodNames);
	       ClassFile cf = cc.cf;

                ClassGenerator.constructor(cf, superclassSpec, compiler, cc, constructorSignatureList);

		for (int i = 0, n = methodIDs.size(); i < n; i++){
		    String mid = (String)methodIDs.get(i);
		    cf.addField(mid, "Lpnuts/lang/PnutsFunction;", Constants.ACC_PRIVATE);
		    Signature sig = (Signature)signatureList.get(i);
		    String methodName = sig.getMethodName();
		    if (methodName != null){
			ClassGenerator.defineMethod(cf, sig.getParameterTypes(),
						    sig.getReturnType(),
						    sig.getExceptionTypes(),
						    sig.getModifiers(),
						    sig.getMethodName(),
						    sig.getMethodName() + ClassFile.signature(sig.getParameterTypes()),
						    mid
						    );
		    }
		    
		}
                
                cf.openMethod("__initialize", "()V", Constants.ACC_PRIVATE);
                cf.add(Opcode.GETSTATIC, className, METHOD_FACTORY_FUNCTION, "Lpnuts/lang/PnutsFunction;");
                cf.add(Opcode.ICONST_1);
                cf.add(Opcode.ANEWARRAY, "java.lang.Object");
                cf.add(Opcode.DUP);
                cf.add(Opcode.ICONST_0);
                cf.add(Opcode.ALOAD_0);
                cf.add(Opcode.AASTORE);
                cf.add(Opcode.GETSTATIC, className, "_context", "Lpnuts/lang/Context;");
                cf.add(Opcode.INVOKEVIRTUAL,
                        "pnuts.lang.PnutsFunction",
                        "call",
                        "([Ljava/lang/Object;Lpnuts/lang/Context;)",
                        "Ljava/lang/Object;");
                cf.add(Opcode.CHECKCAST, "[Ljava/lang/Object;");
                int array = cf.getLocal();
                cf.storeLocal(array);

                for (int i = 0; i < methodIDs.size(); i++){
                    cf.add(Opcode.ALOAD_0);
                    cf.loadLocal(array);
                    cf.pushInteger(i);
                    cf.add(Opcode.AALOAD);
                    cf.add(Opcode.CHECKCAST, "pnuts.lang.PnutsFunction");
                    cf.add(Opcode.PUTFIELD, className, (String)methodIDs.get(i), "Lpnuts/lang/PnutsFunction;");
                }
                cf.add(Opcode.RETURN);
                cf.closeMethod();
                
                /*
                 * public static void attach(Context);
                 */
                cf.openMethod("attach", "(Lpnuts/lang/Context;)V",
                        (short) (Constants.ACC_PUBLIC | Constants.ACC_STATIC));
                cc.setContextIndex(0);
                cc.hasAttachMethod = true;
                cf.add(Opcode.ALOAD_0);
                cf.add(Opcode.PUTSTATIC, className, "_context", "Lpnuts/lang/Context;");
                cf.add(Opcode.RETURN);
                cf.closeMethod();

                /*
                 * public static void attach(Context, PnutsFunction);
                 */
                cf.openMethod("attach", "(Lpnuts/lang/Context;Lpnuts/lang/PnutsFunction;)V",
                        (short) (Constants.ACC_PUBLIC | Constants.ACC_STATIC));
                cc.setContextIndex(0);
                cc.hasAttachMethod = true;
                cf.add(Opcode.ALOAD_0);
                cf.add(Opcode.PUTSTATIC, className, "_context", "Lpnuts/lang/Context;");
                cf.add(Opcode.ALOAD_1);
                cf.add(Opcode.PUTSTATIC, className, METHOD_FACTORY_FUNCTION, "Lpnuts/lang/PnutsFunction;");
                cf.add(Opcode.RETURN);
                cf.closeMethod();

                
		/*
		 * static {}
		 */
	       compiler.staticBlock(cf, (scriptFile != null) ? closure : null, cc);

               ClassGenerationResult result = new ClassGenerationResult();
	       result.classFile = cf;
               result.closureNode = closure;
               return result;
       }

       static SimpleNode buildSetterNode(Class type, String name){
	   SimpleNode n = new SimpleNode(PnutsParserTreeConstants.JJTFUNCTIONSTATEMENT);
	   SimpleNode p = new SimpleNode(PnutsParserTreeConstants.JJTPARAMLIST);
	   SimpleNode p0 = new SimpleNode(PnutsParserTreeConstants.JJTPARAM);
	   p0.str = ("_" + name).intern();
	   p.jjtAddChild(p0, 0);
	   SimpleNode block = new SimpleNode(PnutsParserTreeConstants.JJTBLOCK);
	   SimpleNode assign = new SimpleNode(PnutsParserTreeConstants.JJTASSIGNMENT);
	   SimpleNode lhs = new SimpleNode(PnutsParserTreeConstants.JJTIDNODE);
	   SimpleNode rhs = new SimpleNode(PnutsParserTreeConstants.JJTIDNODE);
	   lhs.str = name;
	   rhs.str = p0.str;
	   assign.jjtAddChild(rhs, 1);
	   assign.jjtAddChild(lhs, 0);
	   block.jjtAddChild(assign, 0);
	   n.jjtAddChild(block, 1);
	   n.jjtAddChild(p, 0);
	   n.str = ("set" + Character.toUpperCase(name.charAt(0)) + name.substring(1)).intern();
	   return n;
       }

       static SimpleNode buildGetterNode(Class type, String name){
	   SimpleNode n = new SimpleNode(PnutsParserTreeConstants.JJTFUNCTIONSTATEMENT);
	   SimpleNode p = new SimpleNode(PnutsParserTreeConstants.JJTPARAMLIST);
	   SimpleNode block = new SimpleNode(PnutsParserTreeConstants.JJTBLOCK);
	   SimpleNode id = new SimpleNode(PnutsParserTreeConstants.JJTIDNODE);
	   id.str = name;
	   block.jjtAddChild(id, 0);
	   n.jjtAddChild(block, 1);
	   n.jjtAddChild(p, 0);
	   String prefix = (type == boolean.class) ? "is" : "get";
	   n.str = (prefix + Character.toUpperCase(name.charAt(0)) + name.substring(1)).intern();
	   return n;
       }


       static String capitalize(String name) { 
	       if (name == null || name.length() == 0) { 
	              return name; 
	       }
	       return name.substring(0, 1).toUpperCase() + name.substring(1);
       }

       static String decapitalize(String name) {
              if (name == null || name.length() == 0) {
	             return name;
	      }
	      if (name.length() > 1 && Character.isUpperCase(name.charAt(1)) &&
			Character.isUpperCase(name.charAt(0))){
	             return name;
	      }
	      char chars[] = name.toCharArray();
	      chars[0] = Character.toLowerCase(chars[0]);
	      return new String(chars);
       }

       static final Class[] NO_PARAM = new Class[0];

       public static Signature getterSignature(Class type, String name){
	       String prefix;
	       if (type == boolean.class){
	              prefix = "is";
	       } else {
		   prefix = "get";
	       }
	       return new Signature(prefix + capitalize(name), type, NO_PARAM, NO_PARAM, Modifier.PUBLIC);
       }

       public static Signature setterSignature(Class type, String name){
	       String prefix = "set";
	       return new Signature(prefix + capitalize(name), void.class, new Class[]{type}, NO_PARAM, Modifier.PUBLIC);
       }

       /*
        * classScript->generateClass
        */
       void generateClass(SimpleNode classDefBody,
			      String className, 
			      ClassSpec superclassSpec,
			      List/*<ClassSpec>*/ interfaces,
			      List importNodes,
			      CompileContext cc)
       {
	       String scriptFile = null;
	       if (cc.scriptSource instanceof URL){
		       String file = ((URL)cc.scriptSource).getFile();
		       int idx = file.lastIndexOf('/');
		       if (idx < 0){
			       scriptFile = file;
		       } else {
			       scriptFile = file.substring(idx + 1);
		       }
	       } else if (cc.scriptSource instanceof File){
		       scriptFile = ((File)cc.scriptSource).getName();
	       }
	       cc.constClassName = className;

	       Class superclass = superclassSpec.compileTimeClass;
               ClassSpec[] interfaceArray;
	       if (interfaces != null){
        	   interfaceArray = (ClassSpec[])interfaces.toArray(new ClassSpec[interfaces.size()]);
	       } else {
		   interfaceArray = new ClassSpec[0];
	       }

	       ClassGenerator.transformClassDefBody(classDefBody, superclass);

	       generateClass(className, scriptFile, classDefBody,
				 superclassSpec, interfaceArray, importNodes, this, cc);
       }

       public Object classScript(SimpleNode node, Context context){
		int num = node.jjtGetNumChildren();
		List importNodes = new ArrayList();
		SimpleNode packageNode = null;
		for (int i = 0; i < num; i++){
			SimpleNode n = node.jjtGetChild(i);
			if (n.id == PnutsParserTreeConstants.JJTPACKAGESTATEMENT){
			       packageNode = n;
			} else if (n.id == PnutsParserTreeConstants.JJTIMPORT){
			       importNodes.add(n);
			       n.accept(interpreter, context);
			} else if (n.id == PnutsParserTreeConstants.JJTCLASSDEF){
			       String className;
			       if (packageNode != null){
				       className = packageName(packageNode) + "." + getClassName(n.jjtGetChild(0));
			       } else {
			              className = getClassName(n.jjtGetChild(0));
			       }
				
				SimpleNode extendsNode = n.jjtGetChild(1);
				String superclass = null;
                                ClassSpec superclassSpec;
				if (extendsNode.jjtGetNumChildren() == 1){
					SimpleNode n0 = extendsNode.jjtGetChild(0);
                                        superclassSpec = ClassSpec.create(getClassName(n0), context);
					superclass = superclassSpec.className;
				} else {
					superclass = "java.lang.Object";
                                        superclassSpec = ClassSpec.create(superclass, context);
				}

				List interfaces = null;
				SimpleNode implementsNode = n.jjtGetChild(2);
				int nc = implementsNode.jjtGetNumChildren();
				if (nc > 0){
					interfaces = new ArrayList();
					for (int j = 0; j < nc; j++){
						SimpleNode nj = implementsNode.jjtGetChild(j);
						String typename = getClassName(nj);
                                                ClassSpec spec = ClassSpec.create(typename, context);
						interfaces.add(spec);
					}
				}
				SimpleNode classDefBody = n.jjtGetChild(3);
				this.className = className;
				CompileContext cc = (CompileContext)context;

				generateClass(classDefBody,
						  className,
						  superclassSpec,
						  interfaces,
						  importNodes,
						  cc);
				return null;
			} else {
			    throw new InternalError();
			}
		}
		throw new InternalError();
       }


	static String packageName(SimpleNode node){
		StringBuffer sbuf = new StringBuffer();
		sbuf.append(node.jjtGetChild(0).str);
		for (int i = 1, n = node.jjtGetNumChildren(); i < n; i++) {
			sbuf.append('.');
			sbuf.append(node.jjtGetChild(i).str);
		}
		return sbuf.toString();
	}

	public Object packageNode(SimpleNode node, Context context) {
	    CompileContext cc = (CompileContext)context;
	    ClassFile cf = cc.cf;
	    int ctx = cc.getContextIndex();
	    int n = node.jjtGetNumChildren();
	    cf.add(Opcode.GETSTATIC, "pnuts.lang.PnutsFunction", "PACKAGE", "Lpnuts/lang/PnutsFunction;");
	    if (n == 0){
		if ("(".equals(node.str)){
		    cf.add(Opcode.GETSTATIC, className, "NO_PARAM", "[Ljava/lang/Object;");
		    cf.loadLocal(ctx);
		    cf.add(Opcode.INVOKEVIRTUAL,
			   "pnuts.lang.PnutsFunction",
			   "call",
			   "([Ljava/lang/Object;Lpnuts/lang/Context;)",
			   "Ljava/lang/Object;");
		} else {
		    return null;
		}
	    } else {
		SimpleNode c = node.jjtGetChild(0);
		cf.add(Opcode.ICONST_1);
		cf.add(Opcode.ANEWARRAY, "java.lang.Object");
		cf.add(Opcode.DUP);
		cf.add(Opcode.ICONST_0);
		if (n == 1 && c.id != PnutsParserTreeConstants.JJTPACKAGE){ // package("...")
		    c.accept(this, context);
		} else {
		    cf.add(Opcode.LDC, cf.addConstant(packageName(node)));
		}
		cf.add(Opcode.AASTORE);
		cf.loadLocal(ctx);
		cf.add(Opcode.INVOKEVIRTUAL,
		       "pnuts.lang.PnutsFunction",
		       "call",
		       "([Ljava/lang/Object;Lpnuts/lang/Context;)",
		       "Ljava/lang/Object;");
	    }
	    return null;
	}

	public Object importNode(SimpleNode node, Context context) {
		CompileContext cc = (CompileContext) context;
		ClassFile cf = cc.cf;
		int ctx = cc.getContextIndex();


		SimpleNode p = node.jjtGetParent();
		if (p != null && p.id == PnutsParserTreeConstants.JJTEXPRESSIONLIST){
		    node.accept(interpreter, context);
		}

		String t2 = node.str;
		int n = node.jjtGetNumChildren();
		if (n == 0) {
			if ("*".equals(t2)) {
				cf.loadLocal(ctx);
				cf.add(Opcode.LDC, cf.addConstant("*"));
				cf.add(Opcode.INVOKESTATIC, "pnuts.lang.Runtime", "addImport",
						"(Lpnuts/lang/Context;Ljava/lang/String;)", "V");
				cf.add(Opcode.ACONST_NULL);
			} else if ("(".equals(t2)) {
				cf.add(Opcode.GETSTATIC, "pnuts.lang.PnutsFunction", "IMPORT",
						"Lpnuts/lang/PnutsFunction;");
				cf.add(Opcode.GETSTATIC, className, "NO_PARAM",
						"[Ljava/lang/Object;");
				cf.loadLocal(ctx);
				cf.add(Opcode.INVOKEVIRTUAL, "pnuts.lang.PnutsFunction",
						"call", "([Ljava/lang/Object;Lpnuts/lang/Context;)",
						"Ljava/lang/Object;");
			} else {
				cf.add(Opcode.GETSTATIC, "pnuts.lang.PnutsFunction", "IMPORT",
						"Lpnuts/lang/PnutsFunction;");
			}
			return null;
		}
		if (n == 1
				&& node.jjtGetChild(0).id != PnutsParserTreeConstants.JJTPACKAGE) { // import(...)
			int arg = cf.getLocal();
			accept(node, 0, context);
			cf.storeLocal(arg);
			cf.add(Opcode.GETSTATIC, "pnuts.lang.PnutsFunction", "IMPORT",
					"Lpnuts/lang/PnutsFunction;");
			cf.add(Opcode.ICONST_1);
			cf.add(Opcode.ANEWARRAY, "java.lang.Object");
			cf.add(Opcode.DUP);
			cf.add(Opcode.ICONST_0);
			cf.loadLocal(arg);
			cf.freeLocal(arg);
			cf.add(Opcode.AASTORE);
			cf.loadLocal(ctx);
			cf.add(Opcode.INVOKEVIRTUAL, "pnuts.lang.PnutsFunction", "call",
					"([Ljava/lang/Object;Lpnuts/lang/Context;)",
					"Ljava/lang/Object;");
			return null;
		}
		StringBuffer sbuf = new StringBuffer();
		sbuf.append(node.jjtGetChild(0).str);
		for (int i = 1; i < n; i++) {
			sbuf.append('.');
			sbuf.append(node.jjtGetChild(i).str);
		}
		cf.loadLocal(ctx);
		if (node.info != null) { // static import
			cf.add(Opcode.LDC, cf.addConstant(sbuf.toString()));
			cf.add(t2 == null ? Opcode.ICONST_0 : Opcode.ICONST_1);
			cf.add(Opcode.INVOKESTATIC, "pnuts.lang.Runtime",
					"addStaticMembers",
					"(Lpnuts/lang/Context;Ljava/lang/String;Z)", "V");
		} else {
			if (t2 != null) {
				sbuf.append(".*");
			}
			cf.add(Opcode.LDC, cf.addConstant(sbuf.toString()));
			cf.add(Opcode.INVOKESTATIC, "pnuts.lang.Runtime", "addImport",
					"(Lpnuts/lang/Context;Ljava/lang/String;)", "V");
		}
		cf.add(Opcode.ACONST_NULL);
		return null;
	}

	void booleanCheck(int id, ClassFile cf, Context context) {
		if (id != PnutsParserTreeConstants.JJTTRUENODE
				&& id != PnutsParserTreeConstants.JJTFALSENODE
				&& id != PnutsParserTreeConstants.JJTLENODE
				&& id != PnutsParserTreeConstants.JJTLTNODE
				&& id != PnutsParserTreeConstants.JJTGENODE
				&& id != PnutsParserTreeConstants.JJTGTNODE
				&& id != PnutsParserTreeConstants.JJTEQUALNODE
				&& id != PnutsParserTreeConstants.JJTNOTEQNODE
				&& id != PnutsParserTreeConstants.JJTINSTANCEOFEXPRESSION
				&& id != PnutsParserTreeConstants.JJTORNODE
				&& id != PnutsParserTreeConstants.JJTANDNODE
				&& id != PnutsParserTreeConstants.JJTNOTNODE) {
			int index = cf.getLocal();
			cf.add(Opcode.DUP);
			cf.storeLocal(index);

			cf.add(Opcode.INSTANCEOF, "java.lang.Boolean");
			Label l_next = cf.getLabel();
			cf.add(Opcode.IFNE, l_next);
			Label l_next_2 = cf.getLabel();

			// disable boolean check
			// error(cf, "boolean.expected", new int[] { index }, (CompileContext) context);

			cf.loadLocal(index);
			cf.add(Opcode.INVOKESTATIC,
			       "pnuts.lang.Runtime",
			       "toBoolean",
			       "(Ljava/lang/Object;)",
			       "Ljava/lang/Boolean;");
			cf.add(Opcode.GOTO, l_next_2);
			l_next.fix();
			cf.loadLocal(index);
			l_next_2.fix();
			cf.freeLocal(index);
		}
	}

	public Object logAndNode(SimpleNode node, Context context) {
		CompileContext cc = (CompileContext) context;
		ClassFile cf = cc.cf;
		addLineInfo(cc, cc.getContextIndex(), node);
		
		accept(node, 0, context);
		booleanCheck(node.jjtGetChild(0).id, cf, context);
		cf.add(Opcode.CHECKCAST, "java.lang.Boolean");
		int tgt1 = cf.getLocal();
		cf.add(Opcode.DUP);
		cf.storeLocal(tgt1);

		cf.add(Opcode.INVOKEVIRTUAL, "java.lang.Boolean", "booleanValue", "()",
				"Z");
		Label l_else = cf.getLabel();
		cf.add(Opcode.IFEQ, l_else);
		accept(node, 1, context);
		booleanCheck(node.jjtGetChild(1).id, cf, context);
		Label next = cf.getLabel();
		cf.add(Opcode.GOTO, next);
		l_else.fix();
		cf.popStack();
		cf.loadLocal(tgt1);
		next.fix();
		cf.freeLocal(tgt1);
		return null;
	}

	public Object logOrNode(SimpleNode node, Context context) {
		CompileContext cc = (CompileContext) context;
		ClassFile cf = cc.cf;
		addLineInfo(cc, cc.getContextIndex(), node);
		
		accept(node, 0, context);
		booleanCheck(node.jjtGetChild(0).id, cf, context);
		cf.add(Opcode.CHECKCAST, "java.lang.Boolean");
		int tgt1 = cf.getLocal();
		cf.add(Opcode.DUP);
		cf.storeLocal(tgt1);

		cf.add(Opcode.INVOKEVIRTUAL, "java.lang.Boolean", "booleanValue", "()",
				"Z");
		Label l_else = cf.getLabel();
		cf.add(Opcode.IFNE, l_else);
		accept(node, 1, context);
		booleanCheck(node.jjtGetChild(1).id, cf, context);
		Label next = cf.getLabel();
		cf.add(Opcode.GOTO, next);
		l_else.fix();
		cf.popStack();
		cf.loadLocal(tgt1);
		next.fix();
		cf.freeLocal(tgt1);
		return null;
	}

	public Object logNotNode(SimpleNode node, Context context) {
		CompileContext cc = (CompileContext) context;
		ClassFile cf = cc.cf;
		addLineInfo(cc, cc.getContextIndex(), node);
		
		accept(node, 0, context);
		booleanCheck(node.jjtGetChild(0).id, cf, context);
		cf.add(Opcode.CHECKCAST, "java.lang.Boolean");
		cf.add(Opcode.INVOKEVIRTUAL, "java.lang.Boolean", "booleanValue", "()",
				"Z");
		Label t = cf.getLabel();
		Label next = cf.getLabel();
		cf.add(Opcode.IFEQ, t);
		cf.add(Opcode.GETSTATIC, "java.lang.Boolean", "FALSE",
				"Ljava/lang/Boolean;");
		cf.add(Opcode.GOTO, next);
		t.fix();
		cf.popStack();
		cf.add(Opcode.GETSTATIC, "java.lang.Boolean", "TRUE",
				"Ljava/lang/Boolean;");
		next.fix();
		return null;
	}

	boolean canConstantFold(SimpleNode node) {
		return _constantFolding &&
		    isConstant(node.jjtGetChild(0)) &&
		    isConstant(node.jjtGetChild(1));
	}

	static boolean isConstant(SimpleNode node) {
		switch (node.id) {
		case PnutsParserTreeConstants.JJTINTEGERNODE:{
			Object[] p = (Object[]) node.info;
			return p[1] == null;
		}
		case PnutsParserTreeConstants.JJTFLOATINGNODE:{
			Object[] p = (Object[]) node.info;
			return p[1] == null;
		}
		case PnutsParserTreeConstants.JJTSTRINGNODE:
		case PnutsParserTreeConstants.JJTCHARACTERNODE:
		case PnutsParserTreeConstants.JJTTRUENODE:
		case PnutsParserTreeConstants.JJTFALSENODE:
		case PnutsParserTreeConstants.JJTNULLNODE:
			return true;
		case PnutsParserTreeConstants.JJTLENODE:
		case PnutsParserTreeConstants.JJTLTNODE:
		case PnutsParserTreeConstants.JJTGENODE:
		case PnutsParserTreeConstants.JJTGTNODE:
		case PnutsParserTreeConstants.JJTEQUALNODE:
		case PnutsParserTreeConstants.JJTNOTEQNODE:
		case PnutsParserTreeConstants.JJTANDNODE:
		case PnutsParserTreeConstants.JJTORNODE:
		case PnutsParserTreeConstants.JJTXORNODE:
		case PnutsParserTreeConstants.JJTLOGORNODE:
		case PnutsParserTreeConstants.JJTLOGANDNODE:
		case PnutsParserTreeConstants.JJTSHIFTLEFTNODE:
		case PnutsParserTreeConstants.JJTSHIFTRIGHTNODE:
		case PnutsParserTreeConstants.JJTSHIFTARITHMETICNODE:
		case PnutsParserTreeConstants.JJTADDNODE:
		case PnutsParserTreeConstants.JJTSUBTRACTNODE:
		case PnutsParserTreeConstants.JJTMULTNODE:
		case PnutsParserTreeConstants.JJTDIVIDENODE:
		case PnutsParserTreeConstants.JJTMODNODE:
		    return isConstant(node.jjtGetChild(0)) && isConstant(node.jjtGetChild(1));
		case PnutsParserTreeConstants.JJTNEGATIVENODE:
		case PnutsParserTreeConstants.JJTNOTNODE:
		case PnutsParserTreeConstants.JJTLOGNOTNODE:
		    return isConstant(node.jjtGetChild(0));
		default:
			return false;
		}
	}

	Object binary(String operator, SimpleNode node, Context context) {
		CompileContext cc = (CompileContext) context;
		ClassFile cf = cc.cf;
		int ctx = cc.getContextIndex();
		addLineInfo(cc, ctx, node);

		SimpleNode n0 = node.jjtGetChild(0);
		SimpleNode n1 = node.jjtGetChild(1);
		if (node.getAttribute("hasTryStatement") != null){
			int arg0 = cf.getLocal();
			int arg1 = cf.getLocal();
			n0.accept(this, context);
			cf.storeLocal(arg0);
			n1.accept(this, context);
			cf.storeLocal(arg1);
			cf.loadLocal(arg0);
			cf.loadLocal(arg1);
			cf.freeLocal(arg0);
			cf.freeLocal(arg1);
		} else {
			n0.accept(this, context);
			n1.accept(this, context);
		}
		cf.loadLocal(ctx);
		cf.add(Opcode.INVOKESTATIC, "pnuts.lang.Runtime", operator,
				"(Ljava/lang/Object;Ljava/lang/Object;Lpnuts/lang/Context;)",
				"Ljava/lang/Object;");
		return null;
	}

	public Object shiftLeftNode(SimpleNode node, Context context) {
		try {
			if (canConstantFold(node)) {
				addConstant(interpreter.shiftLeftNode(node, context), context);
				return null;
			}
		} catch (Exception e){
		}
		return binary("shiftLeft", node, context);
	}

	public Object shiftRightNode(SimpleNode node, Context context) {
		try {
			if (canConstantFold(node)) {
				addConstant(interpreter.shiftRightNode(node, context), context);
				return null;
			}
		} catch (Exception e){
		}
		return binary("shiftRight", node, context);
	}

	public Object shiftArithmeticNode(SimpleNode node, Context context) {
		try {
			if (canConstantFold(node)) {
				addConstant(interpreter.shiftArithmeticNode(node, context), context);
				return null;
			}
		} catch (Exception e){
		}
		return binary("shiftArithmetic", node, context);
	}

	static void addConstant(Object n, Context context) {
		CompileContext cc = (CompileContext) context;
		ClassFile cf = cc.cf;
		if (n instanceof String) {
			cf.add(Opcode.LDC, cf.addConstant((String) n));
			return;
		}
		String assoc = (String) cc.constants.get(n);
		if (assoc == null) {
			assoc = gensym(cc);
			cc.constants.put(n, assoc);
		}
		if (n instanceof Integer) {
			cf.add(Opcode.GETSTATIC, cc.constClassName, assoc,
					"Ljava/lang/Integer;");
		} else if (n instanceof Long) {
			cf.add(Opcode.GETSTATIC, cc.constClassName, assoc,
					"Ljava/lang/Long;");
		} else if (n instanceof Byte) {
			cf.add(Opcode.GETSTATIC, cc.constClassName, assoc,
					"Ljava/lang/Byte;");
		} else if (n instanceof BigInteger) {
			cf.add(Opcode.GETSTATIC, cc.constClassName, assoc,
					"Ljava/math/BigInteger;");
		} else if (n instanceof BigDecimal) {
			cf.add(Opcode.GETSTATIC, cc.constClassName, assoc,
					"Ljava/math/BigDecimal;");
		} else if (n instanceof Float) {
			cf.add(Opcode.GETSTATIC, cc.constClassName, assoc,
					"Ljava/lang/Float;");
		} else if (n instanceof Double) {
			cf.add(Opcode.GETSTATIC, cc.constClassName, assoc,
					"Ljava/lang/Double;");
		} else {
			throw new InternalError("compiler error");
		}

	}

	public Object addNode(SimpleNode node, Context context) {
		try {
			if (canConstantFold(node)) {
				addConstant(interpreter.addNode(node, context), context);
				return null;
			}
		} catch (Exception e){
		}
		return binary("add", node, context);
	}

	public Object subtractNode(SimpleNode node, Context context) {
		try {
			if (canConstantFold(node)) {
				addConstant(interpreter.subtractNode(node, context), context);
				return null;
			}
		} catch (Exception e){
		}
		return binary("subtract", node, context);
	}

	public Object multNode(SimpleNode node, Context context) {
		try {
			if (canConstantFold(node)) {
				addConstant(interpreter.multNode(node, context), context);
				return null;
			}
		} catch (Exception e){
		}
		return binary("multiply", node, context);
	}

	public Object divideNode(SimpleNode node, Context context) {
		try {
			if (canConstantFold(node)) {
				addConstant(interpreter.divideNode(node, context), context);
				return null;
			}
		} catch (Exception e) {
		}
		return binary("divide", node, context);
	}

	public Object modNode(SimpleNode node, Context context) {
		try {
			if (canConstantFold(node)) {
				addConstant(interpreter.modNode(node, context), context);
				return null;
			}
		} catch (Exception e){
		}
		return binary("mod", node, context);
	}

	public Object xorNode(SimpleNode node, Context context) {
		try {
			if (canConstantFold(node)) {
				Object xor = interpreter.xorNode(node, context);
				if (xor instanceof Number) {
					addConstant(xor, context);
					return null;
				} else if (xor instanceof Boolean) {
					CompileContext cc = (CompileContext) context;
					ClassFile cf = cc.cf;
					if (((Boolean) xor).booleanValue()) {
						cf.add(Opcode.ICONST_0);
					} else {
						cf.add(Opcode.ICONST_1);
					}
				}
			}
		} catch (Exception e){
		}
		return binary("xor", node, context);
	}

	public Object orNode(SimpleNode node, Context context) {
		try {
			if (canConstantFold(node)) {
				Object or = interpreter.orNode(node, context);
				if (or instanceof Number) {
					addConstant(or, context);
					return null;
				} else if (or instanceof Boolean) {
					CompileContext cc = (CompileContext) context;
					ClassFile cf = cc.cf;
					if (((Boolean) or).booleanValue()) {
						cf.add(Opcode.ICONST_0);
					} else {
						cf.add(Opcode.ICONST_1);
					}
				}
			}
		} catch (Exception e){
		}
		return binary("or", node, context);
	}

	public Object andNode(SimpleNode node, Context context) {
		try {
			if (canConstantFold(node)) {
				Object and = interpreter.andNode(node, context);
				if (and instanceof Number) {
					addConstant(and, context);
					return null;
				} else if (and instanceof Boolean) {
					if (((Boolean) and).booleanValue()) {
						CompileContext cc = (CompileContext) context;
						ClassFile cf = cc.cf;
						if (((Boolean) and).booleanValue()) {
							cf.add(Opcode.ICONST_0);
						} else {
							cf.add(Opcode.ICONST_1);
						}
					}
				}
			}
		} catch (Exception e){
		}
		return binary("and", node, context);
	}

	Object unary(String operator, SimpleNode node, Context context) {
		CompileContext cc = (CompileContext) context;
		int ctx = cc.getContextIndex();
		ClassFile cf = cc.cf;
		accept(node, 0, context);
		cf.loadLocal(ctx);
		cf.add(Opcode.INVOKESTATIC, "pnuts.lang.Runtime", operator,
				"(Ljava/lang/Object;Lpnuts/lang/Context;)",
				"Ljava/lang/Object;");
		return null;
	}

	public Object negativeNode(SimpleNode node, Context context) {
		return unary("negate", node, context);
	}

	public Object notNode(SimpleNode node, Context context) {
		return unary("not", node, context);
	}

	Object bool(String operator, SimpleNode node, Context context) {
		CompileContext cc = (CompileContext) context;
		ClassFile cf = cc.cf;
		int ctx = cc.getContextIndex();
		addLineInfo(cc, ctx, node);

		if (node.getAttribute("hasTryStatement") != null){
			int arg0 = cf.getLocal();
			int arg1 = cf.getLocal();
			accept(node, 0, cc);
			cf.storeLocal(arg0);
			accept(node, 1, cc);
			cf.storeLocal(arg1);
			cf.loadLocal(arg0);
			cf.loadLocal(arg1);
			cf.freeLocal(arg0);
			cf.freeLocal(arg1);
		} else {
			accept(node, 0, cc);
			accept(node, 1, cc);
		}
		cf.loadLocal(ctx);
		cf.add(Opcode.INVOKESTATIC, "pnuts.lang.Runtime", operator,
				"(Ljava/lang/Object;Ljava/lang/Object;Lpnuts/lang/Context;)",
				"Z");
		if (isConditionalNode(node)){
			node.setAttribute("inlinedBoolean", Boolean.TRUE);
		} else {
			Label deny = cf.getLabel();
			cf.add(Opcode.IFEQ, deny);
			cf.add(Opcode.GETSTATIC, "java.lang.Boolean", "TRUE",
					"Ljava/lang/Boolean;");
			Label next = cf.getLabel();
			cf.add(Opcode.GOTO, next);
			deny.fix();
			cf.popStack();
			cf.add(Opcode.GETSTATIC, "java.lang.Boolean", "FALSE",
					"Ljava/lang/Boolean;");
			next.fix();
		}
		return null;
	}

	public Object ltNode(SimpleNode node, Context context) {
		return bool("lt", node, context);
	}

	public Object leNode(SimpleNode node, Context context) {
		return bool("le", node, context);
	}

	public Object gtNode(SimpleNode node, Context context) {
		return bool("gt", node, context);
	}

	public Object geNode(SimpleNode node, Context context) {
		return bool("ge", node, context);
	}

	public Object equalNode(SimpleNode node, Context context) {
		return bool("eq", node, context);
	}

	public Object notEqNode(SimpleNode node, Context context) {
		return bool("ne", node, context);
	}

	public Object ifStatement(SimpleNode node, Context context) {
		CompileContext cc = (CompileContext) context;
		ClassFile cf = cc.cf;
		int ctx = cc.getContextIndex();

		addLineInfo(cc, ctx, node);

		SimpleNode condNode = node.jjtGetChild(0);
		condNode.accept(this, context);

		Label l_else = cf.getLabel();
		Label next = cf.getLabel();

		cc.openBranchEnv();

		if (condNode.getAttribute("inlinedBoolean") != null){
			cf.add(Opcode.IFEQ, l_else);
			accept(node, 1, context);
			cf.add(Opcode.GOTO, next);
			l_else.fix();
			cf.popStack();
		} else {
			booleanCheck(node.jjtGetChild(0).id, cf, context);
			cf.add(Opcode.CHECKCAST, "java.lang.Boolean");
			cf.add(Opcode.INVOKEVIRTUAL, "java.lang.Boolean", "booleanValue",
					"()", "Z");
			cf.add(Opcode.IFEQ, l_else);
			accept(node, 1, context);
			cf.add(Opcode.GOTO, next);
			l_else.fix();
			cf.popStack();
		}
		int n = node.jjtGetNumChildren();
		int j = 0;
		/*
		 * if (n < 3){ cf.add(Opcode.ACONST_NULL); cf.add(Opcode.GOTO, next); }
		 */
		for (int i = 2; i < n; i++) {
			SimpleNode _node = node.jjtGetChild(i);
			if (_node.id == PnutsParserTreeConstants.JJTELSEIFNODE) {
				cc.addBranch();

				SimpleNode _condNode = _node.jjtGetChild(0);
				_condNode.accept(this, context);

				Label next_else = cf.getLabel();
				if (_condNode.getAttribute("inlinedBoolean") != null){
					cf.add(Opcode.IFEQ, next_else);
					accept(_node, 1, context);
					cf.add(Opcode.GOTO, next);
					next_else.fix();
					cf.popStack();
				} else {
					booleanCheck(_node.jjtGetChild(0).id, cf, context);
					cf.add(Opcode.CHECKCAST, "java.lang.Boolean");
					cf.add(Opcode.INVOKEVIRTUAL, "java.lang.Boolean",
							"booleanValue", "()", "Z");
					cf.add(Opcode.IFEQ, next_else);
					accept(_node, 1, context);
					cf.add(Opcode.GOTO, next);
					next_else.fix();
					cf.popStack();
				}
			} else if (_node.id == PnutsParserTreeConstants.JJTELSENODE) {
				cc.addBranch();

				accept(_node, 0, context);
				if (i == n - 1) {
					next.fix();
					cc.closeBranchEnv();
					return null;
				} else {
					cf.add(Opcode.GOTO, next);
				}
			}
		}
		cf.add(Opcode.ACONST_NULL);

		cc.closeBranchEnv();

		next.fix();
		return null;
	}

	static boolean isLeafFrame(SimpleNode node) {
		FrameInfo info = (FrameInfo) node.getAttribute("frameInfo");
		if (!info.leafCheckDone) {
			scanLeafFrames(node);
		}
		return info.isLeaf;
	}

	private static boolean isFrame(SimpleNode node) {
		int id = node.id;
		if (id == PnutsParserTreeConstants.JJTFUNCTIONSTATEMENT
				|| id == PnutsParserTreeConstants.JJTMETHODDEF
				|| id == PnutsParserTreeConstants.JJTFOREACHSTATEMENT) {
			return true;
		}
		if (id == PnutsParserTreeConstants.JJTFORSTATEMENT) {
			SimpleNode c = node.jjtGetChild(0);
			if (c.id == PnutsParserTreeConstants.JJTFORENUM){
				return (c.jjtGetNumChildren() == 1);
			}
		}
		return false;
	}

	static boolean scanLeafFrames(SimpleNode node) {
		boolean c = true;
		int n = node.jjtGetNumChildren();
		for (int i = 0; i < n; i++) {
			SimpleNode child = node.jjtGetChild(i);
			if (!scanLeafFrames(child)) {
				if (isFrame(node)) {
					FrameInfo info = (FrameInfo)node.getAttribute("frameInfo");
					if (info == null){
						node.setAttribute("frameInfo", info = new FrameInfo());
					}
					info.isLeaf = false;
					info.leafCheckDone = true;
				}
				c = false;
			}
		}
		boolean c1 = !isFrame(node);
		boolean c2 = (c && c1);
		if (node.id == PnutsParserTreeConstants.JJTFUNCTIONSTATEMENT ||
		    node.id == PnutsParserTreeConstants.JJTMETHODDEF){
			FrameInfo info = (FrameInfo)node.getAttribute("frameInfo");
			if (info == null){
				node.setAttribute("frameInfo", info = new FrameInfo());
			}
			info.isLeaf = c;
			info.leafCheckDone = true;
		}
		return c2;
	}

	public Object functionStatement(SimpleNode node, Context context) {
		FrameInfo info = (FrameInfo)node.getAttribute("frameInfo");
		if (info == null){
			node.setAttribute("frameInfo", info = new FrameInfo());
		}
		if (!info.preprocessed) {
			preprocess(node);
		}

		CompileContext cc = (CompileContext) context;
		int ctx = cc.getContextIndex();
		ClassFile cf = cc.cf;

		addLineInfo(cc, ctx, node);

		SimpleNode block = node.jjtGetChild(1);
		SimpleNode param = node.jjtGetChild(0);
		int nargs = param.jjtGetNumChildren();
		boolean varargs = "[".equals(param.str);
		String[] locals = new String[nargs];

		SimpleNode n0 = null;
		if (nargs == 1
				&& (n0 = param.jjtGetChild(0)).id == PnutsParserTreeConstants.JJTINDEXNODE) {
			nargs = -1;
			locals[0] = n0.jjtGetChild(0).str;
		} else {
			for (int j = 0; j < nargs; j++) {
				locals[j] = param.jjtGetChild(j).str;
			}
		}

		boolean isGenerator = Runtime.isGenerator(block);

		String cls = className + "$" + (classCount++ & 0x7fffffffffffffffL);
                node.setAttribute("classname", cls); 

		cf = new ClassFile(cls, "pnuts.lang.Function", sourceFile,
				Constants.ACC_PUBLIC);
		cf.addInterface("pnuts.compiler.Compiled");
		cc.classFiles.add(cf);
		cf.parent = cc.cf;

		/*
		 * public PnutsFunction register(PnutsFunction pf, boolean);
		 */
		cf.openMethod("register",
				"(Lpnuts/lang/PnutsFunction;Z)Lpnuts/lang/PnutsFunction;",
				Constants.ACC_PUBLIC);

		cf.add(Opcode.ALOAD_0);
		cf.add(Opcode.ALOAD_1);
		cf.add(Opcode.ILOAD_2);
		cf.add(Opcode.INVOKESPECIAL, "pnuts.lang.Function", "register",
				"(Lpnuts/lang/PnutsFunction;Z)", "Lpnuts/lang/PnutsFunction;");
		cf.add(Opcode.ARETURN);
		cf.closeMethod();

		if (optimize){
			/*
			 * protected String unparse(Context context);
			 */
			cf.openMethod("unparse", "(Lpnuts/lang/Context;)Ljava/lang/String;",
				      Constants.ACC_PROTECTED);
			cf.pushString(Runtime.unparse(node, context));
			cf.add(Opcode.ARETURN);
			cf.closeMethod();
		} else {
			/*
			 * protected SimpleNode getNode();
			 */
			cf.openMethod("getNode", "()Lpnuts/lang/SimpleNode;", Constants.ACC_PROTECTED);
			cf.pushString(Runtime.saveNode(node));
			cf.add(Opcode.INVOKESTATIC, "pnuts.lang.Runtime", "loadNode", "(Ljava/lang/String;)", "Lpnuts/lang/SimpleNode;");
			cf.add(Opcode.ARETURN);
			cf.closeMethod();
		}

		String fname = node.str;

		/*
		 * protected Object exec(Object[] args, Context conext);
		 */
		cc._openFrame(fname, locals, isLeafFrame(node));
		cf.openMethod("exec",
				"([Ljava/lang/Object;Lpnuts/lang/Context;)Ljava/lang/Object;",
				Constants.ACC_PROTECTED);
		CompileContext cc2 = (CompileContext) cc.clone(false, true);
		cc2.line = 0xffffffff;
		cc2.cf = cf;
		cc2.setContextIndex(2);
		cc2.returnLabel = cf.getLabel();

		ByteBuffer code = cf.getCodeBuffer();
		ByteBuffer blockCode = new ByteBuffer();
		cf.setCodeBuffer(blockCode);

		if (fname != null) {
			int f = cf.getLocal();
			//cc2._declare(fname, f, -1);
			cc2._declare_frame(fname, f);
			cf.add(Opcode.ALOAD_0);
			cf.add(Opcode.GETFIELD, "pnuts.lang.Function", "function",
					"Lpnuts/lang/PnutsFunction;");
			cf.storeLocal(f);
		}

		int expt = cf.getLocal();
		cc2._declare(EXCEPTOIN_FIELD_SYMBOL, expt, -1);

		CompileContext cc3 = null;
		ClassFile cf2 = null;

		if (isGenerator) {

			String cls2 = className + "$" + (classCount++ & 0x7fffffffffffffffL);
			cf2 = new ClassFile(cls2, "pnuts.lang.PnutsFunction", sourceFile,
					Constants.ACC_PUBLIC);

			cc.classFiles.add(cf2);
			cf2.parent = cf;

			String closureSymbol = YIELD;

			SimpleNode gnode = Generator.convertYield(block, closureSymbol);

			cc3 = (CompileContext) cc2.clone();
			cc3.cf = cf2;

			/*
			 * protected Object exec(Object[], Context)
			 */
			cc3._openFrame(fname, locals, false);
			cf2.openMethod("exec",
				       "([Ljava/lang/Object;Lpnuts/lang/Context;)Ljava/lang/Object;",
				       Constants.ACC_PROTECTED);

			cf2.loadLocal(0);
			cf2.add(Opcode.GETFIELD, cls2, "$context$", "Lpnuts/lang/Context;");
			cf2.storeLocal(2);

			cc3.setContextIndex(2);
			cc3.returnLabel = cf2.getLabel();

			ByteBuffer code2 = cf2.getCodeBuffer();
			ByteBuffer blockCode2 = new ByteBuffer();
			cf2.setCodeBuffer(blockCode2);

			cc3.openScope(new String[] {});
			int args = cf2.getLocal();
			cf2.loadLocal(0);
			cf2.add(Opcode.GETFIELD, cls2, "args", "[Ljava/lang/Object;");
			cf2.storeLocal(args);
			for (int i = 0; i < locals.length; i++) {
				cc3._declare(locals[i], args, i);
			}
			int closure = cf2.getLocal();

			cc3._declare(closureSymbol, closure, -1);

			cf2.add(Opcode.ALOAD_1);
			cf2.add(Opcode.ICONST_0);
			cf2.add(Opcode.AALOAD);
			cf2.add(Opcode.CHECKCAST, "pnuts.lang.PnutsFunction");
			cf2.storeLocal(closure);

			gnode.accept(this, cc3);

			cc3.returnLabel.fix();

			cf2.setCodeBuffer(code2);

			LocalInfo[] infos = cc3.env.bottom.info;
			int count = cc3.env.bottom.count;

			for (int i = 0; i < infos.length; i++) {
				if (infos[i] != null) {
					int idx = infos[i].index;
					String sym = infos[i].symbol;
					int map = infos[i].map;
					if (idx < 0){
						cf2.add(Opcode.ACONST_NULL);
						cf2.storeLocal(map);
					} else if (idx == 0){
						cf2.add(Opcode.ICONST_1);
						cf2.add(Opcode.ANEWARRAY, "java.lang.Object");
						cf2.add(Opcode.DUP);
						cf2.add(Opcode.ICONST_0);
						cf2.add(Opcode.ACONST_NULL);

						cf2.add(Opcode.AASTORE);
						cf2.storeLocal(map);
					}
				}
			}


			cf2.shift(code2.size());

			blockCode2.prepend(code2);
			cf2.setCodeBuffer(blockCode2);

			cf2.add(Opcode.ARETURN);

			cc3.closeScope();

			cf2.closeMethod();

			ArrayList exports = (ArrayList) cc3.env.parent.exports.get(cc3.env);

			cc3._closeFrame();

			StringBuffer arg = new StringBuffer("([Ljava/lang/Object;");
			int n_names = 0;

			if (exports != null) {
				n_names = exports.size();
			}
			String names[] = new String[n_names];

			int j = 0;
			if (n_names > 0) {
				for (int i = 0, n = exports.size(); i < n; i++) {
					names[j++] = ((Reference) exports.get(i)).symbol;
					arg.append("[Ljava/lang/Object;");
				}
			}

			arg.append("Lpnuts/lang/Context;");
			arg.append(')');

			cf2.openMethod("<init>", arg + "V", Constants.ACC_PUBLIC);
			cf2.add(Opcode.ALOAD_0);
			cf2.add(Opcode.INVOKESPECIAL, "pnuts.lang.PnutsFunction", "<init>",
					"()", "V");

			cf2.addField("args", "[Ljava/lang/Object;", Constants.ACC_PRIVATE);
			cf2.add(Opcode.ALOAD_0);
			cf2.add(Opcode.ALOAD_1);
			cf2.add(Opcode.PUTFIELD, cls2, "args", "[Ljava/lang/Object;");

			for (int i = 0; i < n_names; i++) {
				cf2.addField(names[i], "[Ljava/lang/Object;", (short) 0);
				cf2.add(Opcode.ALOAD_0);
				cf2.loadLocal(2 + i);
				cf2.add(Opcode.PUTFIELD, cf2.getClassName(), names[i],
						"[Ljava/lang/Object;");
			}

			cf2.addField("$context$", "Lpnuts/lang/Context;", (short) 0);
			cf2.add(Opcode.ALOAD_0);
			cf2.loadLocal(2 + n_names);
			cf2.add(Opcode.ICONST_0);
			cf2.add(Opcode.ICONST_0);
			cf2.add(Opcode.INVOKEVIRTUAL, "pnuts.lang.Context", "clone",
					"(ZZ)", "Ljava/lang/Object;");
			cf2.add(Opcode.CHECKCAST, "pnuts.lang.Context");
			cf2.add(Opcode.PUTFIELD, cf2.getClassName(), "$context$",
					"Lpnuts/lang/Context;");

			cf2.add(Opcode.RETURN);
			cf2.closeMethod();

			//	    cc.debug(cf2);

			cf.add(Opcode.NEW, "pnuts.lang.Generator");
			cf.add(Opcode.DUP);

			cf.add(Opcode.NEW, cls2);
			cf.add(Opcode.DUP);
			cf.loadLocal(1);

			if (exports != null) {
				int size = exports.size();
				for (int i = 0; i < size; i++) {
					Reference ref = (Reference) exports.get(i);
					if (ref.index < 0) {
						cf.add(Opcode.ALOAD_0);
						cf.add(Opcode.GETFIELD, cf.getClassName(), ref.symbol,
								"[Ljava/lang/Object;");
					} else if (ref.offset < 0) {
						cf.add(Opcode.ICONST_1);
						cf.add(Opcode.ANEWARRAY, "java.lang.Object");
						cf.add(Opcode.DUP);
						cf.add(Opcode.ICONST_0);
						cf.loadLocal(ref.index);
						cf.add(Opcode.AASTORE);
					} else {
						cf.loadLocal(ref.index);
						cf.add(Opcode.CHECKCAST, "[Ljava/lang/Object;");
					}
				}
			}

			cf.loadLocal(2);

			cf.add(Opcode.INVOKESPECIAL, cls2, "<init>", arg.toString(), "V");

			cf.add(Opcode.INVOKESPECIAL, "pnuts.lang.Generator", "<init>",
					"(Lpnuts/lang/PnutsFunction;)", "V");

		} else {
			if (info.freeVars != null) {
				int loc = cf.getLocal();
				for (Iterator it = info.freeVars.iterator(); it.hasNext();) {
					String var = (String) it.next();
					if (var == fname){
					    continue;
					}
					cc2.declare(var);
					Reference ref = cc2.getReference(var);
					cf.loadLocal(cc2.getContextIndex());
					cf.add(Opcode.LDC, cf.addConstant(var));
					cf.add(Opcode.INVOKEVIRTUAL, "pnuts.lang.Context", "getId",
							"(Ljava/lang/String;)", "Ljava/lang/Object;");
					cf.storeLocal(loc);
					ref.set(cf, loc);
				}
				cf.freeLocal(loc);
			}
			block.accept(this, cc2);
		}

		cc2.returnLabel.fix();

		cf.setCodeBuffer(code);

		LocalInfo[] infos = cc.env.bottom.info;
		int count = cc.env.bottom.count;

		for (int i = 0; i < cc.env.bottom.count; i++) {
			int idx = infos[i].index;
			String sym = infos[i].symbol;
			int map = infos[i].map;
			if (idx < 0){
				cf.add(Opcode.ACONST_NULL);
				cf.storeLocal(map);
			} else if (idx == 0){
				cf.add(Opcode.ICONST_1);
				cf.add(Opcode.ANEWARRAY, "java.lang.Object");
				cf.add(Opcode.DUP);
				cf.add(Opcode.ICONST_0);
				cf.add(Opcode.ACONST_NULL);

				cf.add(Opcode.AASTORE);
				cf.storeLocal(map);
			}
		}

		Reference eref = cc2.getReference(EXCEPTOIN_FIELD_SYMBOL);
		cf.add(Opcode.ACONST_NULL);
		cf.storeLocal(eref.index);

		cf.shift(code.size());

		Label catchStart = cf.getLabel(true);

		blockCode.prepend(code);
		cf.setCodeBuffer(blockCode);

		boolean finallyIsSet = cc2.env.finallySet;
		Label jsrTag = cf.getLabel();
		Label finallyEnd = cf.getLabel();

		if (finallyIsSet) {
			int value = cf.getLocal();
			cf.storeLocal(value);
			cf.add(Opcode.JSR, jsrTag);
			cf.loadLocal(value);
			cf.freeLocal(value);
		}

		cf.add(Opcode.ARETURN);

		Label catchEnd = cf.getLabel(true);
		Label catchTarget = cf.getLabel(true);
		Label finallyTarget = cf.getLabel();
		cf.reserveStack(1);

		int caught = cf.getLocal();
		cf.storeLocal(caught);
		cf.loadLocal(cc2.getContextIndex());
		cf.loadLocal(caught);
		cf.freeLocal(caught);

		if (!isGenerator) {
		    cf.loadLocal(eref.index);
		    cf.add(Opcode.CHECKCAST, "pnuts.lang.Runtime$TypeMap");
		    cf.add(Opcode.INVOKESTATIC,
			   "pnuts.lang.Runtime",
			   "checkException",
			   "(Lpnuts/lang/Context;Ljava/lang/Throwable;Lpnuts/lang/Runtime$TypeMap;)",
			   "V");
		}
		if (finallyIsSet) {
			finallyEnd.fix();
			cf.add(Opcode.JSR, jsrTag);
		}

		cf.add(Opcode.ACONST_NULL);
		cf.add(Opcode.ARETURN);

		if (finallyIsSet) {
			int throwable = cf.getLocal();
			int returnAddr = cf.getLocal();

			finallyTarget.fix();

			cf.reserveStack(1);
			cf.storeLocal(throwable);
			cf.add(Opcode.JSR, jsrTag);
			cf.loadLocal(throwable);
			cf.freeLocal(throwable);
			cf.add(Opcode.ATHROW);

			jsrTag.fix();

			cf.reserveStack(1);
			cf.storeLocal(returnAddr);
			Reference ref = cc.getReference("!finally");
			cf.loadLocal(ref.index);
			cf.add(Opcode.CHECKCAST, "pnuts.lang.PnutsFunction");
			cf.add(Opcode.GETSTATIC, className, "NO_PARAM",
					"[Ljava/lang/Object;");

			cf.loadLocal(cc2.getContextIndex());
			cf.add(Opcode.INVOKEVIRTUAL, "pnuts.lang.PnutsFunction", "call",
					"([Ljava/lang/Object;Lpnuts/lang/Context;)",
					"Ljava/lang/Object;");
			cf.add(Opcode.POP);
			cf.add(Opcode.RET, returnAddr);
			cf.freeLocal(returnAddr);
		}

		cf.addExceptionHandler(catchStart, catchEnd, catchTarget,
				"java.lang.Throwable");

		if (finallyIsSet) {
			cf.addExceptionHandler(catchStart, finallyEnd, finallyTarget, null);
		}

		StringBuffer arg = new StringBuffer(
				"(Ljava/lang/String;[Ljava/lang/String;IZLpnuts/lang/SimpleNode;Lpnuts/lang/Package;Lpnuts/lang/Context;");
		int n_names = 0;
		if (cc.env.imports != null) {
			n_names = cc.env.imports.size();
		}

		String names[] = new String[n_names];

		int j = 0;
		if (n_names > 0) {
			List imports = cc.env.imports;
			for (int i = 0, n = imports.size(); i < n; i++){
				String sym = (String)imports.get(i);
				if (DEBUG) {
					System.out.println(fname + ": " + sym);
				}
				names[j++] = sym;
				arg.append("[Ljava/lang/Object;");
			}
		}

		if (DEBUG) {
			System.out.println(cf.getClassName() + ": " + arg);
		}

		cf.closeMethod();
		cc._closeFrame();

		arg.append(")");
		/*
		 * constructor
		 */
		cf.openMethod("<init>", arg + "V", (short) 0);
		cf.add(Opcode.ALOAD_0);
		cf.add(Opcode.ALOAD_1); // String name
		cf.add(Opcode.ALOAD_2); // String[] locals
		cf.add(Opcode.ILOAD_3); // int nargs
		cf.add(Opcode.ILOAD, 4); // int nargs
		cf.add(Opcode.ALOAD, 5); // SimpleNode
		cf.add(Opcode.ALOAD, 6); // Package
		cf.add(Opcode.ALOAD, 7); // Context
		cf.add(Opcode.INVOKESPECIAL,
		       "pnuts.lang.Function",
		       "<init>",
		       "(Ljava/lang/String;[Ljava/lang/String;IZLpnuts/lang/SimpleNode;Lpnuts/lang/Package;Lpnuts/lang/Context;)",
		       "V");

		for (int i = 0; i < n_names; i++) {
			cf.addField(names[i], "[Ljava/lang/Object;", (short) 0);
			cf.add(Opcode.ALOAD_0);
			cf.add(Opcode.ALOAD, 8 + i);
			cf.add(Opcode.PUTFIELD, cf.getClassName(), names[i],
					"[Ljava/lang/Object;");
		}

		cf.add(Opcode.RETURN);
		cf.closeMethod();

		cf = cc.cf;
		cf.loadLocal(ctx);
		cf.add(Opcode.INVOKEVIRTUAL, "pnuts.lang.Context", "getCurrentPackage",
				"()", "Lpnuts/lang/Package;");
		int pkg = cf.getLocal();
		cf.storeLocal(pkg);

		cf.add(Opcode.NEW, cls);
		cf.add(Opcode.DUP);
		int fnameIndex = 0;
		if (fname != null) {
			fnameIndex = cf.addConstant(fname);
			cf.add(Opcode.LDC, fnameIndex);
		} else {
			cf.add(Opcode.ACONST_NULL);
		}
		cf.pushInteger(locals.length);
		cf.add(Opcode.ANEWARRAY, "java.lang.String");
		for (int i = 0; i < locals.length; i++) {
			cf.add(Opcode.DUP);
			cf.pushInteger(i);
			cf.add(Opcode.LDC, cf.addConstant(locals[i]));
			cf.add(Opcode.AASTORE);
		}
		cf.pushInteger(nargs);
		cf.add(varargs ? Opcode.ICONST_1 : Opcode.ICONST_0);
		cf.add(Opcode.ACONST_NULL);
		cf.loadLocal(pkg);
		cf.loadLocal(ctx);

		ArrayList exports;

		if (isGenerator) {
			exports = (ArrayList) cc.env.exports.get(cc3.env);
		} else {
			exports = (ArrayList) cc.env.exports.get(cc2.env);
		}

		if (exports != null) {
			int size = exports.size();
			for (int i = 0; i < size; i++) {
				Reference ref = (Reference) exports.get(i);
				if (ref.index < 0) {
					cf.add(Opcode.ALOAD_0);
					cf.add(Opcode.GETFIELD, cf.getClassName(), ref.symbol,
							"[Ljava/lang/Object;");
				} else if (ref.offset < 0) {
					cf.add(Opcode.ICONST_1);
					cf.add(Opcode.ANEWARRAY, "java.lang.Object");
					cf.add(Opcode.DUP);
					cf.add(Opcode.ICONST_0);
					cf.loadLocal(ref.index);
					cf.add(Opcode.AASTORE);
				} else {
					cf.loadLocal(ref.index);
					cf.add(Opcode.CHECKCAST, "[Ljava/lang/Object;");
				}
			}
		}

		cf.add(Opcode.INVOKESPECIAL, cls, "<init>", arg.toString(), "V");

		int func = cf.getLocal();
		cf.storeLocal(func);

		int pfunc = cf.getLocal();
		cf.add(Opcode.ACONST_NULL);
		cf.storeLocal(pfunc);

		Reference ref = null;
		Reference ref2 = null;
		if (fname != null && node.getAttribute("isMethod") == null) {
			ref = cc.env.findReference(fname); // without lexical scope
			ref2 = cc.findReference(fname); // with lexical scope
			if (ref != null && ref.frame != cc.env) {
				getRef(ref, cc);
				cf.storeLocal(pfunc);

				cf.loadLocal(pfunc);
				cf.add(Opcode.INSTANCEOF, "pnuts.lang.PnutsFunction");
				Label instance = cf.getLabel();
				cf.add(Opcode.IFNE, instance);
				cf.add(Opcode.ACONST_NULL);
				cf.storeLocal(pfunc);
				instance.fix();

				cf.loadLocal(func);
				cf.loadLocal(pfunc);
				cf.add(Opcode.CHECKCAST, "pnuts.lang.PnutsFunction");
				cf.add(Opcode.ICONST_0);
				cf.add(Opcode.INVOKEVIRTUAL, cls, "register",
						"(Lpnuts/lang/PnutsFunction;Z)",
						"Lpnuts/lang/PnutsFunction;");
				cf.storeLocal(pfunc);

			} else if (ref2 != null) { // ref == null && ref2 != null
				getRef(ref2, cc);
				cf.storeLocal(pfunc);

				cf.loadLocal(pfunc);
				cf.add(Opcode.INSTANCEOF, "pnuts.lang.PnutsFunction");
				Label instance = cf.getLabel();
				cf.add(Opcode.IFNE, instance);
				cf.add(Opcode.ACONST_NULL);
				cf.storeLocal(pfunc);
				instance.fix();

				cf.loadLocal(func);
				cf.loadLocal(pfunc);
				cf.add(Opcode.CHECKCAST, "pnuts.lang.PnutsFunction");
				cf.add(Opcode.ICONST_1);
				cf.add(Opcode.INVOKEVIRTUAL, cls, "register",
						"(Lpnuts/lang/PnutsFunction;Z)",
						"Lpnuts/lang/PnutsFunction;");
				cf.storeLocal(pfunc);

			} else if (cc.env.parent != null) {
				cf.loadLocal(func);
				cf.add(Opcode.LDC, fnameIndex);
				cf.loadLocal(pkg);
				cf.add(Opcode.INVOKESTATIC,
					"pnuts.lang.Runtime",
					"defineUnboundFunction",
					"(Lpnuts/lang/Function;Ljava/lang/String;Lpnuts/lang/Package;)",
					"Lpnuts/lang/PnutsFunction;");
				cf.storeLocal(pfunc);

				cc.declare(fname);
				ref = cc.getReference(fname);
				ref.set(cf, pfunc);
				cf.loadLocal(pfunc);
				return null;
			} else {
				cf.loadLocal(func);
				cf.add(Opcode.LDC, fnameIndex);
				cf.loadLocal(pkg);
				cf.loadLocal(ctx);
				cf.add(
				       Opcode.INVOKESTATIC,
				       "pnuts.lang.Runtime",
				       "defineTopLevelFunction",
				       "(Lpnuts/lang/Function;Ljava/lang/String;Lpnuts/lang/Package;Lpnuts/lang/Context;)",
				       "Lpnuts/lang/PnutsFunction;");
				return null;
			}
		} else { // fname == null
			cf.loadLocal(func);
			cf.add(Opcode.ACONST_NULL);
			cf.add(Opcode.ICONST_0);
			cf.add(Opcode.INVOKEVIRTUAL, cls, "register",
					"(Lpnuts/lang/PnutsFunction;Z)",
					"Lpnuts/lang/PnutsFunction;");
			return null;
		}

		if (cc.env.parent != null) {
			if (ref == null) {
				cc.declare(fname);
				ref = cc.getReference(fname);
			} else {
				cc.redefine(fname);
			}
			ref.set(cf, pfunc);
			cf.loadLocal(pfunc);
		} else {
			cf.loadLocal(pkg);
			cf.add(Opcode.LDC, fnameIndex);
			cf.loadLocal(pfunc);
			cf.loadLocal(ctx);
			cf.add(Opcode.INVOKEVIRTUAL,
			       "pnuts.lang.Package",
			       "set",
			       "(Ljava/lang/String;Ljava/lang/Object;Lpnuts/lang/Context;)",
			       "V");
			cf.loadLocal(pfunc);
		}
		cf.freeLocal(func);
		cf.freeLocal(pfunc);
		cf.freeLocal(pkg);
		return null;
	}

	public Object applicationNode(SimpleNode node, Context context) {
		CompileContext cc = (CompileContext) context;
		ClassFile cf = cc.cf;
		int ctx = cc.getContextIndex();

		addLineInfo(cc, ctx, node);

		SimpleNode n0 = node.jjtGetChild(0);
		if (n0.id == PnutsParserTreeConstants.JJTIDNODE){
			String sym = n0.str;
			Reference ref = cc.getReference(sym);
			if (ref != null){
				Frame frame = ref.frame;
				if (frame != null){
					SimpleNode n1 = node.jjtGetChild(1);
					int nargs = n1.jjtGetNumChildren();
					if (frame.fname == sym && frame.locals.length == nargs){
						if (DEBUG){
							System.out.println("recursive " + sym);
						}
						//System.out.println("ref.frame = " + frame + ", env=" + cc.env);
						cf.add(Opcode.ALOAD_0);
						_listElements(node.jjtGetChild(1), context);
						cf.loadLocal(ctx);
						cf.add(Opcode.INVOKEVIRTUAL,
							"pnuts.lang.Function",
							"exec",
							"([Ljava/lang/Object;Lpnuts/lang/Context;)",
							"Ljava/lang/Object;");
						return null;
					}
				}
			}
		}

		if (node.getAttribute("hasTryStatement") != null){
			int tgt = cf.getLocal();
			int arg = cf.getLocal();
			accept(node, 0, context);
			cf.storeLocal(tgt);
			//	    accept(node, 1, context);
			_listElements(node.jjtGetChild(1), context);
			cf.storeLocal(arg);

			cf.loadLocal(ctx);
			cf.loadLocal(tgt);
			cf.loadLocal(arg);

			cf.freeLocal(tgt);
			cf.freeLocal(arg);
		} else {
			cf.loadLocal(ctx);
			accept(node, 0, context);
			//	    accept(node, 1, context);
			_listElements(node.jjtGetChild(1), context);
		}

		SimpleNode argNode = node.jjtGetChild(1);
		int nargs = argNode.jjtGetNumChildren();

		boolean types_created = false;
		for (int i = 0; i < nargs; i++) {
			SimpleNode n = argNode.jjtGetChild(i);
			if (n.id == PnutsParserTreeConstants.JJTCASTEXPRESSION) {
				if (!types_created) {
					cf.pushInteger(nargs);
					cf.add(Opcode.ANEWARRAY, "java.lang.Class");
					types_created = true;
				}
				cf.add(Opcode.DUP);
				cf.pushInteger(i);

				resolveType(n.jjtGetChild(0), cc, ctx);

				cf.add(Opcode.AASTORE);
			}
		}
		if (!types_created) {
			cf.add(Opcode.ACONST_NULL);
		}

		if (_includeLineNo) {
			cf.pushInteger(node.beginLine);
			if (_includeColumnNo){
				cf.pushInteger(node.beginColumn);
			} else {
				cf.pushInteger(-1);
			}
			cf.add(Opcode.INVOKESTATIC,
			       "pnuts.lang.Runtime",
			       "call",
			       "(Lpnuts/lang/Context;Ljava/lang/Object;[Ljava/lang/Object;[Ljava/lang/Class;II)",
			       "Ljava/lang/Object;");
		} else {
			cf.add(Opcode.INVOKESTATIC,
			       "pnuts.lang.Runtime",
			       "call",
			       "(Lpnuts/lang/Context;Ljava/lang/Object;[Ljava/lang/Object;[Ljava/lang/Class;)",
			       "Ljava/lang/Object;");
		}

		return null;
	}

	public Object tryStatement(SimpleNode node, Context context) {
		CompileContext cc = (CompileContext) context;
		ClassFile cf = cc.cf;
		/*
		 * if (node.jjtGetParent().id ==
		 * PnutsParserTreeConstants.JJTLISTELEMENTS){ throw new
		 * PnutsException("try/catch is not allowed in parameters", context); }
		 * if (cf.stackTop > 0){ throw new PnutsException("try/catch is not
		 * allowed in parameters", context); }
		 */
		int throwable = cf.getLocal();
		cf.add(Opcode.ACONST_NULL);
		cf.storeLocal(throwable);

		int value = cf.getLocal();
		cf.add(Opcode.ACONST_NULL);
		cf.storeLocal(value);

		Label next = cf.getLabel();
		Label finalTag = cf.getLabel();
		Label jsrTag = cf.getLabel();

		int n = node.jjtGetNumChildren();
		SimpleNode lastNode = node.jjtGetChild(n - 1);
		SimpleNode finallyBlock = null;
		if (lastNode.id == PnutsParserTreeConstants.JJTFINALLYBLOCK) {
			finallyBlock = lastNode;
			cc.pushFinallyBlock(jsrTag);
		}
		boolean hasCatchBlock = false;
		if (n > 1
				&& node.jjtGetChild(1).id == PnutsParserTreeConstants.JJTCATCHBLOCK) {
			hasCatchBlock = true;
		}

		Label catchStart = cf.getLabel(true);

		accept(node, 0, context);
		cf.storeLocal(value);

		Label catchEnd = cf.getLabel(true);

		if (finallyBlock != null) {
			cf.add(Opcode.JSR, jsrTag);
		}
		cf.add(Opcode.GOTO, next);

		Label escape = cf.getLabel(true);
		cf.reserveStack(1);

		cf.storeLocal(throwable);
		cf.loadLocal(throwable);
		cf.add(Opcode.ATHROW);

		Label catchTarget = null;
		if (hasCatchBlock) {
			catchTarget = cf.getLabel(true);
			cf.reserveStack(1);
			cf.storeLocal(throwable);

			cf.loadLocal(throwable);
			cf.add(Opcode.INSTANCEOF, "pnuts.lang.PnutsException");
			Label l1 = cf.getLabel();
			cf.add(Opcode.IFEQ, l1);
			cf.loadLocal(throwable);
			cf.add(Opcode.CHECKCAST, "pnuts.lang.PnutsException");
			cf.add(Opcode.INVOKEVIRTUAL, "pnuts.lang.PnutsException",
					"getThrowable", "()", "Ljava/lang/Throwable;");
			cf.storeLocal(throwable);
			l1.fix();

			int cls = cf.getLocal();

			for (int i = 1; i < n; i++) {
				SimpleNode c = node.jjtGetChild(i);
				if (c.id == PnutsParserTreeConstants.JJTCATCHBLOCK) {
					String var = c.str;
					StringBuffer sbuf = new StringBuffer();
					SimpleNode classNode = c.jjtGetChild(0);
					sbuf.append(classNode.jjtGetChild(0).str);
					for (int j = 1; j < classNode.jjtGetNumChildren(); j++) {
						sbuf.append('.');
						sbuf.append(classNode.jjtGetChild(j).str);
					}
					cf.loadLocal(cc.getContextIndex());
					cf.add(Opcode.LDC, cf.addConstant(sbuf.toString()));
					cf.add(Opcode.INVOKEVIRTUAL, "pnuts.lang.Context",
							"resolveClass", "(Ljava/lang/String;)",
							"Ljava/lang/Class;");
					cf.storeLocal(cls);
					cf.loadLocal(cls);
					Label nextCatch = cf.getLabel();
					cf.add(Opcode.IFNULL, nextCatch);
					cf.loadLocal(cls);
					cf.loadLocal(throwable);
					cf.add(Opcode.INVOKEVIRTUAL, "java.lang.Class",
							"isInstance", "(Ljava/lang/Object;)", "Z");
					cf.add(Opcode.IFEQ, nextCatch);
					cc.openScope(new String[] {});
					cc._declare(var, throwable, -1);
					accept(c, 1, context);
					cc.closeScope();
					cf.storeLocal(value);
					if (finallyBlock != null) {
						cf.add(Opcode.JSR, jsrTag);
					}
					cf.add(Opcode.GOTO, next);
					nextCatch.fix();
				}
			}
			cf.loadLocal(throwable);
			cf.add(Opcode.ATHROW);
		}

		if (finallyBlock != null) {
			finalTag = cf.getLabel(true);
			cf.reserveStack(1);
			cf.storeLocal(throwable);
			cf.add(Opcode.JSR, jsrTag);
			cf.loadLocal(throwable);
			cf.add(Opcode.ATHROW);

			jsrTag.fix();

			int retAddr = cf.getLocal();
			cf.reserveStack(1);
			cf.storeLocal(retAddr);

			cc.popFinallyBlock();
			accept(finallyBlock, 0, context);
			cf.add(Opcode.POP);
			cf.add(Opcode.RET, retAddr);
		}

		cf.addExceptionHandler(catchStart, catchEnd, escape,
				"pnuts.lang.Escape");
		if (hasCatchBlock) {
			cf.addExceptionHandler(catchStart, catchEnd, catchTarget,
					"java.lang.Throwable");
		}
		if (finallyBlock != null) {
			cf.addExceptionHandler(catchStart, finalTag, finalTag, null);
		}

		next.fix();

		cf.loadLocal(value);

		//	cf.freeLocal(throwable);
		//	cf.freeLocal(value);

		return null;
	}

	public Object catchBlock(SimpleNode node, Context context){
	       return null;
	}

	public Object blockNode(SimpleNode node, Context context) {
		CompileContext cc = (CompileContext) context;
		ClassFile cf = cc.cf;
		int n = node.jjtGetNumChildren();

		if (n > 0) {
			int m = n - 1;
			for (int i = 0; i < m; i++) {
				accept(node, i, context);
				cf.add(Opcode.POP);
			}
			accept(node, m, context);
		} else {
			cf.add(Opcode.ACONST_NULL);
		}
		return null;
	}

	public Object trueNode(SimpleNode node, Context context) {
		CompileContext cc = (CompileContext) context;
		ClassFile cf = cc.cf;
		if (isConditionalNode(node)){
			node.setAttribute("inlinedBoolean", Boolean.TRUE);
			cf.add(Opcode.ICONST_1);
		} else {
			cf.add(Opcode.GETSTATIC, "java.lang.Boolean", "TRUE",
					"Ljava/lang/Boolean;");
		}
		return null;
	}

	public Object falseNode(SimpleNode node, Context context) {
		CompileContext cc = (CompileContext) context;
		ClassFile cf = cc.cf;
		if (isConditionalNode(node)){
			node.setAttribute("inlinedBoolean", Boolean.TRUE);
			cf.add(Opcode.ICONST_0);
		} else {
			cc.cf.add(Opcode.GETSTATIC, "java.lang.Boolean", "FALSE",
					"Ljava/lang/Boolean;");
		}
		return null;
	}

	public Object nullNode(SimpleNode node, Context context) {
		CompileContext cc = (CompileContext) context;	
		cc.cf.add(Opcode.ACONST_NULL);
		return null;
	}

	public Object idNode(SimpleNode node, Context context) {
		CompileContext cc = (CompileContext) context;
		ClassFile cf = cc.cf;

		String symbol = node.str;
		int ctx = cc.getContextIndex();

		addLineInfo(cc, ctx, node);

		Reference ref = cc.getReference(symbol);

		if (ref != null) {
			getRef(ref, cc);
		} else {
			cf.loadLocal(ctx);
			cf.add(Opcode.LDC, cf.addConstant(symbol));
			cf.add(Opcode.INVOKEVIRTUAL, "pnuts.lang.Context", "getId",
					"(Ljava/lang/String;)", "Ljava/lang/Object;");
		}
		return null;
	}

	public Object global(SimpleNode node, Context context) {
		CompileContext cc = (CompileContext) context;
		ClassFile cf = cc.cf;
		int ctx = cc.getContextIndex();

		cf.add(Opcode.LDC, cf.addConstant(""));
		cf.loadLocal(ctx);
		cf.add(Opcode.INVOKESTATIC, "pnuts.lang.Package", "find",
				"(Ljava/lang/String;Lpnuts/lang/Context;)",
				"Lpnuts/lang/Package;");

		int gbl = cf.getLocal();
		cf.add(Opcode.DUP);
		cf.storeLocal(gbl);

		Label else_ = cf.getLabel();
		cf.add(Opcode.IFNULL, else_);

		cf.loadLocal(gbl);
		int symbol = cf.addConstant(node.str);
		cf.add(Opcode.LDC, symbol);
		cf.loadLocal(ctx);

		cf.add(Opcode.INVOKEVIRTUAL, "pnuts.lang.Package", "lookup",
				"(Ljava/lang/String;Lpnuts/lang/Context;)",
				"Lpnuts/lang/NamedValue;");

		int val = cf.getLocal();
		cf.add(Opcode.DUP);
		cf.storeLocal(val);

		cf.add(Opcode.IFNULL, else_);

		cf.loadLocal(val);
		cf.add(Opcode.INVOKEINTERFACE, "pnuts.lang.Value", "get", "()",
				"Ljava/lang/Object;");

		Label next = cf.getLabel();
		cf.add(Opcode.GOTO, next);
		else_.fix();
		errorSymbol(cf, "not.defined", symbol, cc);
		next.fix();

		cf.freeLocal(gbl);
		return null;
	}

	public Object listElements(SimpleNode node, Context context) {
		if ("{".equals(node.str)){
		    buildList(node, context);
		} else {
		    buildArray(node, context);
		}
		return null;
	}

	void buildList(SimpleNode node, Context context) {
		int n = node.jjtGetNumChildren();
		CompileContext cc = (CompileContext) context;
		ClassFile cf = cc.cf;
		int ctx = cc.getContextIndex();
		boolean hasTryStatement = (node.getAttribute("hasTryStatement") != null);
		if (hasTryStatement) {
			int[] vars = new int[n];
			for (int i = 0; i < n; i++) {
				int var = cf.getLocal();
				accept(node, i, context);
				cf.storeLocal(var);
				vars[i] = var;
			}
			cf.loadLocal(ctx);
			cf.add(Opcode.INVOKESTATIC, "pnuts.lang.Runtime", "createList",
			       "(Lpnuts/lang/Context;)",
			       "Ljava/util/List;");
			int ret = cf.getLocal();
			cf.storeLocal(ret);
			for (int i = 0; i < n; i++){
			    cf.loadLocal(ret);
			    accept(node, i, context);
			    cf.add(Opcode.INVOKEINTERFACE, "java.util.List", "add", "(Ljava/lang/Object;)", "Z");
			    cf.add(Opcode.POP);
			}
			for (int i = 0; i < n; i++) {
			    cf.freeLocal(vars[i]);
			}
			cf.loadLocal(ret);
		} else {
			cf.loadLocal(ctx);
			cf.add(Opcode.INVOKESTATIC, "pnuts.lang.Runtime", "createList",
			       "(Lpnuts/lang/Context;)",
			       "Ljava/util/List;");
			int ret = cf.getLocal();
			cf.storeLocal(ret);
			for (int i = 0; i < n; i++){
			    cf.loadLocal(ret);
			    accept(node, i, context);
			    cf.add(Opcode.INVOKEINTERFACE, "java.util.List", "add", "(Ljava/lang/Object;)", "Z");
			    cf.add(Opcode.POP);
			}
			cf.loadLocal(ret);
		}
	}

	void buildArray(SimpleNode node, Context context) {
		CompileContext cc = (CompileContext) context;
		ClassFile cf = cc.cf;
		int ctx = cc.getContextIndex();
		_listElements(node, context);
		cf.loadLocal(ctx);
		cf.add(Opcode.INVOKESTATIC, "pnuts.lang.Runtime", "makeArray",
			   "([Ljava/lang/Object;Lpnuts/lang/Context;)",
			   "Ljava/lang/Object;");
	}

	public Object _listElements(SimpleNode node, Context context) {
		CompileContext cc = (CompileContext) context;
		ClassFile cf = cc.cf;

		int n = node.jjtGetNumChildren();
		if (n == 0) {
			cf.add(Opcode.GETSTATIC, cc.constClassName, "NO_PARAM",
					"[Ljava/lang/Object;");
		} else {
			boolean hasTryStatement = (node.getAttribute("hasTryStatement") != null);
			if (hasTryStatement) {
				int[] vars = new int[n];
				for (int i = 0; i < n; i++) {
					int var = cf.getLocal();
					accept(node, i, context);
					cf.storeLocal(var);
					vars[i] = var;
				}
				cf.pushInteger(n);
				cf.add(Opcode.ANEWARRAY, "java.lang.Object");
				for (int i = 0; i < n; i++) {
					cf.add(Opcode.DUP);
					cf.pushInteger(i);
					cf.loadLocal(vars[i]);
					cf.add(Opcode.AASTORE);
				}
				for (int i = 0; i < n; i++) {
					cf.freeLocal(vars[i]);
				}
			} else {
				cf.pushInteger(n);
				cf.add(Opcode.ANEWARRAY, "java.lang.Object");
				for (int i = 0; i < n; i++) {
					cf.add(Opcode.DUP);
					cf.pushInteger(i);
					accept(node, i, context);
					cf.add(Opcode.AASTORE);
				}
			}
		}
		return null;
	}

	public Object mapNode(SimpleNode node, Context context) {
		CompileContext cc = (CompileContext) context;
		ClassFile cf = cc.cf;
		int n = node.jjtGetNumChildren();
		cf.pushInteger(n);
		cf.loadLocal(cc.getContextIndex());
		cf.add(Opcode.INVOKESTATIC, "pnuts.lang.Runtime", "createMap",
				"(ILpnuts/lang/Context;)", "Ljava/util/Map;");
		int map = cf.getLocal();
		cf.storeLocal(map);
		for (int i = 0; i < n; i++) {
			SimpleNode c = node.jjtGetChild(i);
			cf.loadLocal(map);
			accept(c, 0, context);
			accept(c, 1, context);
			cf.add(Opcode.INVOKEINTERFACE, "java.util.Map", "put",
					"(Ljava/lang/Object;Ljava/lang/Object;)",
					"Ljava/lang/Object;");
			cf.add(Opcode.POP);
		}
		cf.loadLocal(map);
		return null;
	}

	public Object castExpression(SimpleNode node, Context context) {
		CompileContext cc = (CompileContext) context;
		ClassFile cf = cc.cf;
		int ctx = cc.getContextIndex();

		int tgt = cf.getLocal();
		accept(node, 1, context);
		cf.storeLocal(tgt);

		cf.loadLocal(ctx);
		resolveType(node.jjtGetChild(0), cc, ctx);
		cf.loadLocal(tgt);
		cf.freeLocal(tgt);
		cf.add(Opcode.ICONST_1);
		cf.add(Opcode.INVOKESTATIC, "pnuts.lang.Runtime", "cast",
				"(Lpnuts/lang/Context;Ljava/lang/Class;Ljava/lang/Object;Z)",
				"Ljava/lang/Object;");
		return null;
	}

	public Object memberNode(SimpleNode node, Context context) {
		CompileContext cc = (CompileContext) context;
		ClassFile cf = cc.cf;
		int ctx = cc.getContextIndex();

		accept(node, 0, context);
		int tgt = cf.getLocal();
		cf.storeLocal(tgt);

		Label else_end = cf.getLabel();

		if ("length".equals(node.str)) {
			cf.loadLocal(tgt);
			cf.add(Opcode.INVOKESTATIC, "pnuts.lang.Runtime", "isArray",
					"(Ljava/lang/Object;)", "Z");
			Label else_ = cf.getLabel();
			cf.add(Opcode.IFEQ, else_);

			if (hasValueOfPrimitive){
			    cf.loadLocal(tgt);
			    cf.add(Opcode.INVOKESTATIC, "java.lang.reflect.Array", "getLength",
				   "(Ljava/lang/Object;)", "I");
			    cf.add(Opcode.INVOKESTATIC, "java.lang.Integer", "valueOf", "(I)", "Ljava/lang/Integer;");
			} else {
			    cf.add(Opcode.NEW, "java.lang.Integer");
			    cf.add(Opcode.DUP);
			    cf.loadLocal(tgt);
			    cf.add(Opcode.INVOKESTATIC, "java.lang.reflect.Array", "getLength",
				   "(Ljava/lang/Object;)", "I");
			    cf.add(Opcode.INVOKESPECIAL, "java.lang.Integer", "<init>", "(I)",
				   "V");
			}
			
			cf.add(Opcode.GOTO, else_end);
			else_.fix();
			cf.popStack();
		}

		if (node.info != null){ /* experimental BIND feature */
		    Object[] info = (Object[])node.info;
		    int targetIndex = ((int[])info[0])[0];
		    int tableIndex = ((int[])info[0])[1];
		    final SimpleNode c = (SimpleNode)info[1];
		    SimpleNode c0 = c.jjtGetChild(0);

		    cf.loadLocal(tableIndex);
		    cf.loadLocal(targetIndex);
		    cf.add(Opcode.LDC, cf.addConstant(c.str));
		    cf.loadLocal(tgt);
		    cf.add(Opcode.LDC, cf.addConstant(node.str));
		    toFunctionNode(c0).accept(this, context);
		    cf.add(Opcode.INVOKESTATIC,
			   "pnuts.lang.Runtime",
			   "watchProperty",
			   "(Ljava/util/Map;Ljava/lang/Object;Ljava/lang/String;Ljava/lang/Object;Ljava/lang/String;Lpnuts/lang/Callable;)",
			   "V");

		    if (c0 == node){
			cf.loadLocal(tableIndex);
			cf.loadLocal(tgt);
			cf.add(Opcode.LDC, cf.addConstant(node.str));
			cf.loadLocal(targetIndex);
			cf.add(Opcode.LDC, cf.addConstant(c.str));
			cf.add(Opcode.ACONST_NULL);
			cf.add(Opcode.INVOKESTATIC,
			       "pnuts.lang.Runtime",
			       "watchProperty",
			       "(Ljava/util/Map;Ljava/lang/Object;Ljava/lang/String;Ljava/lang/Object;Ljava/lang/String;Lpnuts/lang/Callable;)",
			       "V");			
		    }
		    node.info = null;
		}

		cf.loadLocal(ctx);
		cf.loadLocal(tgt);
		cf.add(Opcode.LDC, cf.addConstant(node.str));
		cf.add(Opcode.INVOKESTATIC, "pnuts.lang.Runtime", "getField",
				"(Lpnuts/lang/Context;Ljava/lang/Object;Ljava/lang/String;)",
				"Ljava/lang/Object;");
		else_end.fix();
		cf.freeLocal(tgt);
		return null;
	}

	public Object methodNode(SimpleNode node, Context context) {
		CompileContext cc = (CompileContext) context;
		ClassFile cf = cc.cf;
		int ctx = cc.getContextIndex();

		addLineInfo(cc, ctx, node);

		accept(node, 0, context);
		int tgt = cf.getLocal();
		cf.storeLocal(tgt);

		SimpleNode argNode = node.jjtGetChild(1);
		int nargs = argNode.jjtGetNumChildren();
		int arg = cf.getLocal();
		//	accept(node, 1, context);
		_listElements(argNode, context);
		cf.storeLocal(arg);

		boolean types_created = false;
		for (int i = 0; i < nargs; i++) {
			SimpleNode n = argNode.jjtGetChild(i);
			if (n.id == PnutsParserTreeConstants.JJTCASTEXPRESSION) {
				if (!types_created) {
					cf.pushInteger(nargs);
					cf.add(Opcode.ANEWARRAY, "java.lang.Class");
					types_created = true;
				}
				cf.add(Opcode.DUP);
				cf.pushInteger(i);
				resolveType(n.jjtGetChild(0), cc, ctx);
				cf.add(Opcode.AASTORE);
			}
		}
		int types = 0;
		if (types_created) {
			types = cf.getLocal();
			cf.storeLocal(types);
		}

		cf.loadLocal(ctx);
		cf.loadLocal(tgt);
		cf.add(Opcode.INVOKEVIRTUAL, "java.lang.Object", "getClass", "()",
				"Ljava/lang/Class;");
		cf.add(Opcode.LDC, cf.addConstant(node.str));
		cf.loadLocal(arg);
		cf.freeLocal(arg);

		if (types_created) {
			cf.loadLocal(types);
		} else {
			cf.add(Opcode.ACONST_NULL);
		}
		cf.loadLocal(tgt);
		cf.freeLocal(tgt);

		cf.add(Opcode.INVOKESTATIC,
		       "pnuts.lang.Runtime",
		       "callMethod",
		       "(Lpnuts/lang/Context;Ljava/lang/Class;Ljava/lang/String;[Ljava/lang/Object;[Ljava/lang/Class;Ljava/lang/Object;)",
		       "Ljava/lang/Object;");
		return null;
	}

	public Object className(SimpleNode node, Context context) {
		CompileContext cc = (CompileContext) context;
		int ctx = cc.getContextIndex();
		resolveClassName(node, cc, ctx);
		return null;
	}

	/*
	 * ArrayType .. ArrayType IndexNode IdNode IntegerNode
	 */
	public Object arrayType(SimpleNode node, Context context) {
		CompileContext cc = (CompileContext) context;
		ClassFile cf = cc.cf;

		SimpleNode n = node;
		int count = 0;
		while (n != null && n.id == PnutsParserTreeConstants.JJTARRAYTYPE) {
			count++;
			n = n.jjtGetChild(0);
		}
		if (n != null && n.id == PnutsParserTreeConstants.JJTINDEXNODE) {
			Label clserr = cf.getLabel();
			Label numerr = cf.getLabel();

			Object[] idx = parseIndex(n);
			SimpleNode idx0 = (SimpleNode) idx[0];
			idx0.accept(this, context);
			cf.add(Opcode.DUP);
			cf.add(Opcode.INSTANCEOF, "java.lang.Class");

			cf.add(Opcode.IFEQ, clserr);
			cf.add(Opcode.CHECKCAST, "java.lang.Class");

			cf.pushInteger(count);
			cf.add(Opcode.INVOKESTATIC, "pnuts.lang.Runtime", "arrayType",
					"(Ljava/lang/Class;I)", "Ljava/lang/Class;");

			Object[] dim = (Object[]) idx[1];

			cf.pushInteger(dim.length);
			cf.add(Opcode.NEWARRAY, Opcode.T_INT);
			int dlen = dim.length;
			for (int i = 0; i < dlen; i++) {
				cf.add(Opcode.DUP);
				cf.pushInteger(i);
				((SimpleNode) dim[i]).accept(this, context);
				/*
				 * cf.add(Opcode.DUP); cf.add(Opcode.INSTANCEOF,
				 * "java.lang.Number"); cf.add(Opcode.IFEQ, numerr);
				 */
				cf.add(Opcode.CHECKCAST, "java.lang.Number");
				cf.add(Opcode.INVOKEVIRTUAL, "java.lang.Number", "intValue",
						"()", "I");
				cf.add(Opcode.IASTORE);
			}

			cf.add(Opcode.INVOKESTATIC, "java.lang.reflect.Array",
					"newInstance", "(Ljava/lang/Class;[I)",
					"Ljava/lang/Object;");
			Label next = cf.getLabel();
			cf.add(Opcode.GOTO, next);
			numerr.fix();
			error(cf, "number.expected", cc);
			cf.add(Opcode.GOTO, next);
			clserr.fix();
			error(cf, "classOrArray.expected", cc);
			next.fix();
			return null;
		} else if (node.jjtGetParent().id != PnutsParserTreeConstants.JJTNEW) {
			n.accept(this, context);
			int type = cf.getLocal();
			cf.add(Opcode.DUP);
			cf.storeLocal(type);

			cf.add(Opcode.INSTANCEOF, "java.lang.Class");
			Label err = cf.getLabel();
			cf.add(Opcode.IFEQ, err);
			cf.loadLocal(type);
			cf.freeLocal(type);
			cf.add(Opcode.CHECKCAST, "java.lang.Class");
			cf.pushInteger(count);
			cf.add(Opcode.INVOKESTATIC, "pnuts.lang.Runtime", "arrayType",
					"(Ljava/lang/Class;I)", "Ljava/lang/Class;");
			Label next = cf.getLabel();
			cf.add(Opcode.GOTO, next);
			err.fix();
			error(cf, "classOrArray.expected", new int[] { type }, cc);
			next.fix();
		} else {
			cf.add(Opcode.NEW, "java.lang.IllegalArgumentException");
			cf.add(Opcode.DUP);
			cf.add(Opcode.INVOKESPECIAL, "java.lang.IllegalArgumentException",
					"<init>", "()", "V");
			cf.add(Opcode.ATHROW);
		}
		return null;
	}

	/**
	 * @return [base_component_node, [idx_node_0, idx_node_1, ...]]
	 */
	static Object[] parseIndex(SimpleNode node) {
		SimpleNode c0 = node.jjtGetChild(0);
		SimpleNode c1 = node.jjtGetChild(1);
		if (c0.id != PnutsParserTreeConstants.JJTINDEXNODE) {
			return new Object[] { c0, new Object[] { c1 } };
		} else {
			Object[] r = parseIndex(c0);
			Object[] d = (Object[]) r[1];
			Object[] a = new Object[d.length + 1];
			System.arraycopy(d, 0, a, 0, d.length);
			a[d.length] = c1;
			return new Object[] { r[0], a };
		}
	}

	static void convertIndexNode(SimpleNode node) {
		SimpleNode n0 = node.jjtGetChild(0);
		SimpleNode n1 = node.jjtGetChild(1);
		if (ConstraintsTransformer.isPredicate(n1)) {
			SimpleNode n = ConstraintsTransformer.buildFunc(n1);
			node.jjtAddChild(n, 1);
			n.jjtSetParent(node);
		}
		if (n0.id == PnutsParserTreeConstants.JJTINDEXNODE) {
			convertIndexNode(n0);
		}
	}

	public Object indexNode(SimpleNode node, Context context) {
		FrameInfo info = (FrameInfo)node.getAttribute("frameInfo");
		if (info == null){
			node.setAttribute("frameInfo", info = new FrameInfo());
		}

		if (!info.preprocessed) {
			info.preprocessed = true;
			convertIndexNode(node);
		}

		CompileContext cc = (CompileContext) context;
		ClassFile cf = cc.cf;
		Object[] idx = parseIndex(node);
		SimpleNode c = (SimpleNode) idx[0];
		c.accept(this, context);
		int tgt = cf.getLocal();
		cf.storeLocal(tgt);

		Object[] dim = (Object[]) idx[1];

		Label else_end = cf.getLabel();
		int ctx = cc.getContextIndex();

		if (c.id == PnutsParserTreeConstants.JJTCLASS
				|| c.id == PnutsParserTreeConstants.JJTIDNODE) {
			cf.loadLocal(tgt);
			cf.add(Opcode.INSTANCEOF, "java.lang.Class");
			Label else_ = cf.getLabel();
			cf.add(Opcode.IFEQ, else_);

			if (node.getAttribute("hasTryStatement") != null){
				int[] vars = new int[dim.length];
				for (int i = 0; i < dim.length; i++) {
					int var = cf.getLocal();
					((SimpleNode) dim[i]).accept(this, context);
					cf.storeLocal(var);
					vars[i] = var;
				}
				cf.loadLocal(tgt);
				cf.add(Opcode.CHECKCAST, "java.lang.Class");
				cf.pushInteger(dim.length);
				cf.add(Opcode.NEWARRAY, Opcode.T_INT);
				for (int i = 0; i < dim.length; i++) {
					cf.add(Opcode.DUP);
					cf.pushInteger(i);
					cf.loadLocal(vars[i]);
					cf.add(Opcode.CHECKCAST, "java.lang.Number");
					cf.add(Opcode.INVOKEVIRTUAL, "java.lang.Number",
							"intValue", "()", "I");
					cf.add(Opcode.IASTORE);
				}
				for (int i = 0; i < dim.length; i++) {
					cf.freeLocal(vars[i]);
				}

			} else {
				cf.loadLocal(tgt);
				cf.add(Opcode.CHECKCAST, "java.lang.Class");
				cf.pushInteger(dim.length);
				cf.add(Opcode.NEWARRAY, Opcode.T_INT);
				for (int i = 0; i < dim.length; i++) {
					cf.add(Opcode.DUP);
					cf.pushInteger(i);
					((SimpleNode) dim[i]).accept(this, context);

					cf.add(Opcode.CHECKCAST, "java.lang.Number");
					cf.add(Opcode.INVOKEVIRTUAL, "java.lang.Number",
							"intValue", "()", "I");
					cf.add(Opcode.IASTORE);
				}
			}
			cf.add(Opcode.INVOKESTATIC, "java.lang.reflect.Array",
					"newInstance", "(Ljava/lang/Class;[I)",
					"Ljava/lang/Object;");
			cf.add(Opcode.GOTO, else_end);
			else_.fix();
			cf.popStack();
		}

		for (int i = 0; i < dim.length; i++) {
			((SimpleNode) dim[i]).accept(this, context);
			int _idx = cf.getLocal();
			cf.storeLocal(_idx);

			cf.loadLocal(tgt);
			cf.loadLocal(_idx);
			cf.loadLocal(ctx);
			cf.add(Opcode.INVOKESTATIC,
			       "pnuts.lang.Runtime",
			       "getElement",
			       "(Ljava/lang/Object;Ljava/lang/Object;Lpnuts/lang/Context;)",
			       "Ljava/lang/Object;");
			cf.storeLocal(tgt);
		}
		cf.loadLocal(tgt);

		else_end.fix();
		cf.freeLocal(tgt);
		return null;
	}

	public Object instanceofExpression(SimpleNode node, Context context) {
		CompileContext cc = (CompileContext) context;
		ClassFile cf = cc.cf;
		int ctx = cc.getContextIndex();

		Label next = cf.getLabel();
		Label l_false = cf.getLabel();

		resolveType(node.jjtGetChild(1), cc, ctx);
		accept(node, 0, context);

		cf.add(Opcode.INVOKEVIRTUAL, "java.lang.Class", "isInstance",
				"(Ljava/lang/Object;)", "Z");
		cf.add(Opcode.IFEQ, l_false);
		cf.add(Opcode.GETSTATIC, "java.lang.Boolean", "TRUE",
				"Ljava/lang/Boolean;");
		cf.add(Opcode.GOTO, next);
		l_false.fix();
		cf.add(Opcode.GETSTATIC, "java.lang.Boolean", "FALSE",
				"Ljava/lang/Boolean;");
		next.fix();
		return null;
	}

	public Object assignment(SimpleNode node, Context context) {
	    SimpleNode lhs = node.jjtGetChild(0);
	    if (lhs.id == PnutsParserTreeConstants.JJTMULTIASSIGNLHS){
		SimpleNode rhs = node.jjtGetChild(1);
		if (rhs.id == PnutsParserTreeConstants.JJTLISTELEMENTS){
		    return multiAssignFast(lhs, rhs, context);
		} else {
		    return multiAssign(lhs, rhs, context);
		}
	    } else {
		return assign(node, context, PnutsParserTreeConstants.JJTASSIGNMENT);
	    }
	}

	Object multiAssignFast(SimpleNode lhs, SimpleNode rhs, Context context) {
		CompileContext cc = (CompileContext) context;
		int ctx = cc.getContextIndex();
		ClassFile cf = cc.cf;
		int n = lhs.jjtGetNumChildren();
		int[] r_array = new int[n];
		for (int i = 0; i < n; i++){
		    SimpleNode _rhs = rhs.jjtGetChild(i);
		    int r = r_array[i] = cf.getLocal();
		    _rhs.accept(this, context);
		    cf.storeLocal(r);
		}
		for (int i = 0; i < n; i++){
		    SimpleNode _lhs = lhs.jjtGetChild(i);
		    assignId(cf, PnutsParserTreeConstants.JJTASSIGNMENT, ctx, r_array[i], _lhs, cc, false, false);
		}
		cf.add(Opcode.ACONST_NULL);
		for (int i = 0; i < n; i++){
		    cf.freeLocal(r_array[i]);
		}
		return null;
	}

	Object multiAssign(SimpleNode lhs, SimpleNode rhs, Context context) {
		CompileContext cc = (CompileContext) context;
		int ctx = cc.getContextIndex();
		ClassFile cf = cc.cf;
		int n = lhs.jjtGetNumChildren();
		int expr = cf.getLocal();
		int _rhs = cf.getLocal();
		rhs.accept(this, context);
		cf.storeLocal(expr);
		for (int i = 0; i < n; i++){
		    cf.loadLocal(expr);
		    cf.pushInteger(i);
		    cf.loadLocal(ctx);
		    cf.add(Opcode.INVOKESTATIC,
			   "pnuts.lang.Runtime",
			   "getElementAt",
			   "(Ljava/lang/Object;ILpnuts/lang/Context;)",
			   "Ljava/lang/Object;");
		    cf.storeLocal(_rhs);
		    SimpleNode _lhs = lhs.jjtGetChild(i);
		    assignId(cf, PnutsParserTreeConstants.JJTASSIGNMENT, ctx, _rhs, _lhs, cc, false, true);
		    cf.add(Opcode.POP);
		}
		cf.loadLocal(expr);
		return null;
	}

	public Object assignmentTA(SimpleNode node, Context context) {
		return assign(node, context, PnutsParserTreeConstants.JJTASSIGNMENTTA);
	}

	public Object assignmentMA(SimpleNode node, Context context) {
		return assign(node, context, PnutsParserTreeConstants.JJTASSIGNMENTMA);
	}

	public Object assignmentDA(SimpleNode node, Context context) {
		return assign(node, context, PnutsParserTreeConstants.JJTASSIGNMENTDA);
	}

	public Object assignmentPA(SimpleNode node, Context context) {
		return assign(node, context, PnutsParserTreeConstants.JJTASSIGNMENTPA);
	}

	public Object assignmentSA(SimpleNode node, Context context) {
		return assign(node, context, PnutsParserTreeConstants.JJTASSIGNMENTSA);
	}

	public Object assignmentLA(SimpleNode node, Context context) {
		return assign(node, context, PnutsParserTreeConstants.JJTASSIGNMENTLA);
	}

	public Object assignmentRA(SimpleNode node, Context context) {
		return assign(node, context, PnutsParserTreeConstants.JJTASSIGNMENTRA);
	}

	public Object assignmentRAA(SimpleNode node, Context context) {
		return assign(node, context, PnutsParserTreeConstants.JJTASSIGNMENTRAA);
	}

	public Object assignmentAA(SimpleNode node, Context context) {
		return assign(node, context, PnutsParserTreeConstants.JJTASSIGNMENTAA);
	}

	public Object assignmentEA(SimpleNode node, Context context) {
		return assign(node, context, PnutsParserTreeConstants.JJTASSIGNMENTEA);
	}

	public Object assignmentOA(SimpleNode node, Context context) {
		return assign(node, context, PnutsParserTreeConstants.JJTASSIGNMENTOA);
	}

	public Object preIncrNode(SimpleNode node, Context context) {
		SimpleNode node0 = node.jjtGetChild(0);
		node0.accept(this, context);
		_assign(node0, context,
				PnutsParserTreeConstants.JJTASSIGNMENTPA | 0x8000, false, true);
		return null;
	}

	public Object preDecrNode(SimpleNode node, Context context) {
		SimpleNode node0 = node.jjtGetChild(0);
		node0.accept(this, context);
		_assign(node0, context,
				PnutsParserTreeConstants.JJTASSIGNMENTSA | 0x8000, false, true);
		return null;
	}

	public Object postIncrNode(SimpleNode node, Context context) {
		CompileContext cc = (CompileContext) context;
		ClassFile cf = cc.cf;
		accept(node, 0, context);
		int ret = cf.getLocal();
		cf.storeLocal(ret);
		cf.loadLocal(ret);

		_assign(node.jjtGetChild(0), cc,
				PnutsParserTreeConstants.JJTASSIGNMENTPA | 0x8000, false, false);
		cf.loadLocal(ret);
		cf.freeLocal(ret);
		return null;
	}

	public Object postDecrNode(SimpleNode node, Context context) {
		CompileContext cc = (CompileContext) context;
		ClassFile cf = cc.cf;

		accept(node, 0, context);
		int ret = cf.getLocal();
		cf.storeLocal(ret);
		cf.loadLocal(ret);

		_assign(node.jjtGetChild(0), context,
				PnutsParserTreeConstants.JJTASSIGNMENTSA | 0x8000, false, false);

		cf.loadLocal(ret);
		cf.freeLocal(ret);
		return null;
	}

	public Object staticMethodNode(SimpleNode node, Context context) {
		CompileContext cc = (CompileContext) context;
		ClassFile cf = cc.cf;
		int ctx = cc.getContextIndex();

		addLineInfo(cc, ctx, node);

		SimpleNode argNode = node.jjtGetChild(1);
		int nargs = argNode.jjtGetNumChildren();

		//	accept(node, 1, context);
		_listElements(argNode, context);
		int args = cf.getLocal();
		cf.storeLocal(args);

		SimpleNode c1 = node.jjtGetChild(0);
		String pkgName = getPackageName(c1);

		cf.add(Opcode.LDC, cf.addConstant(pkgName));
		cf.loadLocal(ctx);

		cf.add(Opcode.INVOKESTATIC, "pnuts.lang.Package", "find",
				"(Ljava/lang/String;Lpnuts/lang/Context;)",
				"Lpnuts/lang/Package;");
		int pkg = cf.getLocal();
		cf.add(Opcode.DUP);
		cf.storeLocal(pkg);

		Label l_null = cf.getLabel();
		cf.add(Opcode.IFNULL, l_null);
		cf.loadLocal(pkg);
		cf.add(Opcode.LDC, cf.addConstant(node.str));
		cf.loadLocal(ctx);
		cf.add(Opcode.INVOKEVIRTUAL, "pnuts.lang.Package", "get",
				"(Ljava/lang/String;Lpnuts/lang/Context;)",
				"Ljava/lang/Object;");
		int got = cf.getLocal();
		cf.add(Opcode.DUP);
		cf.storeLocal(got);

		cf.add(Opcode.INSTANCEOF, "pnuts.lang.PnutsFunction");
		Label l_else = cf.getLabel();
		cf.add(Opcode.IFEQ, l_else);
		cf.loadLocal(ctx);
		cf.loadLocal(got);
		cf.add(Opcode.CHECKCAST, "pnuts.lang.PnutsFunction");
		cf.loadLocal(args);
		cf.add(Opcode.INVOKESTATIC,
		       "pnuts.lang.Runtime",
		       "callFunction",
		       "(Lpnuts/lang/Context;Lpnuts/lang/PnutsFunction;[Ljava/lang/Object;)",
		       "Ljava/lang/Object;");
		Label next = cf.getLabel();
		cf.add(Opcode.GOTO, next);
		l_else.fix();
		cf.popStack();
		cf.loadLocal(got);
		cf.add(Opcode.INSTANCEOF, "java.lang.Class");
		cf.add(Opcode.IFEQ, l_null);

		boolean types_created = false;
		for (int i = 0; i < nargs; i++) {
			SimpleNode n = argNode.jjtGetChild(i);
			if (n.id == PnutsParserTreeConstants.JJTCASTEXPRESSION) {
				if (!types_created) {
					cf.pushInteger(nargs);
					cf.add(Opcode.ANEWARRAY, "java.lang.Class");
					types_created = true;
				}
				cf.add(Opcode.DUP);
				cf.pushInteger(i);
				resolveType(n.jjtGetChild(0), cc, ctx);
				cf.add(Opcode.AASTORE);
			}
		}
		int types = 0;
		if (types_created) {
			types = cf.getLocal();
			cf.storeLocal(types);
		}

		cf.loadLocal(ctx);
		cf.loadLocal(got);
		cf.add(Opcode.CHECKCAST, "java.lang.Class");
		cf.loadLocal(args);
		if (types_created) {
			cf.loadLocal(types);
		} else {
			cf.add(Opcode.ACONST_NULL);
		}
		cf.add(Opcode.INVOKESTATIC,
		       "pnuts.lang.Runtime",
		       "newInstance",
		       "(Lpnuts/lang/Context;Ljava/lang/Class;[Ljava/lang/Object;[Ljava/lang/Class;)",
		       "Ljava/lang/Object;");

		cf.add(Opcode.GOTO, next);

		l_null.fix();
		cf.popStack();
		accept(node, 0, context);
		int tgt = cf.getLocal();
		cf.add(Opcode.DUP);
		cf.storeLocal(tgt);

		cf.add(Opcode.INSTANCEOF, "java.lang.Class");
		Label ok = cf.getLabel();
		cf.add(Opcode.IFNE, ok);
		cf.add(Opcode.NEW, "pnuts.lang.PnutsException");
		cf.add(Opcode.DUP);
		cf.add(Opcode.LDC, cf.addConstant("illegal.staticCall"));
		cf.add(Opcode.ICONST_3);
		cf.add(Opcode.ANEWARRAY, "java.lang.Object");
		cf.add(Opcode.DUP);
		cf.add(Opcode.ICONST_0);
		cf.add(Opcode.LDC, cf.addConstant(pkgName));
		cf.add(Opcode.AASTORE);
		cf.add(Opcode.DUP);
		cf.add(Opcode.ICONST_1);
		cf.add(Opcode.LDC, cf.addConstant(node.str));
		cf.add(Opcode.AASTORE);
		cf.add(Opcode.DUP);
		cf.add(Opcode.ICONST_2);


		if (hasValueOfPrimitive){
		    cf.pushInteger(nargs);
		    cf.add(Opcode.INVOKESTATIC, "java.lang.Integer", "valueOf", "(I)", "Ljava/lang/Integer;");
		} else {
		    cf.add(Opcode.NEW, "java.lang.Integer");
		    cf.add(Opcode.DUP);
		    cf.pushInteger(nargs);
		    cf.add(Opcode.INVOKESPECIAL, "java.lang.Integer", "<init>", "(I)", "V");
		}

		cf.add(Opcode.AASTORE);
		cf.loadLocal(ctx);
		cf.add(Opcode.INVOKESPECIAL, "pnuts.lang.PnutsException", "<init>",
				"(Ljava/lang/String;[Ljava/lang/Object;Lpnuts/lang/Context;)",
				"V");
		cf.add(Opcode.ATHROW);
		ok.fix();

		types_created = false;
		for (int i = 0; i < nargs; i++) {
			SimpleNode n = argNode.jjtGetChild(i);
			if (n.id == PnutsParserTreeConstants.JJTCASTEXPRESSION) {
				if (!types_created) {
					cf.pushInteger(nargs);
					cf.add(Opcode.ANEWARRAY, "java.lang.Class");
					types_created = true;
				}
				cf.add(Opcode.DUP);
				cf.pushInteger(i);
				resolveType(n.jjtGetChild(0), cc, ctx);
				cf.add(Opcode.AASTORE);
			}
		}
		types = 0;
		if (types_created) {
			types = cf.getLocal();
			cf.storeLocal(types);
		}
		cf.loadLocal(ctx);
		cf.loadLocal(tgt);
		cf.add(Opcode.CHECKCAST, "java.lang.Class");
		cf.add(Opcode.LDC, cf.addConstant(node.str));
		cf.loadLocal(args);
		if (types_created) {
			cf.loadLocal(types);
			cf.freeLocal(types);
		} else {
			cf.add(Opcode.ACONST_NULL);
		}
		cf.add(Opcode.ACONST_NULL);

		cf.add(Opcode.INVOKESTATIC,
		       "pnuts.lang.Runtime",
		       "callMethod",
		       "(Lpnuts/lang/Context;Ljava/lang/Class;Ljava/lang/String;[Ljava/lang/Object;[Ljava/lang/Class;Ljava/lang/Object;)",
		       "Ljava/lang/Object;");
		next.fix();
		return null;
	}

	public Object staticMemberNode(SimpleNode node, Context context) {
		CompileContext cc = (CompileContext) context;
		ClassFile cf = cc.cf;
		int ctx = cc.getContextIndex();

		addLineInfo(cc, ctx, node);

		SimpleNode c1 = node.jjtGetChild(0);
		Label next = cf.getLabel();

		String pkgName = getPackageName(c1);

		cf.add(Opcode.LDC, cf.addConstant(pkgName));
		cf.loadLocal(ctx);
		cf.add(Opcode.INVOKESTATIC, "pnuts.lang.Package", "find",
				"(Ljava/lang/String;Lpnuts/lang/Context;)",
				"Lpnuts/lang/Package;");
		int pkg = cf.getLocal();
		cf.add(Opcode.DUP);
		cf.storeLocal(pkg);

		Label l_null = cf.getLabel();
		cf.add(Opcode.IFNULL, l_null);

		cf.loadLocal(pkg);
		cf.freeLocal(pkg);
		cf.add(Opcode.LDC, cf.addConstant(node.str));
		cf.loadLocal(ctx);
		cf.add(Opcode.INVOKEVIRTUAL, "pnuts.lang.Package", "lookup",
				"(Ljava/lang/String;Lpnuts/lang/Context;)",
				"Lpnuts/lang/NamedValue;");

		int val = cf.getLocal();
		cf.add(Opcode.DUP);
		cf.storeLocal(val);

		Label undef = cf.getLabel();
		cf.add(Opcode.IFNULL, undef);

		cf.loadLocal(val);
		cf.freeLocal(val);

		cf.add(Opcode.INVOKEINTERFACE, "pnuts.lang.Value", "get", "()",
				"Ljava/lang/Object;");
		cf.add(Opcode.GOTO, next);

		undef.fix();
		errorSymbol(cf, "not.defined", cf.addConstant(node.str), cc);
		l_null.fix();

		c1.accept(this, context);
		int tgt = cf.getLocal();
		cf.add(Opcode.DUP);
		cf.storeLocal(tgt);

		cf.add(Opcode.INSTANCEOF, "java.lang.Class");
		Label err = cf.getLabel();
		cf.add(Opcode.IFEQ, err);

		cf.loadLocal(ctx);
		cf.loadLocal(tgt);
		cf.freeLocal(tgt);
		cf.add(Opcode.CHECKCAST, "java.lang.Class");
		cf.add(Opcode.LDC, cf.addConstant(node.str));
		cf.add(Opcode.INVOKESTATIC, "pnuts.lang.Runtime", "getStaticField",
				"(Lpnuts/lang/Context;Ljava/lang/Class;Ljava/lang/String;)",
				"Ljava/lang/Object;");
		cf.add(Opcode.GOTO, next);

		err.fix();
		error(cf, "packageOrClass.expected", new int[] { tgt }, cc);
		next.fix();
		return null;
	}

	public Object rangeNode(SimpleNode node, Context context) {
		CompileContext cc = (CompileContext) context;
		ClassFile cf = cc.cf;
		int ctx = cc.getContextIndex();

		accept(node, 0, context);
		int tgt = cf.getLocal();
		cf.storeLocal(tgt);

		accept(node, 1, context);
		int idx1 = cf.getLocal();
		cf.storeLocal(idx1);

		int idx2 = cf.getLocal();
		if (node.jjtGetNumChildren() >= 3) {
			accept(node, 2, context);
		} else {
			cf.add(Opcode.ACONST_NULL);
		}
		cf.storeLocal(idx2);

		cf.loadLocal(tgt);
		cf.freeLocal(tgt);
		cf.loadLocal(idx1);
		cf.freeLocal(idx1);
		cf.loadLocal(idx2);
		cf.freeLocal(idx2);
		cf.loadLocal(ctx);
		cf.add(Opcode.INVOKESTATIC,
		       "pnuts.lang.Runtime",
		       "getRange",
		       "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Lpnuts/lang/Context;)",
		       "Ljava/lang/Object;");
		return null;
	}

	public Object forStatement(SimpleNode node, Context context) {
		CompileContext cc = (CompileContext) context;
		ClassFile cf = cc.cf;
		int ctx = cc.getContextIndex();

		addLineInfo(cc, ctx, node);

		SimpleNode initNode = null;
		SimpleNode condNode = null;
		SimpleNode updateNode = null;
		SimpleNode blockNode = null;

		int j = 0;
		SimpleNode n = node.jjtGetChild(j);

		if (n.id == PnutsParserTreeConstants.JJTFORENUM) {
	    		SimpleNode n0 = n.jjtGetChild(0);
			if (n0.id == PnutsParserTreeConstants.JJTMULTIASSIGNLHS){
			    int num = n0.jjtGetNumChildren();
			    String[] vars = new String[num];
			    for (int i = 0; i < num; i++){
				vars[i] = n0.jjtGetChild(i).str;
			    }
			    blockNode = node.jjtGetChild(1);
			    doForeach(vars, n.jjtGetChild(1), blockNode, cc, cf);
			    return null;
			}
			int nc = n.jjtGetNumChildren();
			blockNode = node.jjtGetChild(1);
			if (nc == 1) { // for (i : value)
				doForeach(new String[]{n.str}, n.jjtGetChild(0), blockNode, cc, cf);
			} else if (nc == 2) { // for (i : start .. end)
				doForeachRange(n.str, n.jjtGetChild(0), n.jjtGetChild(1),
						blockNode, cc, cf);
			} else {
			}
			return null;
		}
		/*
		 * for(A;B;C) ...
		 */
		if (n.id == PnutsParserTreeConstants.JJTFORINIT) {
			initNode = n;
			j++;
		}
		n = node.jjtGetChild(j);
		if (n.id != PnutsParserTreeConstants.JJTFORUPDATE
				&& n.id != PnutsParserTreeConstants.JJTBLOCK) {
			condNode = n;
			j++;
		}
		n = node.jjtGetChild(j);
		if (n.id == PnutsParserTreeConstants.JJTFORUPDATE) {
			updateNode = n;
			j++;
		}

		blockNode = node.jjtGetChild(j);

		int last = cf.getLocal();
		cf.add(Opcode.ACONST_NULL);
		cf.storeLocal(last);

		if (initNode != null) {
			int num = initNode.jjtGetNumChildren();
			String[] env = new String[num];
			for (int i = 0; i < env.length; i++) {
				SimpleNode sn = initNode.jjtGetChild(i);
				env[i] = sn.str;
			}
			cc.openScope(env);
			for (int i = 0; i < env.length; i++) {
				/**/
				Reference ref = cc.getReference(env[i]);
				if (DEBUG) {
					System.out.println("ref = " + ref);
				}
				SimpleNode sn = initNode.jjtGetChild(i);
				accept(sn, 0, context);
				if (ref.offset < 0) {
					cf.storeLocal(ref.index);
				} else {
					int tgt = cf.getLocal();
					cf.storeLocal(tgt);
					cf.add(Opcode.ACONST_NULL);
					cf.storeLocal(ref.index);
					ref.set(cf, tgt);
				}
				/**/

			}
		} else {
			cc.openScope(new String[] {});
		}

		Label start = cf.getLabel(true);
		Label cont = cf.getLabel();
		Label next = cf.getLabel();
		Label brk = cf.getLabel();

		ControlEnv ctrl = cc.openControlEnv(node.id);
		ctrl.continueLabel = cont;
		ctrl.breakLabel = brk;

		if (condNode != null) {
			condNode.accept(this, context);
			if (condNode.getAttribute("inlinedBoolean") != null){
				cf.add(Opcode.IFEQ, next);
			} else {
				booleanCheck(condNode.id, cf, context);
				cf.add(Opcode.CHECKCAST, "java.lang.Boolean");
				cf.add(Opcode.INVOKEVIRTUAL, "java.lang.Boolean",
						"booleanValue", "()", "Z");
				cf.add(Opcode.IFEQ, next);
			}
		}

		blockNode.accept(this, context);
		cont.fix();
		cf.storeLocal(last);

		if (updateNode != null) {
			int num = updateNode.jjtGetNumChildren();
			for (int i = 0; i < num; i++) {
				accept(updateNode, i, context);
				cf.add(Opcode.POP);
			}
		}

		cf.add(Opcode.GOTO, start);
		next.fix();
		cf.loadLocal(last);
		cf.freeLocal(last);
		cc.closeScope();
		brk.fix();

		cc.closeControlEnv();

		return null;
	}

	public Object breakNode(SimpleNode node, Context context) {
		CompileContext cc = (CompileContext) context;
		ClassFile cf = cc.cf;

		cc.leaveControlEnv();

		if (node.jjtGetNumChildren() > 0) {
			accept(node, 0, context);
		} else {
			cf.add(Opcode.ACONST_NULL);
		}

		Label brk = cc.getBreakLabel();
		if (brk != null) {
			cf.add(Opcode.GOTO, brk);
		} else {
			if (cc.inGeneratorBlock) {
				int value = cf.getLocal();
				cf.storeLocal(value);
				cf.add(Opcode.NEW, "pnuts.lang.Generator$Break");
				cf.add(Opcode.DUP);
				cf.loadLocal(value);
				cf.freeLocal(value);
				cf.add(Opcode.INVOKESPECIAL, "pnuts.lang.Generator$Break",
						"<init>", "(Ljava/lang/Object;)", "V");
				cf.add(Opcode.ATHROW);
			}
		}
		return null;
	}

	public Object continueNode(SimpleNode node, Context context) {
		CompileContext cc = (CompileContext) context;
		ClassFile cf = cc.cf;

		cc.leaveControlEnv();

		Label cnt = cc.getContinueLabel();
		cf.add(Opcode.ACONST_NULL);
		if (cnt != null) {
			cf.add(Opcode.GOTO, cnt);
		} else {
			if (cc.inGeneratorBlock) {
				cf.add(Opcode.ARETURN);
			}
		}
		return null;
	}

	public Object returnNode(SimpleNode node, Context context) {
		CompileContext cc = (CompileContext) context;
		ClassFile cf = cc.cf;

		cc.leaveFrame();

		if (node.jjtGetNumChildren() > 0) {
			accept(node, 0, context);
		} else {
			cf.add(Opcode.ACONST_NULL);
		}

		if (cc.env.parent != null) {
			if (cc.inGeneratorBlock) {
				int value = cf.getLocal();
				cf.storeLocal(value);
				cf.add(Opcode.NEW, "pnuts.lang.Jump");
				cf.add(Opcode.DUP);
				cf.loadLocal(value);
				cf.add(Opcode.INVOKESPECIAL, "pnuts.lang.Jump", "<init>",
						"(Ljava/lang/Object;)", "V");
				cf.add(Opcode.ATHROW);
			} else {
				cf.add(Opcode.GOTO, cc.returnLabel);
			}
		} else {
			cf.add(Opcode.INVOKESTATIC, "pnuts.lang.Runtime", "jump",
					"(Ljava/lang/Object;)", "V");
			cf.add(Opcode.ACONST_NULL);
		}
		return null;
	}

	public Object yieldNode(SimpleNode node, Context context) {
		throw new PnutsException("yield must be used in a generator function",
				context);
	}

	public Object catchNode(SimpleNode node, Context context) {
		CompileContext cc = (CompileContext) context;
		ClassFile cf = cc.cf;
		int ctx = cc.getContextIndex();

		if (node.jjtGetNumChildren() == 0) {
			cf.add(Opcode.GETSTATIC, "pnuts.lang.PnutsFunction", "CATCH",
			       "Lpnuts/lang/PnutsFunction;");
			return null;
		}

		int cls = cf.getLocal();
		int func = cf.getLocal();

		accept(node, 0, context);
		cf.add(Opcode.CHECKCAST, "java.lang.Class");
		cf.storeLocal(cls);

		accept(node, 1, context);
		cf.add(Opcode.CHECKCAST, "pnuts.lang.PnutsFunction");
		cf.storeLocal(func);

		Reference eref = cc.getReference(EXCEPTOIN_FIELD_SYMBOL);
		if (cc.env.parent != null && eref != null) {
			int tmap = cf.getLocal();
			cf.add(Opcode.NEW, "pnuts.lang.Runtime$TypeMap");
			cf.add(Opcode.DUP);
			cf.loadLocal(cls);
			cf.loadLocal(func);

			getRef(eref, cc);

			cf.add(Opcode.CHECKCAST, "pnuts.lang.Runtime$TypeMap");

			cf.add(Opcode.INVOKESPECIAL,
			       "pnuts.lang.Runtime$TypeMap",
			       "<init>",
			       "(Ljava/lang/Class;Ljava/lang/Object;Lpnuts/lang/Runtime$TypeMap;)",
			       "V");
			 cf.storeLocal(tmap);
				 eref.set(cf, tmap); 
			cf.add(Opcode.ACONST_NULL);

		} else {
			cf.loadLocal(cls);
			cf.loadLocal(func);
			cf.loadLocal(ctx);
			cf.add(Opcode.INVOKESTATIC,
			       "pnuts.lang.Runtime",
			       "catchException",
			       "(Ljava/lang/Class;Lpnuts/lang/PnutsFunction;Lpnuts/lang/Context;)",
			       "V");
			cf.add(Opcode.ACONST_NULL);
		}
		cf.freeLocal(cls);
		cf.freeLocal(func);
		return null;
	}

	public Object throwNode(SimpleNode node, Context context) {
		CompileContext cc = (CompileContext) context;
		ClassFile cf = cc.cf;
		int ctx = cc.getContextIndex();

		addLineInfo(cc, ctx, node);

		int n = node.jjtGetNumChildren();
		if (n == 0) {
			cf.add(Opcode.GETSTATIC, "pnuts.lang.PnutsFunction", "THROW",
					"Lpnuts/lang/PnutsFunction;");
		} else {
			accept(node, 0, context);
			int arg = cf.getLocal();
			cf.storeLocal(arg);
			cf.loadLocal(arg);
			cf.add(Opcode.INSTANCEOF, "java.lang.Throwable");
			Label throwable = cf.getLabel();
			cf.add(Opcode.IFNE, throwable);

			Label next = cf.getLabel();
			cf.add(Opcode.NEW, "pnuts.lang.PnutsException");
			cf.add(Opcode.DUP);
			cf.loadLocal(arg);
			cf.add(Opcode.INVOKESTATIC, "java.lang.String", "valueOf",
					"(Ljava/lang/Object;)", "Ljava/lang/String;");
			cf.loadLocal(cc.getContextIndex());
			cf.add(Opcode.INVOKESPECIAL, "pnuts.lang.PnutsException", "<init>",
					"(Ljava/lang/String;Lpnuts/lang/Context;)", "V");
			cf.add(Opcode.GOTO, next);
			throwable.fix();
			cf.loadLocal(arg);
			cf.add(Opcode.CHECKCAST, "java.lang.Throwable");

			cf.freeLocal(arg);
			next.fix();
			cf.add(Opcode.ATHROW);
		}
		return null;
	}

	public Object finallyNode(SimpleNode node, Context context) {
		CompileContext cc = (CompileContext) context;
		ClassFile cf = cc.cf;
		int ctx = cc.getContextIndex();
		int n = node.jjtGetNumChildren();
		if (n == 0) {
			cf.add(Opcode.ACONST_NULL);
		} else if (n == 1) {
			Frame env = cc.env;
			if (env.parent != null) {
				env.finallySet = true;
				int fin = cf.getLocal();
				cc.declare("!finally", fin, -1);
				accept(node, 0, context);
				cf.storeLocal(fin);
				cf.loadLocal(fin);
			} else {
				cf.loadLocal(ctx);
				int val = cf.getLocal();
				accept(node, 0, context);
				cf.storeLocal(val);
				cf.loadLocal(val);
				cf.add(Opcode.CHECKCAST, "pnuts.lang.PnutsFunction");
				cf.add(Opcode.INVOKESTATIC,
				       "pnuts.lang.Runtime",
				       "setExitHook",
				       "(Lpnuts/lang/Context;Lpnuts/lang/PnutsFunction;)",
				       "V");
				cf.loadLocal(val);
			}
		} else if (n == 2) {
			int value = cf.getLocal();
			int retAddr = cf.getLocal();
			Label catchStart = cf.getLabel(true);
			Label jsrTag = cf.getLabel();

			accept(node, 0, context);
			cf.add(Opcode.CHECKCAST, "pnuts.lang.PnutsFunction");
			cf.add(Opcode.GETSTATIC, cc.constClassName, "NO_PARAM",
					"[Ljava/lang/Object;");
			cf.loadLocal(ctx);
			cf.add(Opcode.INVOKEVIRTUAL, "pnuts.lang.PnutsFunction", "call",
					"([Ljava/lang/Object;Lpnuts/lang/Context;)",
					"Ljava/lang/Object;");
			cf.storeLocal(value);

			Label catchEnd = cf.getLabel(true);

			cf.add(Opcode.JSR, jsrTag);

			cf.loadLocal(value);
			cf.add(Opcode.ARETURN);

			Label finallyTag = cf.getLabel(true);

			cf.reserveStack(1);
			cf.add(Opcode.ATHROW);

			jsrTag.fix();

			cf.reserveStack(1);
			cf.storeLocal(retAddr);
			accept(node, 1, context);
			cf.add(Opcode.CHECKCAST, "pnuts.lang.PnutsFunction");
			cf.add(Opcode.GETSTATIC, cc.constClassName, "NO_PARAM",
					"[Ljava/lang/Object;");
			cf.loadLocal(ctx);
			cf.add(Opcode.INVOKEVIRTUAL, "pnuts.lang.PnutsFunction", "call",
					"([Ljava/lang/Object;Lpnuts/lang/Context;)",
					"Ljava/lang/Object;");
			cf.add(Opcode.POP);
			cf.add(Opcode.RET, retAddr);

			cf.addExceptionHandler(catchStart, catchEnd, finallyTag, null);
		}
		return null;
	}

	/*
	 * while or do..while
	 */
	private Object conditionLoop(SimpleNode node, Context context,
			boolean isDoWhile) {
		CompileContext cc = (CompileContext) context;
		ClassFile cf = cc.cf;
		int ctx = cc.getContextIndex();

		addLineInfo(cc, ctx, node);

		cf.add(Opcode.ACONST_NULL);
		int last = cf.getLocal();
		cf.storeLocal(last);

		Label start = cf.getLabel(true);
		Label next = cf.getLabel();
		Label brk = cf.getLabel();
		Label cont = cf.getLabel();

		ControlEnv env;
		if (isDoWhile) {
			env = cc.openControlEnv(PnutsParserTreeConstants.JJTDOSTATEMENT);
		} else {
			env = cc.openControlEnv(PnutsParserTreeConstants.JJTWHILESTATEMENT);
		}
		env.breakLabel = brk;
		env.continueLabel = cont;

		if (isDoWhile) {
			if (traceMode){
				addLineInfo(cc, ctx, node);
			}
			accept(node, 0, context);
			cont.fix();
			cf.storeLocal(last);
		}

		SimpleNode condNode = node.jjtGetChild(isDoWhile ? 1 : 0);
		condNode.accept(this, context);

		if (condNode.getAttribute("inlinedBoolean") != null){
			cf.add(Opcode.IFEQ, next);
		} else {
			booleanCheck(node.jjtGetChild(0).id, cf, context);
			cf.add(Opcode.CHECKCAST, "java.lang.Boolean");
			cf.add(Opcode.INVOKEVIRTUAL, "java.lang.Boolean", "booleanValue",
					"()", "Z");
			cf.add(Opcode.IFEQ, next);
		}

		if (!isDoWhile) {
			if (traceMode){
				addLineInfo(cc, ctx, node);
			}
			accept(node, 1, context);
			cont.fix();
			cf.storeLocal(last);
		}
		cf.add(Opcode.GOTO, start);

		next.fix();

		cc.closeControlEnv();

		cf.loadLocal(last);
		cf.freeLocal(last);
		brk.fix();
		return null;
	}

	public Object doStatement(SimpleNode node, Context context) {
		return conditionLoop(node, context, true);
	}

	public Object whileStatement(SimpleNode node, Context context) {
		return conditionLoop(node, context, false);
	}

	private void doGenerator(String[] vars, int tgt, SimpleNode blockNode,
			CompileContext cc, ClassFile cf) {
		String cls = className + "$" + (classCount++ & 0x7fffffffffffffffL);

		String tmpVar;
		if (vars.length > 1){
		    tmpVar = "_tmp";
		    blockNode = GeneratorHelper.expandMultiAssign(vars, tmpVar, blockNode);
		} else {
		    tmpVar = vars[0];
		}

		ClassFile cf2 = new ClassFile(cls, "pnuts.lang.PnutsFunction", sourceFile,
				Constants.ACC_PUBLIC);
		cf2.addInterface("pnuts.compiler.Compiled");
		cc.classFiles.add(cf2);
		CompileContext cc2 = (CompileContext) cc.clone();
		cc2.returnLabel = cf2.getLabel();
		cc2.cf = cf2;

		cc2._openFrame(null, new String[] { tmpVar }, false);
		cf2.openMethod("exec",
				"([Ljava/lang/Object;Lpnuts/lang/Context;)Ljava/lang/Object;",
				Constants.ACC_PROTECTED);
		cc2.setContextIndex(2);
		cc2.inGeneratorBlock = true;

		cf2.loadLocal(0);
		cf2.add(Opcode.GETFIELD, cls, "$context$", "Lpnuts/lang/Context;");
		cf2.storeLocal(2);

		cc2._declare(tmpVar, 1, 0);

		blockNode.accept(this, cc2);

		cc2.returnLabel.fix();
		cf2.add(Opcode.ARETURN);

		cc2.inGeneratorBlock = false;
		cf2.closeMethod();

		StringBuffer arg = new StringBuffer("(");
		int n_names = 0;
		if (cc2.env.imports != null) {
			n_names = cc2.env.imports.size();
		}
		String names[] = new String[n_names];
		int j = 0;
		if (n_names > 0) {
		    List imports = cc2.env.imports;
			for (int i = 0, n = imports.size(); i < n; i++){
				String sym = (String) imports.get(i);
				names[j++] = sym;
				arg.append("[Ljava/lang/Object;");
			}
		}

		arg.append("Lpnuts/lang/Context;");
		arg.append(")");

		cf2.openMethod("<init>", arg + "V", Constants.ACC_PUBLIC);
		cf2.add(Opcode.ALOAD_0);
		cf2.add(Opcode.INVOKESPECIAL, "pnuts.lang.PnutsFunction", "<init>",
				"()", "V");

		for (int i = 0; i < n_names; i++) {
			cf2.addField(names[i], "[Ljava/lang/Object;", (short) 0);
			cf2.add(Opcode.ALOAD_0);
			cf2.add(Opcode.ALOAD, 1 + i);
			cf2.add(Opcode.PUTFIELD, cf2.getClassName(), names[i],
					"[Ljava/lang/Object;");
		}

		cf2.addField("$context$", "Lpnuts/lang/Context;", (short) 0);
		cf2.add(Opcode.ALOAD_0);
		cf2.add(Opcode.ALOAD, 1 + n_names);
		cf2.add(Opcode.PUTFIELD, cf2.getClassName(), "$context$",
				"Lpnuts/lang/Context;");

		cf2.add(Opcode.RETURN);
		cf2.closeMethod();

		cf.loadLocal(tgt);
		cf.add(Opcode.CHECKCAST, "pnuts.lang.Generator");
		cf.add(Opcode.NEW, cls);
		cf.add(Opcode.DUP);

		ArrayList exports = (ArrayList) cc.env.exports.get(cc2.env);

		cc2._closeFrame();

		if (exports != null) {

			int size = exports.size();
			for (int i = 0; i < size; i++) {
				Reference ref = (Reference) exports.get(i);
				if (ref.index < 0) {
					cf.add(Opcode.ALOAD_0);
					cf.add(Opcode.GETFIELD, cf.getClassName(), ref.symbol,
							"[Ljava/lang/Object;");
				} else if (ref.offset < 0) {
					cf.add(Opcode.ICONST_1);
					cf.add(Opcode.ANEWARRAY, "java.lang.Object");
					cf.add(Opcode.DUP);
					cf.add(Opcode.ICONST_0);
					cf.loadLocal(ref.index);
					cf.add(Opcode.AASTORE);
				} else {
					cf.loadLocal(ref.index);
					Label l1 = cf.getLabel();
					cf.add(Opcode.IFNONNULL, l1);
					cf.add(Opcode.ICONST_1);
					cf.add(Opcode.ANEWARRAY, "java.lang.Object");
					cf.storeLocal(ref.index);
					l1.fix();
					cf.loadLocal(ref.index);
					cf.add(Opcode.CHECKCAST, "[Ljava/lang/Object;");
				}
			}
		}

		cf.loadLocal(cc.getContextIndex());
		cf.add(Opcode.INVOKESPECIAL, cls, "<init>", arg.toString(), "V");

		cf.loadLocal(cc.getContextIndex());
		cf.add(Opcode.INVOKESTATIC,
		       "pnuts.lang.Runtime",
		       "applyGenerator",
		       "(Lpnuts/lang/Generator;Lpnuts/lang/PnutsFunction;Lpnuts/lang/Context;)",
		       "Ljava/lang/Object;");
	}

	private void doForeach(String[] vars, SimpleNode collectionNode,
			SimpleNode blockNode, CompileContext cc, ClassFile cf) {
		int ctx = cc.getContextIndex();
		cc.openScope(vars);
		/*
		Reference count = cc.getReference(vars[0]);
		cf.add(Opcode.ICONST_1);
		cf.add(Opcode.ANEWARRAY, "java.lang.Object");
		cf.storeLocal(count.index);
		*/
		Reference[] count = new Reference[vars.length];
		for (int i = 0; i < vars.length; i++){
		    count[i] = cc.getReference(vars[i]);
		    cf.add(Opcode.ICONST_1);
		    cf.add(Opcode.ANEWARRAY, "java.lang.Object");
		    cf.storeLocal(count[i].index);		    
		}

		Label next = cf.getLabel();
		Label brk = cf.getLabel();

		ControlEnv env = cc
				.openControlEnv(PnutsParserTreeConstants.JJTFORSTATEMENT);
		env.breakLabel = brk;

		int last = cf.getLocal();
		cf.add(Opcode.ACONST_NULL);
		cf.storeLocal(last);

		collectionNode.accept(this, cc);
		int tgt = cf.getLocal();
		cf.add(Opcode.DUP);
		cf.storeLocal(tgt);

		Label nonnull = cf.getLabel();

		cf.add(Opcode.IFNONNULL, nonnull);
		cf.add(Opcode.GOTO, next);
		nonnull.fix();

		cf.loadLocal(tgt);
		cf.add(Opcode.INSTANCEOF, "pnuts.lang.Generator");
		Label fn = cf.getLabel();
		cf.add(Opcode.IFNE, fn);

		cf.loadLocal(tgt);
		cf.loadLocal(cc.getContextIndex());
		cf.add(Opcode.INVOKESTATIC, "pnuts.lang.Runtime", "toEnumeration",
				"(Ljava/lang/Object;Lpnuts/lang/Context;)",
				"Ljava/util/Enumeration;");
		int tmp1 = cf.getLocal();
		cf.storeLocal(tmp1);
		cf.loadLocal(tmp1);

		Label err = cf.getLabel();
		cf.add(Opcode.IFNULL, err);

		Label loop2 = cf.getLabel();
		cf.add(Opcode.GOTO, loop2);
		Label body = cf.getLabel(true);

		cf.loadLocal(tmp1);
		cf.add(Opcode.CHECKCAST, "java.util.Enumeration");
		cf.add(Opcode.INVOKEINTERFACE, "java.util.Enumeration", "nextElement",
				"()", "Ljava/lang/Object;");

		int tmp2 = cf.getLocal();
		cf.storeLocal(tmp2);

		if (vars.length > 1){ // multi-assignment
		    int tmp3 = cf.getLocal();
		    for (int i = 0; i < vars.length; i++){
			cf.loadLocal(tmp2);
			cf.pushInteger(i);
			cf.loadLocal(ctx);
			cf.add(Opcode.INVOKESTATIC, "pnuts.lang.Runtime",
			       "getElementAt", "(Ljava/lang/Object;ILpnuts/lang/Context;)", "Ljava/lang/Object;");
			cf.storeLocal(tmp3);
			count[i].set(cf, tmp3);
		    }
		} else {
		    count[0].set(cf, tmp2);
		}

		Label cont = cf.getLabel();
		env.continueLabel = cont;

		blockNode.accept(this, cc);
		cont.fix();
		cf.storeLocal(last);

		loop2.fix();
		cf.loadLocal(tmp1);
		cf.add(Opcode.CHECKCAST, "java.util.Enumeration");
		cf.add(Opcode.INVOKEINTERFACE, "java.util.Enumeration",
				"hasMoreElements", "()", "Z");
		cf.add(Opcode.IFEQ, next);
		cf.add(Opcode.GOTO, body);

		fn.fix();
		doGenerator(vars, tgt, blockNode, cc, cf);
		cf.storeLocal(last);
		cf.add(Opcode.GOTO, next);

		err.fix();
		error(cf, "illegal.type.foreach", new int[] { tgt }, cc);

		next.fix();
		cf.loadLocal(last);
		cf.freeLocal(last);
		cf.freeLocal(tmp1);
		cf.freeLocal(tmp2);
		cf.freeLocal(tgt);

		cc.closeControlEnv();

		cc.closeScope();
		brk.fix();
	}

	private void doForeachRange(String var, SimpleNode startNode,
			SimpleNode endNode, SimpleNode blockNode, CompileContext cc,
			ClassFile cf) {
		cc.openScope(new String[] { var });
		Reference count = cc.getReference(var);
		cf.add(Opcode.ICONST_1);
		cf.add(Opcode.ANEWARRAY, "java.lang.Object");
		cf.storeLocal(count.index);

		Label next = cf.getLabel();
		Label brk = cf.getLabel();

		ControlEnv env = cc
				.openControlEnv(PnutsParserTreeConstants.JJTFORSTATEMENT);
		env.breakLabel = brk;

		int last = cf.getLocal();
		int tmp = cf.getLocal();
		cf.add(Opcode.ACONST_NULL);
		cf.storeLocal(last);

		int start = cf.getLocal();
		startNode.accept(this, cc);
		cf.add(Opcode.CHECKCAST, "java.lang.Number");
		cf.add(Opcode.INVOKEVIRTUAL, "java.lang.Number", "intValue", "()", "I");
		cf.istoreLocal(start);

		int end = cf.getLocal();
		endNode.accept(this, cc);
		cf.add(Opcode.CHECKCAST, "java.lang.Number");
		cf.add(Opcode.INVOKEVIRTUAL, "java.lang.Number", "intValue", "()", "I");
		cf.istoreLocal(end);

		cf.iloadLocal(start);
		cf.iloadLocal(end);
		Label loop2 = cf.getLabel();
		cf.add(Opcode.IF_ICMPGT, loop2);

		Label loop1 = cf.getLabel(true);

		cf.iloadLocal(start);
		cf.iloadLocal(end);
		cf.add(Opcode.IF_ICMPGT, next);

		if (hasValueOfPrimitive){
		    cf.iloadLocal(start);
		    cf.add(Opcode.INVOKESTATIC, "java.lang.Integer", "valueOf", "(I)", "Ljava/lang/Integer;");
		} else {
		    cf.add(Opcode.NEW, "java.lang.Integer");
		    cf.add(Opcode.DUP);
		    cf.iloadLocal(start);
		    cf.add(Opcode.INVOKESPECIAL, "java.lang.Integer", "<init>", "(I)", "V");
		}

		cf.storeLocal(tmp);
		count.set(cf, tmp);

		Label cont = cf.getLabel();
		env.continueLabel = cont;

		blockNode.accept(this, cc);
		cont.fix();
		cf.storeLocal(last);

		cf.add(Opcode.IINC, start, 1);
		cf.add(Opcode.GOTO, loop1);

		loop2.fix();

		cf.iloadLocal(start);
		cf.iloadLocal(end);
		cf.add(Opcode.IF_ICMPLT, next);

		if (hasValueOfPrimitive){
		    cf.iloadLocal(start);
		    cf.add(Opcode.INVOKESTATIC, "java.lang.Integer", "valueOf", "(I)", "Ljava/lang/Integer;");
		} else {
		    cf.add(Opcode.NEW, "java.lang.Integer");
		    cf.add(Opcode.DUP);
		    cf.iloadLocal(start);
		    cf.add(Opcode.INVOKESPECIAL, "java.lang.Integer", "<init>", "(I)", "V");
		}

		cf.storeLocal(tmp);
		count.set(cf, tmp);

		Label cont2 = cf.getLabel();
		env.continueLabel = cont2;

		blockNode.accept(this, cc);
		cont2.fix();
		cf.storeLocal(last);

		cf.add(Opcode.IINC, start, -1);
		cf.add(Opcode.GOTO, loop2);

		next.fix();
		cf.loadLocal(last);
		cf.freeLocal(last);
		cf.freeLocal(tmp);
		cf.freeLocal(start);
		cf.freeLocal(end);

		cc.closeControlEnv();

		cc.closeScope();
		brk.fix();
	}

	public Object foreachStatement(SimpleNode node, Context context) {
		CompileContext cc = (CompileContext) context;
		ClassFile cf = cc.cf;
		int ctx = cc.getContextIndex();

		addLineInfo(cc, ctx, node);

		doForeach(new String[]{node.str}, node.jjtGetChild(0), node.jjtGetChild(1), cc, cf);
		return null;
	}

	public Object switchStatement(SimpleNode node, Context context) {
		CompileContext cc = (CompileContext) context;
		ClassFile cf = cc.cf;
		int ctx = cc.getContextIndex();

		addLineInfo(cc, ctx, node);

		int n = node.jjtGetNumChildren();
		accept(node, 0, context);
		int tgt = cf.getLocal();
		cf.storeLocal(tgt);

		cc.openScope(new String[] {});

		int match = cf.getLocal();
		cf.add(Opcode.ICONST_0);
		cf.istoreLocal(match);
		int last = cf.getLocal();
		cf.add(Opcode.ACONST_NULL);
		cf.storeLocal(last);

		Label next = cf.getLabel();

		ControlEnv env = cc.openControlEnv(PnutsParserTreeConstants.JJTSWITCHSTATEMENT);
		env.breakLabel = next;

		for (int i = 1; i < n; i++) {
			SimpleNode _node = node.jjtGetChild(i);
			if (_node.jjtGetNumChildren() == 1) { // case
				cf.iloadLocal(match);
				Label hit = cf.getLabel();
				cf.add(Opcode.IFNE, hit);
				i++;
				accept(_node, 0, context);
				cf.loadLocal(tgt);
				cf.add(Opcode.INVOKESTATIC, "pnuts.lang.Runtime", "eq",
						"(Ljava/lang/Object;Ljava/lang/Object;)", "Z");
				Label cont = cf.getLabel();
				cf.add(Opcode.IFEQ, cont);
				cf.add(Opcode.ICONST_1); // match = 1
				cf.istoreLocal(match);
				hit.fix();
				accept(node, i, context);
				cf.storeLocal(last);
				cont.fix();
			} else { // default
				cf.add(Opcode.ICONST_1);
				cf.istoreLocal(match);
				accept(node, ++i, context);
				cf.storeLocal(last);
			}
		}
		cf.freeLocal(tgt);
		cf.freeLocal(match);
		cf.loadLocal(last);
		next.fix();

		cc.closeControlEnv();
		cc.closeScope();

		return null;
	}

	public Object switchBlock(SimpleNode node, Context context) {
		CompileContext cc = (CompileContext) context;
		ClassFile cf = cc.cf;

		int n = node.jjtGetNumChildren();
		if (n > 0) {
			int m = n - 1;
			for (int i = 0; i < m; i++) {
				accept(node, i, context);
				cf.add(Opcode.POP);
			}
			accept(node, m, context);
		} else {
			cf.add(Opcode.ACONST_NULL);
		}
		return null;
	}

	public Object ternary(SimpleNode node, Context context) {
		CompileContext cc = (CompileContext) context;
		ClassFile cf = cc.cf;
		SimpleNode condNode = node.jjtGetChild(0);
		condNode.accept(this, context);

		Label l_else = cf.getLabel();
		Label next = cf.getLabel();

		if (condNode.getAttribute("inlinedBoolean") != null){
			cf.add(Opcode.IFEQ, l_else);
			accept(node, 1, context);
			cf.add(Opcode.GOTO, next);
			l_else.fix();
			cf.popStack();
		} else {
			booleanCheck(node.jjtGetChild(0).id, cf, context);
			cf.add(Opcode.CHECKCAST, "java.lang.Boolean");
			cf.add(Opcode.INVOKEVIRTUAL, "java.lang.Boolean", "booleanValue",
					"()", "Z");
			cf.add(Opcode.IFEQ, l_else);
			accept(node, 1, context);
			cf.add(Opcode.GOTO, next);
			l_else.fix();
			cf.popStack();
		}
		accept(node, 2, context);
		next.fix();
		return null;
	}

	static String getPackageName(SimpleNode node) {
		if (node.jjtGetNumChildren() > 0) {
			SimpleNode c1 = node.jjtGetChild(0);
			return getPackageName(c1) + "::" + node.str;
		} else {
			return node.str;
		}
	}

	static String gensym(Context context) {
		return ((CompileContext) context).sym.gen();
	}

	protected Object accept(SimpleNode node, int idx, Context context) {
		return node.jjtGetChild(idx).accept(this, context);
	}

	private Object assign(SimpleNode node, Context context, int id) {
		accept(node, 1, context);
		_assign(node.jjtGetChild(0), context, id, false, true);
		return null;
	}

	private Object _assign(SimpleNode lhs, Context context, int id,
			boolean mutable, boolean nopop) {
		CompileContext cc = (CompileContext) context;
		ClassFile cf = cc.cf;
		int ctx = cc.getContextIndex();
		int rhs = cf.getLocal();
		if ((id & 0x8000) == 0) {
			cf.storeLocal(rhs);
		}

		if (lhs.id == PnutsParserTreeConstants.JJTIDNODE) {
			assignId(cf, id, ctx, rhs, lhs, cc, mutable, nopop);
		} else if (lhs.id == PnutsParserTreeConstants.JJTGLOBAL) {
			assignGlobal(cf, id, ctx, rhs, lhs, cc, nopop);
		} else if (lhs.id == PnutsParserTreeConstants.JJTINDEXNODE) {
			assignIndex(cf, id, ctx, rhs, lhs, cc, nopop);
		} else if (lhs.id == PnutsParserTreeConstants.JJTSTATICMEMBERNODE) {
			assignStaticMember(cf, id, ctx, rhs, lhs, cc, nopop);
		} else if (lhs.id == PnutsParserTreeConstants.JJTMEMBERNODE) {
			assignMember(cf, id, ctx, rhs, lhs, cc, nopop);
		} else if (lhs.id == PnutsParserTreeConstants.JJTRANGENODE
				&& id == PnutsParserTreeConstants.JJTASSIGNMENT) {
			assignRange(cf, id, ctx, rhs, lhs, cc);
		} else {
			throw new PnutsException("illegal.assign", new Object[] {}, context);
		}
		cf.freeLocal(rhs);
		return null;
	}

	boolean inControl(SimpleNode node, CompileContext cc) {
		while (node != null) {
			if (node.id == PnutsParserTreeConstants.JJTFUNCTIONSTATEMENT ||
			    node.id == PnutsParserTreeConstants.JJTMETHODDEF ) {
				return false;
			} else if (cc.inGeneratorBlock
				   && (node.id == PnutsParserTreeConstants.JJTFOREACHSTATEMENT ||
				       node.id == PnutsParserTreeConstants.JJTFORSTATEMENT
				       && node.jjtGetChild(0).id == PnutsParserTreeConstants.JJTFORENUM))
			{
				return false;
			} else if (node.id == PnutsParserTreeConstants.JJTSWITCHSTATEMENT
					|| node.id == PnutsParserTreeConstants.JJTFOREACHSTATEMENT
					|| node.id == PnutsParserTreeConstants.JJTFORSTATEMENT
					|| node.id == PnutsParserTreeConstants.JJTWHILESTATEMENT
					|| node.id == PnutsParserTreeConstants.JJTDOSTATEMENT
					|| node.id == PnutsParserTreeConstants.JJTIFSTATEMENT) {
				return true;
			}
			node = node.jjtGetParent();
		}
		return false;
	}
	
	static boolean isConditionalNode(SimpleNode node){
		int id = node.jjtGetParent().id;
		return id == PnutsParserTreeConstants.JJTIFSTATEMENT
			|| id == PnutsParserTreeConstants.JJTELSEIFNODE
			|| id == PnutsParserTreeConstants.JJTFORSTATEMENT
			|| id == PnutsParserTreeConstants.JJTWHILESTATEMENT
			|| id == PnutsParserTreeConstants.JJTDOSTATEMENT;
		
	}
	
	void assignId(ClassFile cf, int id, int ctx, int rhs, SimpleNode lhs,
			CompileContext cc, boolean mutable, boolean nopop) {
		Reference ref = cc.findReference(lhs.str);
		Frame env = cc.env;

		if (cc.inGeneratorBlock) {
			if (ref == null) {
				if (cc.env != null && cc.env.parent != null) {
					int ret = -1;
					if (id != PnutsParserTreeConstants.JJTASSIGNMENT) {
						if ((id & 0x8000) == 0) {
							cf.loadLocal(ctx);
							cf.add(Opcode.LDC, cf.addConstant(lhs.str));
							cf.add(Opcode.INVOKEVIRTUAL, "pnuts.lang.Context",
							       "getId", "(Ljava/lang/String;)",
							       "Ljava/lang/Object;");
							ret = cf.getLocal();
							cf.storeLocal(ret);
							cf.loadLocal(ret);
							cf.loadLocal(rhs);
						}
						compute(cf, id, ctx);
						cf.storeLocal(rhs);
					}
					cf.loadLocal(ctx);
					cf.add(Opcode.INVOKEVIRTUAL, "pnuts.lang.Context",
					       "getCurrentPackage", "()", "Lpnuts/lang/Package;");
					cf.add(Opcode.LDC, cf.addConstant(lhs.str));
					cf.loadLocal(rhs);
					cf.loadLocal(ctx);
					cf.add(Opcode.INVOKEVIRTUAL,
					       "pnuts.lang.Package",
					       "set",
					       "(Ljava/lang/String;Ljava/lang/Object;Lpnuts/lang/Context;)",
					       "V");
					if (nopop) {
						cf.loadLocal(rhs);
					}

					return;
				}
			}
		}

		if (cc.env.parent != null && !mutable) {
			int idx = 0;

			if (ref == null) {
				cc.declare(lhs.str);
				ref = cc.getReference(lhs.str);
			} else if (id == PnutsParserTreeConstants.JJTASSIGNMENT){
				cc.redefine(lhs.str);
			}
			if (id != PnutsParserTreeConstants.JJTASSIGNMENT) {
				if ((id & 0x8000) == 0) {
					getRef(ref, cc);
					cf.loadLocal(rhs);
				}
				compute(cf, id, ctx);
				cf.storeLocal(rhs);
			}
			ref.set(cf, rhs);

			if (!inControl(lhs, cc)) {
				cc.setReference(lhs.str);
			}

			if (nopop) {
				cf.loadLocal(rhs);
			}
		} else {
			if (ref != null) {
				if (id != PnutsParserTreeConstants.JJTASSIGNMENT) {
					if ((id & 0x8000) == 0) {
						getRef(ref, cc);
						cf.loadLocal(rhs);
					}
					compute(cf, id, ctx);
					cf.storeLocal(rhs);
				}
				ref.set(cf, rhs);
				if (nopop) {
					cf.loadLocal(rhs);
				}
			} else {
				int ret = -1;
				if (id != PnutsParserTreeConstants.JJTASSIGNMENT) {
					if ((id & 0x8000) == 0) {
						cf.loadLocal(ctx);
						cf.add(Opcode.LDC, cf.addConstant(lhs.str));
						cf.add(Opcode.INVOKEVIRTUAL, "pnuts.lang.Context",
								"getId", "(Ljava/lang/String;)",
								"Ljava/lang/Object;");
						ret = cf.getLocal();
						cf.storeLocal(ret);
						cf.loadLocal(ret);
						cf.loadLocal(rhs);
					}
					compute(cf, id, ctx);
					cf.storeLocal(rhs);
				}
				cf.loadLocal(ctx);
				cf.add(Opcode.INVOKEVIRTUAL, "pnuts.lang.Context",
						"getCurrentPackage", "()", "Lpnuts/lang/Package;");
				cf.add(Opcode.LDC, cf.addConstant(lhs.str));
				cf.loadLocal(rhs);
				cf.loadLocal(ctx);
				cf.add(Opcode.INVOKEVIRTUAL,
				       "pnuts.lang.Package",
				       "set",
				       "(Ljava/lang/String;Ljava/lang/Object;Lpnuts/lang/Context;)",
				       "V");
				if (nopop) {
					cf.loadLocal(rhs);
				}
			}
		}
	}

	void assignMember(ClassFile cf, int id, int ctx, int rhs, SimpleNode lhs,
			CompileContext cc, boolean nopop) {
		accept(lhs, 0, cc);
		int tgt = cf.getLocal();
		cf.storeLocal(tgt);
		if (id != PnutsParserTreeConstants.JJTASSIGNMENT) {
			if ((id & 0x8000) == 0) {
				cf.loadLocal(ctx);
				cf.loadLocal(tgt);
				cf.add(Opcode.LDC, cf.addConstant(lhs.str));
				cf.add(Opcode.INVOKESTATIC,
				       "pnuts.lang.Runtime",
				       "getField",
				       "(Lpnuts/lang/Context;Ljava/lang/Object;Ljava/lang/String;)",
				       "Ljava/lang/Object;");
				cf.loadLocal(rhs);
			}
			compute(cf, id, ctx);
			cf.storeLocal(rhs);
		}

		cf.loadLocal(ctx);
		cf.loadLocal(tgt);
		cf.freeLocal(tgt);
		cf.add(Opcode.LDC, cf.addConstant(lhs.str));
		cf.loadLocal(rhs);
		cf.add(Opcode.INVOKESTATIC,
		       "pnuts.lang.Runtime",
		       "putField",
		       "(Lpnuts/lang/Context;Ljava/lang/Object;Ljava/lang/String;Ljava/lang/Object;)",
		       "V");
		if (nopop) {
			cf.loadLocal(rhs);
		}
	}

	void assignIndex(ClassFile cf, int id, int ctx, int rhs, SimpleNode lhs,
			CompileContext cc, boolean nopop) {
		SimpleNode idxNode = lhs.jjtGetChild(1);
		if (ConstraintsTransformer.isPredicate(idxNode)) {
			SimpleNode n = ConstraintsTransformer.buildFunc(idxNode);
			lhs.jjtAddChild(n, 1);
			n.jjtSetParent(lhs);
		}
		accept(lhs, 0, cc);
		int tgt = cf.getLocal();
		cf.storeLocal(tgt);

		accept(lhs, 1, cc);
		int idx = cf.getLocal();
		cf.storeLocal(idx);

		Label next = cf.getLabel();

		cf.loadLocal(tgt);
		cf.add(Opcode.INSTANCEOF, "java.lang.String");
		Label l2 = cf.getLabel();
		cf.add(Opcode.IFEQ, l2);
		if (id != PnutsParserTreeConstants.JJTASSIGNMENT) {
			error(cf, "illegal.assign", cc);
		} else {
			cf.loadLocal(tgt);
			cf.add(Opcode.CHECKCAST, "java.lang.String");
			cf.loadLocal(idx);
			cf.add(Opcode.CHECKCAST, "java.lang.Number");
			cf.loadLocal(rhs);

			cf.add(Opcode.INSTANCEOF, "java.lang.Character");
			Label ok = cf.getLabel();
			cf.add(Opcode.IFNE, ok);
			error(cf, "illegal.assign", cc);
			ok.fix();
			cf.loadLocal(rhs);

			cf.add(Opcode.INVOKESTATIC,
			       "pnuts.lang.Runtime",
			       "replaceChar",
			       "(Ljava/lang/String;Ljava/lang/Number;Ljava/lang/Object;)",
			       "Ljava/lang/String;");

			SimpleNode ch = lhs.jjtGetChild(0);
			if (ch.id == PnutsParserTreeConstants.JJTIDNODE
					|| ch.id == PnutsParserTreeConstants.JJTGLOBAL
					|| ch.id == PnutsParserTreeConstants.JJTINDEXNODE
					|| ch.id == PnutsParserTreeConstants.JJTMEMBERNODE
					|| ch.id == PnutsParserTreeConstants.JJTSTATICMEMBERNODE)
			{
				if (ch.str != null){
					if (cc.env.parent == null || cc.getReference(ch.str) != null){
						_assign(ch, cc, id, true, true);
					}
				}
			}
			cf.add(Opcode.GOTO, next);
		}

		l2.fix();

		if (id == PnutsParserTreeConstants.JJTASSIGNMENT) {
			cf.loadLocal(tgt);
			cf.loadLocal(idx);
			cf.loadLocal(rhs);
			cf.loadLocal(ctx);
			cf.add(Opcode.INVOKESTATIC,
			       "pnuts.lang.Runtime",
			       "setElement",
			       "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Lpnuts/lang/Context;)",
			       "V");
			if (nopop) {
				cf.loadLocal(rhs);
			}
		} else {
			if ((id & 0x8000) == 0) {
				cf.loadLocal(tgt);
				cf.loadLocal(idx);
				cf.loadLocal(ctx);
				cf.add(Opcode.INVOKESTATIC,
				       "pnuts.lang.Runtime",
				       "getElement",
				       "(Ljava/lang/Object;Ljava/lang/Object;Lpnuts/lang/Context;)",
				       "Ljava/lang/Object;");
				cf.loadLocal(rhs);
			}
			compute(cf, id, ctx);
			cf.storeLocal(rhs);
			cf.loadLocal(tgt);
			cf.loadLocal(idx);
			cf.loadLocal(rhs);
			cf.loadLocal(ctx);
			cf.add(Opcode.INVOKESTATIC,
			       "pnuts.lang.Runtime",
			       "setElement",
			       "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Lpnuts/lang/Context;)",
			       "V");
			if (nopop) {
				cf.loadLocal(rhs);
			}
		}
		cf.freeLocal(tgt);
		cf.freeLocal(idx);
		next.fix();
	}

	void assignRange(ClassFile cf, int id, int ctx, int rhs, SimpleNode lhs,
			CompileContext cc) {
		accept(lhs, 0, cc);
		int tgt = cf.getLocal();
		cf.storeLocal(tgt);

		accept(lhs, 1, cc);
		int idx1 = cf.getLocal();
		cf.storeLocal(idx1);

		int idx2 = cf.getLocal();
		if (lhs.jjtGetNumChildren() >= 3) {
			accept(lhs, 2, cc);
		} else {
			cf.add(Opcode.ACONST_NULL);
		}
		cf.storeLocal(idx2);

		cf.loadLocal(tgt);
		cf.loadLocal(idx1);
		cf.freeLocal(idx1);
		cf.loadLocal(idx2);
		cf.freeLocal(idx2);
		cf.loadLocal(rhs);

		cf.loadLocal(cc.getContextIndex());
		cf.add(Opcode.INVOKESTATIC,
		       "pnuts.lang.Runtime",
		       "setRange",
		       "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Lpnuts/lang/Context;)",
		       "Ljava/lang/Object;");

		SimpleNode ch = lhs.jjtGetChild(0);
		if (ch.id == PnutsParserTreeConstants.JJTIDNODE
				|| ch.id == PnutsParserTreeConstants.JJTGLOBAL
				|| ch.id == PnutsParserTreeConstants.JJTINDEXNODE
				|| ch.id == PnutsParserTreeConstants.JJTMEMBERNODE
				|| ch.id == PnutsParserTreeConstants.JJTSTATICMEMBERNODE) {
			int assigned = cf.getLocal();
			cf.storeLocal(assigned);
			cf.loadLocal(tgt);
			cf.freeLocal(tgt);
			cf.add(Opcode.INSTANCEOF, "java.lang.String");
			Label no = cf.getLabel();
			cf.add(Opcode.IFEQ, no);
			cf.loadLocal(assigned);
			_assign(ch, cc, id, true, true);
			cf.add(Opcode.POP);
			no.fix();
			cf.loadLocal(assigned);
			cf.freeLocal(assigned);
		}
	}

	void assignStaticMember(ClassFile cf, int id, int ctx, int rhs,
			SimpleNode lhs, CompileContext cc, boolean nopop) {
		SimpleNode c1 = lhs.jjtGetChild(0);
		String pkgName = getPackageName(c1);
		cf.add(Opcode.LDC, cf.addConstant(pkgName));
		cf.loadLocal(ctx);
		cf.add(Opcode.INVOKESTATIC, "pnuts.lang.Package", "find",
				"(Ljava/lang/String;Lpnuts/lang/Context;)",
				"Lpnuts/lang/Package;");
		int tgt = cf.getLocal();
		cf.storeLocal(tgt);
		cf.loadLocal(tgt);

		Label l_null = cf.getLabel();
		cf.add(Opcode.IFNULL, l_null);

		if (id != PnutsParserTreeConstants.JJTASSIGNMENT) {
			if ((id & 0x8000) == 0) {
				cf.loadLocal(tgt);
				cf.add(Opcode.LDC, cf.addConstant(lhs.str));
				cf.loadLocal(ctx);
				cf.add(Opcode.INVOKEVIRTUAL, "pnuts.lang.Package", "get",
						"(Ljava/lang/String;Lpnuts/lang/Context;)",
						"Ljava/lang/Object;");
				cf.loadLocal(rhs);
			}
			compute(cf, id, ctx);
			cf.storeLocal(rhs);
		}
		cf.loadLocal(tgt);
		cf.add(Opcode.LDC, cf.addConstant(lhs.str));
		cf.loadLocal(rhs);
		cf.loadLocal(ctx);
		cf.add(Opcode.INVOKEVIRTUAL, "pnuts.lang.Package", "set",
				"(Ljava/lang/String;Ljava/lang/Object;Lpnuts/lang/Context;)",
				"V");
		if (nopop) {
			cf.loadLocal(rhs);
		}

		Label next = cf.getLabel();
		cf.add(Opcode.GOTO, next);
		l_null.fix();
		if (nopop) {
			cf.popStack();
		}
		c1.accept(this, cc);
		cf.add(Opcode.DUP);
		cf.storeLocal(tgt);

		cf.add(Opcode.INSTANCEOF, "java.lang.Class");
		Label err = cf.getLabel();
		cf.add(Opcode.IFEQ, err);
		if (id != PnutsParserTreeConstants.JJTASSIGNMENT) {
			if ((id & 0x8000) == 0) {
				cf.loadLocal(ctx);
				cf.loadLocal(tgt);
				cf.add(Opcode.CHECKCAST, "java.lang.Class");
				cf.add(Opcode.LDC, cf.addConstant(lhs.str));
				cf.add(Opcode.INVOKESTATIC,
				       "pnuts.lang.Runtime",
				       "getStaticField",
				       "(Lpnuts/lang/Context;Ljava/lang/Class;Ljava/lang/String;)",
				       "Ljava/lang/Object;");
				cf.loadLocal(rhs);
			}
			compute(cf, id, ctx);
			cf.storeLocal(rhs);
		}
		cf.loadLocal(ctx);
		cf.loadLocal(tgt);
		cf.freeLocal(tgt);
		cf.add(Opcode.CHECKCAST, "java.lang.Class");
		cf.add(Opcode.LDC, cf.addConstant(lhs.str));
		cf.loadLocal(rhs);
		cf.add(Opcode.INVOKESTATIC,
		       "pnuts.lang.Runtime",
		       "putStaticField",
		       "(Lpnuts/lang/Context;Ljava/lang/Class;Ljava/lang/String;Ljava/lang/Object;)",
		       "V");
		if (nopop) {
			cf.loadLocal(rhs);
		}
		cf.add(Opcode.GOTO, next);
		err.fix();
		error(cf, "package.notFound", cc);
		next.fix();
	}

	void assignGlobal(ClassFile cf, int id, int ctx, int rhs, SimpleNode lhs,
			CompileContext cc, boolean nopop) {
		cf.add(Opcode.LDC, cf.addConstant(""));
		cf.loadLocal(ctx);
		cf.add(Opcode.INVOKESTATIC, "pnuts.lang.Package", "find",
				"(Ljava/lang/String;Lpnuts/lang/Context;)",
				"Lpnuts/lang/Package;");
		int gbl = cf.getLocal();
		cf.storeLocal(gbl);

		if (id != PnutsParserTreeConstants.JJTASSIGNMENT) {
			if ((id & 0x8000) == 0) {
				cf.loadLocal(gbl);

				cf.add(Opcode.LDC, cf.addConstant(lhs.str));
				cf.loadLocal(ctx);
				cf.add(Opcode.INVOKEVIRTUAL, "pnuts.lang.Package", "get",
						"(Ljava/lang/String;Lpnuts/lang/Context;)",
						"Ljava/lang/Object;");
				cf.loadLocal(rhs);
				cf.freeLocal(gbl);
			}
			compute(cf, id, ctx);
			cf.storeLocal(rhs);
		}
		cf.loadLocal(gbl);
		cf.add(Opcode.LDC, cf.addConstant(lhs.str));
		cf.loadLocal(rhs);
		cf.loadLocal(ctx);
		cf.add(Opcode.INVOKEVIRTUAL, "pnuts.lang.Package", "set",
				"(Ljava/lang/String;Ljava/lang/Object;Lpnuts/lang/Context;)",
				"V");
		if (nopop) {
			cf.loadLocal(rhs);
		}
	}

	void error(ClassFile cf, String keyword, CompileContext cc) {
		error(cf, keyword, (int[]) null, cc);
	}

	void errorSymbol(ClassFile cf, String keyword, int stringConstant,
			CompileContext cc) {
		cf.add(Opcode.NEW, "pnuts.lang.PnutsException");
		cf.add(Opcode.DUP);
		cf.add(Opcode.LDC, cf.addConstant(keyword));
		cf.pushInteger(1);
		cf.add(Opcode.ANEWARRAY, "java.lang.Object");
		cf.add(Opcode.DUP);
		cf.pushInteger(0);
		cf.add(Opcode.LDC, stringConstant);
		cf.add(Opcode.AASTORE);
		int ctx = cc.getContextIndex();
		cf.loadLocal(ctx);
		cf.add(Opcode.INVOKESPECIAL, "pnuts.lang.PnutsException", "<init>",
				"(Ljava/lang/String;[Ljava/lang/Object;Lpnuts/lang/Context;)",
				"V");
		cf.add(Opcode.ATHROW);
	}

	void error(ClassFile cf, String keyword, int[] locals, CompileContext cc) {
		cf.add(Opcode.NEW, "pnuts.lang.PnutsException");
		cf.add(Opcode.DUP);
		cf.add(Opcode.LDC, cf.addConstant(keyword));
		if (locals == null) {
			cf.add(Opcode.GETSTATIC, cc.constClassName, "NO_PARAM",
					"[Ljava/lang/Object;");
		} else {
			cf.pushInteger(locals.length);
			cf.add(Opcode.ANEWARRAY, "java.lang.Object");
			for (int i = 0; i < locals.length; i++) {
				cf.add(Opcode.DUP);
				cf.pushInteger(i);
				cf.loadLocal(locals[i]);
				cf.add(Opcode.AASTORE);
			}
		}

		int ctx = cc.getContextIndex();
		cf.loadLocal(ctx);
		cf.add(Opcode.INVOKESPECIAL, "pnuts.lang.PnutsException", "<init>",
				"(Ljava/lang/String;[Ljava/lang/Object;Lpnuts/lang/Context;)",
				"V");
		cf.add(Opcode.ATHROW);
	}

	void compute(ClassFile cf, int id, int ctx) {
		cf.loadLocal(ctx);
		String method;
		switch (id) {
		case PnutsParserTreeConstants.JJTASSIGNMENTTA:
			method = "multiply";
			break;
		case PnutsParserTreeConstants.JJTASSIGNMENTMA:
			method = "mod";
			break;
		case PnutsParserTreeConstants.JJTASSIGNMENTDA:
			method = "divide";
			break;
		case PnutsParserTreeConstants.JJTASSIGNMENTPA:
			method = "add";
			break;
		case PnutsParserTreeConstants.JJTASSIGNMENTPA | 0x8000:
			cf.add(Opcode.INVOKESTATIC, "pnuts.lang.Runtime", "add1",
					"(Ljava/lang/Object;Lpnuts/lang/Context;)",
					"Ljava/lang/Object;");
			return;
		case PnutsParserTreeConstants.JJTASSIGNMENTSA:
			method = "subtract";
			break;
		case PnutsParserTreeConstants.JJTASSIGNMENTSA | 0x8000:
			cf.add(Opcode.INVOKESTATIC, "pnuts.lang.Runtime", "subtract1",
					"(Ljava/lang/Object;Lpnuts/lang/Context;)",
					"Ljava/lang/Object;");
			return;
		case PnutsParserTreeConstants.JJTASSIGNMENTLA:
			method = "shiftLeft";
			break;
		case PnutsParserTreeConstants.JJTASSIGNMENTRA:
			method = "shiftRight";
			break;
		case PnutsParserTreeConstants.JJTASSIGNMENTRAA:
			method = "shiftArithmetic";
			break;
		case PnutsParserTreeConstants.JJTASSIGNMENTAA:
			method = "and";
			break;
		case PnutsParserTreeConstants.JJTASSIGNMENTEA:
			method = "xor";
			break;
		case PnutsParserTreeConstants.JJTASSIGNMENTOA:
			method = "or";
			break;
		default:
			throw new RuntimeException("never happen");
		}
		cf.add(Opcode.INVOKESTATIC, "pnuts.lang.Runtime", method,
				"(Ljava/lang/Object;Ljava/lang/Object;Lpnuts/lang/Context;)",
				"Ljava/lang/Object;");
	}

	/**
	 * Create a class loader to load compiled code.
	 */
	static CodeLoader createCodeLoader(ClassLoader loader, boolean privileged) {
		if (hasJava2Security && privileged) {
			return privilegedCodeLoaderFactory.create(loader);
		} else {
			return codeLoaderFactory.create(loader);
		}
	}

	public static CodeLoader createCodeLoader(ClassLoader loader) {
		return createCodeLoader(loader, false);
	}

	static class CodeLoaderFactory {
		public CodeLoader create(final ClassLoader parent) {
			ClassLoader cl = Pnuts.class.getClassLoader();
			if (cl != null) {
				if (parent != null && cl != parent) {
					return new CodeLoader(new MultiClassLoader(parent, cl));
				} else {
					return new CodeLoader(cl);
				}
			} else {
				return new CodeLoader(parent);
			}
		}
	}

	static class PrivilegedCodeLoaderFactory extends CodeLoaderFactory {
		public CodeLoader create(final ClassLoader parent) {
			return (CodeLoader) AccessController
					.doPrivileged(new PrivilegedAction() {
						public Object run() {
							ClassLoader cl = Pnuts.class.getClassLoader();
							if (cl != null) {
								if (parent != null && cl != parent) {
									return new CodeLoader(new MultiClassLoader(
											parent, cl));
								} else {
									return new CodeLoader(cl);
								}
							} else {
								return new CodeLoader(parent);
							}
						}
					});
		}
	}

	/**
	 * Compile a parsed expression. The resulting bytecode is handled by a
	 * ClassFileHandler.
	 */
	public Object compile(Pnuts pnuts, ClassFileHandler handler) {
		CompileContext cc = new CompileContext();
		return compile(pnuts, handler, cc);
	}

	Object compile(Pnuts pnuts, ClassFileHandler handler, CompileContext cc) {
		automatic = false;
		cc.scriptSource = pnuts.getScriptSource();

		pnuts.accept(this, cc);
		Object compileResult = handler.handle(cc.getClassFile());
		List classFiles = cc.getClassFiles();
		for (int i = 0, n = classFiles.size(); i < n; i++){
		    ClassFile cf = (ClassFile)classFiles.get(i);
		    handler.handle(cf);
		}
		return compileResult;
	}

	/**
	 * Compile a parsed expression.
	 * 
	 * @param pnuts
	 *            a parsed expression to be compiled
	 * @param context
	 *            a context in which the expression is compiled.
	 * @return a Pnuts object
	 */
	public Pnuts compile(Pnuts pnuts, final Context context) {
		Throwable caught = null;
		try {
			final CodeLoader loader = createCodeLoader(
					context.getClassLoader(), true);
			final CompileContext cc = new CompileContext(context);
			ClassFileHandler loadHandler = new ClassFileHandler() {
				public Object handle(ClassFile cf) {
					try {
						return cc.load(cf, loader);
					} catch (IOException e) {
						throw new PnutsException(e, context);
					}
				}
			};
			Class cls = (Class) compile(pnuts, loadHandler, cc);
			cc.resolve(loader);
			Runtime rt = (Runtime) cls.newInstance();

			return new CompiledScript(rt, pnuts);
		} catch (Throwable t) {
			caught = t;
		}
		Runtime.checkException(context, caught);
		throw new PnutsException(caught, context);
	}

	/**
	 * Compile an expression.
	 * 
	 * @param expression
	 *            an expression to be compiled.
	 * @return a Pnuts object.
	 */
	public Pnuts compile(String expression) {
		Context context = new Context();
		try {
			return compile(Pnuts.parse(expression), context);
		} catch (ParseException e) {
			Runtime.checkException(context, e);
			throw new PnutsException(e, context);
		}
	}

	/**
	 * Compile an expression.
	 * 
	 * @param expression
	 *            an expression to be compiled
	 * @param context
	 *            a context in which the expression is compiled.
	 * @return a Pnuts object.
	 */
	public Pnuts compile(String expression, Context context) {
		try {
			return compile(Pnuts.parse(expression), context);
		} catch (ParseException e) {
			Runtime.checkException(context, e);
			throw new PnutsException(e, context);
		}
	}

	/**
	 * Compile a function group. The resulting bytecode is handled by a
	 * ClassFileHandler.
	 * 
	 * @param pf
	 *            a function group.
	 * @param handler
	 *            the resulting bytecode is handle by this object.
	 * @return the result of handler.getResult() method.
	 */
	public Object compile(PnutsFunction pf, ClassFileHandler handler) {
		CompileContext cc = new CompileContext();
		PnutsFunction ret = null;
		StringBuffer buf = new StringBuffer();
		for (Enumeration e = getFunctions(pf); e.hasMoreElements();) {
			Function f = (Function) e.nextElement();
			if (f == null){
				continue;
			}
			int nargs = f.getNumberOfParameter();
			String im[] = f.getImportEnv();
			buf.append("import(null);");
			for (int i = 0; i < im.length; i++) {
				buf.append("import " + im[i] + ";");
			}
			buf.append(pf.unparse(nargs));
			buf.append('\n');
		}
		try {
			String expr = buf.toString();
			if (DEBUG) {
				System.out.println(expr);
			}
			return compile(Pnuts.parse(expr), handler, cc);
		} catch (ParseException pe) {
			throw new PnutsException(pe, cc);
		}
	}

	/**
	 * Compile a function group
	 * 
	 * @param pf
	 *            a function group to be compiled.
	 * @return a compiled version of the function group
	 */
	public PnutsFunction compile(PnutsFunction pf) {
		return compile(pf, new Context());
	}

	/**
	 * Compile a function group
	 * 
	 * @param pf
	 *            a function group to be compiled.
	 * @param context
	 *            a context in which the function is compiled.
	 * @return a compiled version of the function group
	 */
	public PnutsFunction compile(PnutsFunction pf, Context context) {
		StringBuffer buf = new StringBuffer();
		for (Enumeration e = getFunctions(pf); e.hasMoreElements();) {
			Function f = (Function) e.nextElement();
			if (f == null){ // function written in Java
				continue;
			}
			int nargs = f.getNumberOfParameter();
			String im[] = f.getImportEnv();
			buf.append("import(null);");
			for (int i = 0; i < im.length; i++) {
				buf.append("import " + im[i] + ";");
			}
			buf.append(pf.unparse(nargs));
			buf.append('\n');
		}
		Context c = (Context) context.clone();
		return (PnutsFunction) compile(buf.toString(), context).run(c);
	}

	/**
	 * Check if an object is compiled
	 * 
	 * @param obj
	 *            an object to be checked
	 * @return true if the object is compiled
	 */
	public static boolean isCompiled(Object obj) {
		if (obj instanceof Compiled) {
			return true;
		}
		if (obj instanceof PnutsFunction) {
			for (Enumeration e = getFunctions((PnutsFunction) obj); e
					.hasMoreElements();) {
				Function f = (Function) e.nextElement();
				if (!(f instanceof Compiled)) {
					return false;
				}
			}
			return true;
		}
		return false;
	}

	static String getClassName(SimpleNode node){
		int n = node.jjtGetNumChildren();
		if (n > 1){
		    StringBuffer sbuf = new StringBuffer();
		    sbuf.append(node.jjtGetChild(0).str);
		    for (int i = 1; i < n; i++) {
			sbuf.append('.');
			sbuf.append(node.jjtGetChild(i).str);
		    }
		    return sbuf.toString();
		} else {
		    return node.jjtGetChild(0).str;
		}
	}

	void resolveClassName(SimpleNode nameNode, CompileContext cc, int ctx) {
		ClassFile cf = cc.cf;
		int n = nameNode.jjtGetNumChildren();
		if (n == 1) {
			String sym = nameNode.jjtGetChild(0).str;
			if (sym == INT_SYMBOL) {
				cf.add(Opcode.GETSTATIC, "java.lang.Integer", "TYPE",
						"Ljava/lang/Class;");
				return;
			} else if (sym == SHORT_SYMBOL) {
				cf.add(Opcode.GETSTATIC, "java.lang.Short", "TYPE",
						"Ljava/lang/Class;");
				return;
			} else if (sym == CHAR_SYMBOL) {
				cf.add(Opcode.GETSTATIC, "java.lang.Character", "TYPE",
						"Ljava/lang/Class;");
				return;
			} else if (sym == BYTE_SYMBOL) {
				cf.add(Opcode.GETSTATIC, "java.lang.Byte", "TYPE",
						"Ljava/lang/Class;");
				return;
			} else if (sym == LONG_SYMBOL) {
				cf.add(Opcode.GETSTATIC, "java.lang.Long", "TYPE",
						"Ljava/lang/Class;");
				return;
			} else if (sym == FLOAT_SYMBOL) {
				cf.add(Opcode.GETSTATIC, "java.lang.Float", "TYPE",
						"Ljava/lang/Class;");
				return;
			} else if (sym == DOUBLE_SYMBOL) {
				cf.add(Opcode.GETSTATIC, "java.lang.Double", "TYPE",
						"Ljava/lang/Class;");
				return;
			} else if (sym == BOOLEAN_SYMBOL) {
				cf.add(Opcode.GETSTATIC, "java.lang.Boolean", "TYPE",
						"Ljava/lang/Class;");
				return;
			} else if (sym == VOID_SYMBOL) {
				cf.add(Opcode.GETSTATIC, "java.lang.Void", "TYPE",
						"Ljava/lang/Class;");
				return;
			}
		}

		StringBuffer sbuf = new StringBuffer();
		sbuf.append(nameNode.jjtGetChild(0).str);
		for (int i = 1; i < n; i++) {
			sbuf.append('.');
			sbuf.append(nameNode.jjtGetChild(i).str);
		}
		String className = sbuf.toString();
		Label nonnull = cf.getLabel();
		int value = cf.getLocal();

		cf.loadLocal(ctx);
		int index = cf.getLocal();
		cf.add(Opcode.LDC, cf.addConstant(className));
		cf.storeLocal(index);
		cf.loadLocal(index);
		cf.add(Opcode.INVOKEVIRTUAL, "pnuts.lang.Context", "resolveClass",
				"(Ljava/lang/String;)", "Ljava/lang/Class;");

		cf.storeLocal(value);
		cf.loadLocal(value);
		cf.add(Opcode.IFNONNULL, nonnull);
		error(cf, "class.notFound", new int[] { index }, cc);
		nonnull.fix();
		cf.loadLocal(value);
		cf.freeLocal(value);
		cf.freeLocal(index);
	}

	void resolveType(SimpleNode typeNode, CompileContext cc, int ctx) {
		ClassFile cf = cc.cf;
		Class type = null;
		if (typeNode.id == PnutsParserTreeConstants.JJTARRAYTYPE) {
			SimpleNode n = typeNode;
			int count = 0;
			while (n != null && n.id == PnutsParserTreeConstants.JJTARRAYTYPE) {
				count++;
				n = n.jjtGetChild(0);
			}
			if (n != null && n.id != PnutsParserTreeConstants.JJTCLASSNAME) {
				throw new RuntimeException();
			}
			resolveClassName(n, cc, ctx);
			cf.pushInteger(count);
			cf.add(Opcode.INVOKESTATIC, "pnuts.lang.Runtime", "arrayType",
					"(Ljava/lang/Class;I)", "Ljava/lang/Class;");

		} else if (typeNode.id == PnutsParserTreeConstants.JJTCLASSNAME) {
			resolveClassName(typeNode, cc, ctx);
		} else {
			error(cf, "class.expected", cc);
		}
	}

	static void getRef(Reference ref, CompileContext cc){
		ref.get(cc.cf, cc.env.parent != null, cc.getContextIndex());
	}


	public ClassFile compileClassScript(SimpleNode classScriptNode,
					    Object scriptSource,
					    List helperClassFiles)
	{
		CompileContext cc = new CompileContext();
		cc.scriptSource = scriptSource;
		classScript(classScriptNode, cc);
		helperClassFiles.addAll(cc.getClassFiles());
		return cc.cf;
	}

	public Object compileClassScript(Reader reader, Object scriptSource, ClassFileHandler handler) 
	    throws ParseException, IOException
	{
	    ParseEnvironment env = DefaultParseEnv.getInstance(scriptSource);
	    SimpleNode node = new PnutsParser(reader).ClassScript(env);
	    CompileContext cc = new CompileContext();
	    cc.scriptSource = scriptSource;
	    automatic = false;
	    node.accept(this, cc);
	    Object compileResult = handler.handle(cc.getClassFile());
	    List classFiles = cc.getClassFiles();
	    for (int i = 0, n = classFiles.size(); i < n; i++){
		ClassFile cf = (ClassFile)classFiles.get(i);
		handler.handle(cf);
	    }
	    return compileResult;
	}

	public Object beanDef(SimpleNode node, Context context){
	    CompileContext cc = (CompileContext) context;
	    ClassFile cf = cc.cf;
	    cc.beanEnv = new BeanEnv(cc.beanEnv);
	    int tableIndex = cf.getLocal();
	    //cc.beanEnv.tableIndex = tableIndex;
	    cf.add(Opcode.NEW, "java.util.HashMap");
	    cf.add(Opcode.DUP);
	    cf.add(Opcode.INVOKESPECIAL, "java.util.HashMap", "<init>", "()", "V");
	    cf.storeLocal(tableIndex);

	    int ctx = cc.getContextIndex();
	    int conf = cf.getLocal();
	    cf.loadLocal(ctx);
	    cf.add(Opcode.INVOKEVIRTUAL, "pnuts.lang.Context", "getConfiguration", "()", "Lpnuts/lang/Configuration;");
	    cf.storeLocal(conf);
	    cf.loadLocal(conf);
	    cf.loadLocal(ctx);
	    SimpleNode nameNode = node.jjtGetChild(0);
	    resolveClassName(nameNode, cc, ctx);
	    cf.add(Opcode.GETSTATIC, className, "NO_PARAM", "[Ljava/lang/Object;");
	    cf.add(Opcode.ACONST_NULL);
	    cf.add(Opcode.INVOKEVIRTUAL,
		   "pnuts.lang.Configuration",
		   "callConstructor",
		   "(Lpnuts/lang/Context;Ljava/lang/Class;[Ljava/lang/Object;[Ljava/lang/Class;)",
		   "Ljava/lang/Object;");
	    int target = cf.getLocal();
	    cf.storeLocal(target);
	    for (int i = 1; i < node.jjtGetNumChildren(); i++){
		SimpleNode cn = node.jjtGetChild(i);
		String propertyName = cn.str;
		SimpleNode valueNode = cn.jjtGetChild(0);
		boolean bound = "::".equals(cn.info);
		if (bound){ /* experimental BIND feature */
		    List memberNodes = new ArrayList();
		    cn.info = memberNodes;
		    markMemberNodeInBeanDef(cn, target, tableIndex, valueNode);
		}
		cf.loadLocal(conf);
		cf.loadLocal(ctx);
		cf.loadLocal(target);
		cf.add(Opcode.LDC, cf.addConstant(propertyName));
		valueNode.accept(this, context);
		cf.add(Opcode.INVOKEVIRTUAL,
		   "pnuts.lang.Configuration",
		   "putField",
		   "(Lpnuts/lang/Context;Ljava/lang/Object;Ljava/lang/String;Ljava/lang/Object;)",
		   "V");
	    }
	    cf.loadLocal(tableIndex);
	    cf.loadLocal(ctx);
	    cf.add(Opcode.INVOKESTATIC,
		   "pnuts.lang.Runtime",
		   "setupPropertyChangeListeners", 
		   "(Ljava/util/Map;Lpnuts/lang/Context;)",
		   "V");
	    
	    cf.loadLocal(target);
	    cf.freeLocal(conf);
	    cf.freeLocal(target);
	    cc.beanEnv = cc.beanEnv.parent;
	    return null;
	}

    static void markMemberNodeInBeanDef(SimpleNode beanPropertyDef, int targetIndex, int tableIndex, SimpleNode node){
	if (node.id == PnutsParserTreeConstants.JJTMEMBERNODE){
	    node.info = new Object[]{new int[]{targetIndex, tableIndex}, beanPropertyDef};
	} else if (node.id != PnutsParserTreeConstants.JJTFUNCTIONSTATEMENT){
	    for (int i = 0; i < node.jjtGetNumChildren(); i++){
		markMemberNodeInBeanDef(beanPropertyDef, targetIndex, tableIndex, node.jjtGetChild(i));
	    }
	}
    }

}
