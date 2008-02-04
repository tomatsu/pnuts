package pnuts.tools;

import pnuts.lang.Context;

/**
 * Factory for Context object
 * <p>
 * This interface is used by pnuts.tools.Main class to create the initial context.
 */
public interface ContextFactory {

	/**
	 * Create a context
	 */
	public Context createContext();
}
