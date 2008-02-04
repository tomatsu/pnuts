package pnuts.lang;

/**
 * Index-access to an instance of this interface is interpreted as the set/get
 * method call, which are defined in the implementation class.
 * 
 * <pre>
 * 
 *  indexed[idx]          ==&gt; indexed.get(idx)
 *  indexed[idx] = value  ==&gt; indexed.set(idx, value)
 *  
 * </pre>
 */
public interface Indexed {

	/**
	 * Write access to the index
	 * 
	 * @param idx
	 *            the index
	 * @param value
	 *            the object to be assigned
	 */
	public void set(int idx, Object value);

	/**
	 * Read access to the index
	 * 
	 * @param idx
	 *            the index
	 */
	public Object get(int idx);
}