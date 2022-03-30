// =============================================================================
// IMPORTS

import java.util.Collection;
import java.util.Iterator;
// =============================================================================



// =============================================================================
/**
 * An ideal medium with no noise, errors, loss.
 *
 * @file   PerfectMedium.java
 * @author Scott F. Kaplan (sfkaplan@cs.amherst.edu)
 * @date   September 2018, original September 2004
 */
public class PerfectMedium extends Medium {
// =============================================================================



    // =========================================================================
    // PUBLIC METHODS
    // =========================================================================



    // =========================================================================
    /**
     * Send a bit from one client to the other clients.
     *
     * @param sender The client physical layer sending the bit.
     * @param bit The value to be sent, where <code>false</code> sends a
     *            <code>0</code> bit, and <code>true</code> sends a
     *            <code>1</code> bit.
     * @throws RuntimeException if the sender is not registered with this
     *                          medium.
     */
    public void transmit (PhysicalLayer sender, boolean bit) {

	// Only registered clients may send.
	if (!clients.contains(sender)) {
	    throw new RuntimeException("Unregistered sender on the medium");
	}
	
	// Deliver the bit to each client that is not the sender.
	Iterator<PhysicalLayer> clientIterator = clients.iterator();
	while (clientIterator.hasNext()) {
	    
	    PhysicalLayer receiver = clientIterator.next();
	    if (receiver != sender) {
		receiver.receive(bit);
	    }

	}

    } // transmit ()
    // =========================================================================



// =============================================================================
} // class PerfectMedium
// =============================================================================
