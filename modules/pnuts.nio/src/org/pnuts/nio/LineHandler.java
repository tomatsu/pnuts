package org.pnuts.nio;

/**
 * An abstract interface for LineReader's callback
 */
public interface LineHandler extends org.pnuts.text.LineHandler {

	/**
	 * Processes the current line.
	 *
	 * @param bb the byte buffer that contains the current line
	 * @param offset the offset of the buffer
	 * @param length the length of the current line
	 */
	void process(byte[] bb, int offset, int length);
}
