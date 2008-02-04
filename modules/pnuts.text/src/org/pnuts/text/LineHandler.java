package org.pnuts.text;

/**
 * An abstract interface for LineReader's callback
 */
public interface LineHandler {

	/**
	 * Processes the current line.
	 *
	 * @param cb the char buffer that contains the current line
	 * @param offset the offset of the buffer
	 * @param length the length of the current line
	 */
	void process(char[] cb, int offset, int length);
}
