package pnuts.tools;


/**
 * @see pnuts.tools.DebugContext
 */
public interface CommandListener {
    /**
     * Some kind of event raised, e.g. the line number has changed.
     */
    void signal(CommandEvent event);
}
