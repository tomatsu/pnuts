package org.pnuts.text;

import java.io.*;

public interface LineProcessor {
	public int processAll(boolean includeNewLine) throws IOException;
}
