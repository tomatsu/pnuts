package pnuts.awt;

/**
 * Throws to indicate that there is an error in layout components
 *
 * @version	1.1
 * @author	Toyokazu Tomatsu
 */
public class LayoutException extends RuntimeException {

	public LayoutException(){
		this("");
	}

	public LayoutException(String message){
		super(message);
	}
}
