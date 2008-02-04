
package org.pnuts.awt;

import java.awt.TextArea;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Output stream to a TextArea
 */
public class TextAreaOutputStream extends OutputStream {
	TextArea textArea;

	public TextAreaOutputStream(TextArea textArea){
		this.textArea = textArea;
	}

	public void write(int b) throws IOException {
		char a[] = { (char)b };
		String s = new String(a);
		textArea.append(s);
	}

	public void write(byte b[], int off, int len) throws IOException {
		String s = new String(b, off, len);
		textArea.append(s);
	}

	public void close() throws IOException {
		textArea = null;
	}
}
