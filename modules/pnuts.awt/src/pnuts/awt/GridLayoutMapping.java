package pnuts.awt;

import java.awt.*;

/**
 * GridLayout mapping of <a href="/pnuts/doc/hierarchicalLayout.html">Hierarchical Layout</a>.
 *
 * @author Toyokazu Tomatsu
 * @author Nathan Sweet (misc@n4te.com)
 */
public class GridLayoutMapping extends Layout {

	public Container createContainer(Container container, Object[] format){
		if (!(format[1] instanceof Object[])){
			throw new LayoutException("Element after the GridLayout class must be an array of constructor arguments.");
		}
		Object args[] = (Object[])format[1];
		GridLayout lm = null;
		if (args.length == 0){
			lm = new GridLayout();
		} else if (args.length == 2){
			lm = new GridLayout(((Integer)args[0]).intValue(),
								((Integer)args[1]).intValue());
		} else if (args.length == 4){
			lm = new GridLayout(((Integer)args[0]).intValue(),
								((Integer)args[1]).intValue(),
								((Integer)args[2]).intValue(),
								((Integer)args[3]).intValue());
		}
		container.setLayout(lm);
		for (int i = 2; i < format.length; i++){
			Object a = format[i];
			if (a == null){
				container.add("", makePanel(container));
			} else if (isArray(a)){
				Object nestedArray[] = (Object[])format[i];
				if (nestedArray[0] instanceof Class)
					container.add("", Layout.layout(makePanel(container), (Object[])a));
				else if (nestedArray[0] instanceof Container) {
					if (!(nestedArray[1] instanceof Object[])) throw new LayoutException("Second element must be an array: " + nestedArray[1]);
					Layout.layout((Container)nestedArray[0], (Object[])nestedArray[1]);
				} else {
					throw new LayoutException("GridLayout requires an array element to start with a Class or Container: " + nestedArray[0]);
				}
			} else if (a instanceof Component){
				container.add("", (Component)a);
			} else{
				throw new LayoutException("GridLayout requires elements to be a Component, array, or null.");
			}
		}
		return container;
	}
}
