package pnuts.tools;

import pnuts.lang.Context;

public class VisualDebugger
	extends VisualDebuggerView
	implements Debugger, ContextFactory 
{
	public VisualDebugger() {
		VisualDebuggerModel model = new VisualDebuggerModel();
		model.setView(this);
		this.model = model;
	}

	public Context createContext(Context context) {
		DebugContext dc = new DebugContext(context);
		dc.addCommandListener(this);
		return dc;
	}

	public Context createContext() {
		DebugContext dc = new DebugContext();
		dc.addCommandListener(this);
		return dc;
	}

	public void setBreakPoint(Object source, int lineno) {
		model.setBreakPoint(source, lineno);
	}

	public void removeBreakPoint(Object source, int lineno) {
		model.removeBreakPoint(source, lineno);
	}

	public void clearBreakPoints() {
		model.clearBreakPoints();
	}

	public void signal(CommandEvent event) {
		model.signal(event);
	}

}