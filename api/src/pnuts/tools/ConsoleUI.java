package pnuts.tools;

/*
 * Abstract interface for Console UI
 */
public interface ConsoleUI {

	/**
	 * Displays an output from the scripting engine
	 */
	void append(String str);
        
        void close();
}
