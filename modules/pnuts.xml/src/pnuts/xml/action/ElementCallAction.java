package pnuts.xml.action;

import java.util.*;
import pnuts.xml.*;

/**
 * This action calls an abstract method passing the keyword assigned to the path,
 * the element's attributes, and the text element.  A subclass defines a concrete
 * implementation.
 */
public abstract class ElementCallAction extends DigestAction {

	Stack stack = new Stack();

	public void start(String path, String key, Map attributeMap, Object top)
		throws Exception
		{
			stack.push(new HashMap(attributeMap));
		}

	public void end(String path, String key, String text, Object top)
		throws Exception
		{
			Map attributeMap = (Map)stack.pop();
			call(key, attributeMap, text);
	
		}

	protected abstract void call(String key, Map attributeMap, String text);
}
