package org.pnuts.multithread;

/**
 * A simple queue implementation
 */
public class Queue {

	private Cell head;
	private Cell tail;

	static class Cell {
		Object value;
		Cell next;

		Cell(Object value){
			this.value = value;
		}
	}

	public void enqueue(Object value){
		Cell c = new Cell(value);
		synchronized (this){
			if (head == null){
				head = c;
				tail = c;
				notify();
			} else {
				tail.next = c;
				tail = c;
			}
		}
	}

	public boolean isEmpty(){
		return head == null;
	}

	public synchronized Object dequeue() throws InterruptedException {
		return dequeue(-1);
	}

	public synchronized Object dequeue(long timeout) throws InterruptedException {
		Object ret = null;

		if (timeout > 0){
			if (head == null){
				wait(timeout);
			}
		} else if (timeout < 0){
			while (head == null){
				wait();
			}
		}
		if (head != null){
			ret = head.value;
			this.head = head.next;
		}
		return ret;
	}
}
