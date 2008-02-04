package pnuts.lang;

/**
 * This class is a special Exception class in a Pnuts runtime in that it's not
 * checked by exception handlers. This class is used to implement handled
 * exceptions by catch() function and return() function.
 */
public class Jump extends Escape {

	protected Jump() {
	}

	public Jump(Object value) {
		super(value);
	}
}