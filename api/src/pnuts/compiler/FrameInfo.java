package pnuts.compiler;

import java.util.Set;

class FrameInfo {
	boolean isLeaf;
	boolean leafCheckDone;
	boolean preprocessed;
	Set freeVars;
}
