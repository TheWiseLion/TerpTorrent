package NetBase;

/***
 * This interface provides the a function
 * to be run at the end of the doWork()
 * cycle of a managed connection. 
 * Unused. thought it might come in handy :-/
 * @author wiselion
 */
public interface ActiveCallback {
	/**
	 * Run at the end of the do work cycle.
	 * This gets passed a reference from the connection that it's
	 * being run from.
	 * 
	 * This also garentee's that the connection is connected.
	 */
	public void run(ManagedConnection mc);
}
