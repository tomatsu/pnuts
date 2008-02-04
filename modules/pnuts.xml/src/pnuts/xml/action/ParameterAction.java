package pnuts.xml.action;

import pnuts.xml.DigestAction;

/**
 * This action pushes the content of an element onto the stack, which will be poped from the stack
 * by a <a href="CallAction.html">CallAction</a>.
 */
public class ParameterAction extends DigestAction {

	public void end(String path, String key, String text, Object top)
		throws Exception
		{
			push(getStackTopPath(), new Parameter(key, text));
		}
}


class Parameter {
	String name;
	Object value;

	Parameter(String name, Object value){
		this.name = name;
		this.value = value;
	}
}
