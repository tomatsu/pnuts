package pnuts.lang;

/**
 * A factory class for unit numbers. See <a
 * href="http://pnuts.org/doc/lang.html#units">The language specification </a>
 * for details.
 * 
 * @version 1.1
 */
public interface QuantityFactory {

	/**
	 * Defines how to create an object that represents a kind of quantity.
	 * 
	 * @param number
	 *            the amount
	 * @param unit
	 *            the unit
	 * @return the quantity
	 */
	Object make(Number number, String unit);
}