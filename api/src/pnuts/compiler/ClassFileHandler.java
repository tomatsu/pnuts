package pnuts.compiler;

/**
 * This interface defines an abstract interface to get a result of compilation.
 * This interface is used by the following methods.
 * <ul>
 * <li>Compiler.compile(Pnuts, ClassFileHandler)
 * <li>Compiler.compile(String, ClassFileHandler)
 * <li>Compiler.compile(PnutsFunction, ClassFileHandler)
 * </ul>
 */
public interface ClassFileHandler {

	/**
	 * This method is called with each compiled class file when
	 * Compiler.compile(Pnuts, ClassFileHandler) method is called. The first
	 * class file is supposed to be of pnuts.lang.Executable subclass. The
	 * compiled code can be executed with Executable.run(Context) method.
	 */
	public Object handle(ClassFile cf);
}