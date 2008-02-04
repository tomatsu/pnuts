package pnuts.lang;

/**
 * Objects of this class are returned by Package.lookup() method.
 */
public interface NamedValue extends Value {

	/**
	 * Gets the name of the value.
	 * 
	 * @return the intern()'d String that identifies the value
	 */
	public String getName();


	/**
	 * Sets the value
	 *
	 * @param obj the new value
	 */
	public void set(Object obj);
}
