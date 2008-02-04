package org.pnuts.util;

/**
 * Common interface for cache
 */
public interface Cache {

	/**
	 * If key is in the cache it returns value, otherwise null.
	 * 
	 * @param key
	 *            the key
	 */
	public Object get(Object key);

	/**
	 * Register key and its value into the cache.
	 * 
	 * @param key
	 *            the key
	 * @param value
	 *            the value for the key
	 * @return the old value
	 */
	public Object put(Object key, Object value);

	/**
	 * Clear all cached data
	 */
	public void reset();
}