package org.pnuts.tools;

import pnuts.lang.Context;
import pnuts.lang.PnutsFunction;
import pnuts.tools.PnutsConsole;
import pnuts.tools.PnutsConsoleUI;

public class console extends PnutsFunction {
	transient Thread th;

	public console() {
		super("console");
	}

	public boolean defined(int nargs) {
		return nargs == 0;
	}

	protected Object exec(Object[] args, Context context) {
		if (args.length != 0) {
			undefined(args, context);
			return null;
		}
		Context ctx = new Context(context);
                PnutsConsoleUI ui = new PnutsConsoleUI();
                PnutsConsole console = ui.createConsole(null, ctx, null, null, true, null, Thread.NORM_PRIORITY);
		ui.getFrame().setVisible(true);
                console.start();
		return null;
	}
}
