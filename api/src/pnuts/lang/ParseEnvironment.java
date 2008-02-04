package pnuts.lang;

/**
 * This class defines how to handle ParseException thrown by the parser. The
 * instances can be passed to Pnuts.parse(..) method in order to customize the
 * way of error recovery of parsing.
 */
public interface ParseEnvironment {

	/**
	 * Thie method defines how to deal with parse errors
	 * 
	 * @param e
	 *            a ParseException object passed by the parser
	 * @exception ParseException
	 *                this method may rethrow the ParseException
	 */
	void handleParseException(ParseException e) throws ParseException;
}