package pnuts.lang;

class ImmutableBinding extends Binding {

	ImmutableBinding(int h, String name, Object v, Binding n) {
		super(h, name, v, n);
	}

	/**
	 * Always throws IllegalStateException
	 */
	public void set(Object value) {
		throw new IllegalStateException();
	}
}