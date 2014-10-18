package Logic;

import java.io.IOException;

import TorrentData.Torrent;

/**
 * The purpose of this class is to make it easy
 * to interchange the logic used to sustain connections.
 * E.g. to easily go from pop
 * See Diagram: PeerLogic_Control_Flow.jgp
 * @author wiselion
 *
 */
public abstract class PeerLogic {

	/**
	 * At its essence this is all peer logic is
	 * We assume the PeerLogic will loop over all
	 * the managed connection and push and pull data
	 * as needed.
	 * @param input torrent
	 */
	public abstract void doWork(Torrent t) throws IOException;
	
	public abstract String getName();
}
