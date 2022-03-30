// =============================================================================
// IMPORTS

import java.util.Iterator;
// =============================================================================



// =============================================================================
/**
 * A medium that occassionally flips a bit.
 *
 * @file   LowNoiseMedium.java
 * @author Scott F. Kaplan (sfkaplan@cs.amherst.edu)
 * @date   September 2018, original September 2004
 */
public class LowNoiseMedium extends Medium {
// =============================================================================



    // =========================================================================
    // PUBLIC METHODS
    // =========================================================================



    // =========================================================================
    /**
     * Send a bit from one client to the other clients.  With some probability,
     * flip a bit.
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
	    
	    // With low probability, flip this bit.
	    if (Math.random() < errorProbability) {
		if (debug) {
		    System.out.println("LowNoiseMedium.transmit(): Flipped bit!");
		}
		bit = !bit;
	    }

	    PhysicalLayer receiver = clientIterator.next();
	    if (receiver != sender) {
		receiver.receive(bit);
	    }

	}

    } // transmit ()
    // =========================================================================



    // =========================================================================
    // DATA MEMBERS

    // The probablity that a bit will flip.
    private static final double errorProbability = 0.001;
    // =========================================================================



// =============================================================================
} // class LowNoiseMedium
// =============================================================================
