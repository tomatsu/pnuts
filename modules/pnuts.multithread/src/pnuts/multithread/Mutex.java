package pnuts.multithread;

/**
 * A simple mutex class
 *
 * @author	Toyokazu Tomatsu
 * @version	1.1
 */
public class Mutex {
	private boolean locked = false;
	private boolean priv;
	Thread owner;

	/**
	 * create mutex object
	 */
	public Mutex(){
		this(false);
	}

	/**
	 * create mutex object
	 * @param priv   If true a lock belongs to one owner otherwise not.
	 */
	public Mutex(boolean priv){
		this.priv = priv;
	}

	/**
	 * Lock it
	 * Be careful not to deadlock.
	 */
	public synchronized void lock() throws InterruptedException {
		while (locked){
			wait();
		}
		locked = true;
		if (priv){
			owner = Thread.currentThread();
		}
	}

	/**
	 * Unlock it
	 */
	public synchronized void unlock(){
		if (!priv || Thread.currentThread() == owner){
			locked = false;
			notifyAll();
		}
	}
}
