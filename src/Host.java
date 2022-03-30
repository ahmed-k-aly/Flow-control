// =============================================================================
// IMPORTS

import java.util.Queue;
import java.util.LinkedList;
// =============================================================================



// =============================================================================
/**
 * A single host, comprising a single network stack, connected to a medium.
 *
 * @file   Host.java
 * @author Scott F. Kaplan (sfkaplan@cs.amherst.edu)
 * @date   March 2020
 */
public class Host implements Runnable {
// =============================================================================


    
    // =========================================================================
    // PUBLIC METHODS
    // =========================================================================



    // =========================================================================
    public Host (Medium medium, String dataLinkLayerType) {

	this.medium        = medium;
	this.physicalLayer = PhysicalLayer.create(medium);
	this.dataLinkLayer = DataLinkLayer.create(dataLinkLayerType,
						  this.physicalLayer,
						  this);

	this.buffer = new LinkedList<Byte>();

    } // Host ()
    // =========================================================================



    // =========================================================================
    /**
     * Begin this host as an independent thread.  The event loop in
     * its data link layer is started.
     */
    @Override
    public void run () {

        dataLinkLayer.go();
        
    } // run ()
    // =========================================================================



    // =========================================================================
    /**
     * End the event loop in the data link layer, thus ending this hosts' thread.
     */
    public void stop () {

        dataLinkLayer.stop();
        
    } // stop ()
    // =========================================================================
    


    // =========================================================================
    /**
     * Send a sequence of bytes.
     *
     * @param data The sequence of bytes to send.
     */
    public void send (byte[] data) {

	dataLinkLayer.send(data);
	
    } // send ()
    // =========================================================================



    // =========================================================================
    /**
     * Receive bytes from the lower layer.  Buffer those until they are
     * retrieved.
     *
     * @param data The data received and to be buffered.
     */
    public void receive (byte[] data) {

	// Add the bytes into the buffer.
	for (int i = 0; i < data.length; i += 1) {
	    buffer.add(data[i]);
	}
	
    } // receive ()
    // =========================================================================



    // =========================================================================
    /**
     * Retrieve and return any bytes that have been received and buffered.
     *
     * @return the buffered bytes.
     */
    public byte[] retrieve () {

	// Remove the bytes from the buffer, adding them to a newly formed array
	// to be returned.
	byte[] received = new byte[buffer.size()];
	for (int i = 0; i < received.length; i += 1) {
	    received[i] = buffer.remove();
	}

	return received;
	
    } // retrieve ()
    // =========================================================================
    


    // =========================================================================
    // DATA MEMBERS

    /** The medium to which this host is connect. */
    private Medium        medium;

    /** The physical layer in this host's network stack. */
    private PhysicalLayer physicalLayer;

    /** The data link layer in this host's network stack. */
    private DataLinkLayer dataLinkLayer;

    /** The buffered bytes received via the network stack. */
    private Queue<Byte>   buffer;

    /** Whether to emit debugging information. */
    private static final boolean debug = false;
    // =========================================================================

    

// =============================================================================
} // class Host
// =============================================================================
