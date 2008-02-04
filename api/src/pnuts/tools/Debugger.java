package pnuts.tools;

/**
 * An abstract interface for debugger
 */
public interface Debugger extends CommandListener {
	/**
	 * Sets a breakpoint
	 */
	public void setBreakPoint(Object source, int lineno);

	/**
	 * Remove a breakpoint
	 */
	public void removeBreakPoint(Object source, int lineno);

	/**
	 * Remove all breakpoints
	 */
	public void clearBreakPoints();
}