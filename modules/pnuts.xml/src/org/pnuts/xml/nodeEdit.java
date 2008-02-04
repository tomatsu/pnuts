package org.pnuts.xml;

import pnuts.lang.*;
import pnuts.xml.NodeEditingConfiguration;

/*
 * function nodeEdit({boolean})
 */
public class nodeEdit extends PnutsFunction {

	public nodeEdit(){
		super("nodeEdit");
	}

	public boolean defined(int nargs){
		return (nargs == 0 || nargs == 1);
	}

	protected Object exec(Object[] args, Context context){
		int nargs = args.length;
		Configuration current = context.getConfiguration();
		if (nargs == 0){
			return (current instanceof NodeEditingConfiguration) ? Boolean.TRUE : Boolean.FALSE;
		} else if (nargs == 1){
			boolean enabled = ((Boolean)args[0]).booleanValue();
			if (enabled){
				if (!(current instanceof NodeEditingConfiguration)){
					context.setConfiguration(new NodeEditingConfiguration(current));
				}
			} else {
				if (current instanceof NodeEditingConfiguration){
					context.setConfiguration(((NodeEditingConfiguration)current).getParent());
				}
			}
			return null;
		} else {
			undefined(args, context);
			return null;
		}
	}

	public String toString(){
		return "function nodeEdit( { boolean } )";
	}
}
