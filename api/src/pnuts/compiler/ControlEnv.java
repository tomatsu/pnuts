package pnuts.compiler;

import pnuts.lang.PnutsParserTreeConstants;
import java.util.Stack;

class ControlEnv {
	Label breakLabel;
	Label continueLabel;
	Stack finallyBlocks;
	ControlEnv parent;

	ControlEnv(int id, ControlEnv parent) {
		this.parent = parent;
		if (parent != null) {
			finallyBlocks = parent.finallyBlocks;
			if (id == PnutsParserTreeConstants.JJTSWITCHSTATEMENT) {
				continueLabel = parent.continueLabel;
			}
		} else {
			finallyBlocks = new Stack();
		}
	}

	void pushFinallyBlock(Label label) {
		finallyBlocks.push(label);
	}

	Label popFinallyBlock() {
		if (finallyBlocks.size() > 0) {
			return (Label) finallyBlocks.pop();
		} else {
			return null;
		}
	}
}
