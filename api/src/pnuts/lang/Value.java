package pnuts.lang;

/**
 * Objects of this class are returned by Package.lookup() method.
 * 
 * @see pnuts.lang.Package
 */
public interface Value {

	/**
	 * Gets the value.
	 */
	public Object get();
}