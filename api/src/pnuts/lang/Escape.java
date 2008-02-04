package pnuts.lang;

/**
 * This class is a special Exception class in a Pnuts runtime in that it's not
 * checked by exception handlers. This class is used to implement quit()
 * function.
 * 
 * @see pnuts.lang.Jump
 */
public class Escape extends RuntimeException {
	private Object value;

	protected Escape() {
	}

	protected Escape(Object value) {
		this.value = value;
	}

	public Object getValue() {
		return value;
	}
}