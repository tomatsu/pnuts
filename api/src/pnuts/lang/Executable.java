package pnuts.lang;

/**
 * Common interface for executable objects
 * 
 * Objects that represents parsed/compiled scripts implement this interface, so
 * that they can be executed by calling run(Context) method.
 */
public interface Executable {

	/**
	 * Executes the executable object;
	 * 
	 * @param context
	 *            the context in which the script is executed
	 * @return the result of the execution
	 */
	public Object run(Context context);
}