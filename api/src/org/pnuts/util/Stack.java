package org.pnuts.util;

/**
 * Stack implemented by a linked list
 */
public class Stack {
	Cell cell;

	int count = 0;

	public void push(Object object) {
		Cell old = cell;
		cell = new Cell();
		cell.object = object;
		cell.next = old;
		count++;
	}

	public Object pop() {
		Cell c = cell;
		cell = cell.next;
		count--;
		return c.object;
	}

	public int size() {
		return count;
	}

	public Object peek() {
		return cell.object;
	}

	public void removeAllElements() {
		cell = null;
		count = 0;
	}

	public void copyInto(Object[] array) {
		Cell c = cell;
		for (int i = 0; i < count; i++) {
			array[count - 1 - i] = c.object;
			c = c.next;
		}
	}
}