// =============================================================================
// IMPORTS

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Iterator;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
// =============================================================================



// =============================================================================
/**
 * @file   DataLinkLayer.java
 * @author Scott F. Kaplan (sfkaplan@cs.amherst.edu)
 * @date   March 2020.
 *
 * A data link layer accepts a string of bytes, divides it into frames, adds
 * some metadata, and sends the frame via its physical layer.  Upon receiving a
 * frame, the data link layer removes the metadata, potentially performs some
 * checks on the data, and delivers the data to its client network layer.
 */
public abstract class DataLinkLayer {
// =============================================================================



    // =========================================================================
    // PUBLIC METHODS
    // =========================================================================



    // =========================================================================
    /**
     * Create the requested data link layer type and return it.
     *
     * @param  type          The subclass of which to create an instance.
     * @param  physicalLayer The physical layer by which to communicate.
     * @param  host          The host for which this layer is communicating.
     * @return The newly created data link layer.
     * @throws RuntimeException if the given type is not a valid subclass, or if
     *                          the given physical layer doesn't exist (is
     *                          <code>null</code>).
     */
    public static DataLinkLayer create (String        type,
					PhysicalLayer physicalLayer,
					Host          host) {

	if (physicalLayer == null) {
	    throw new RuntimeException("Null physical layer");
	}
	
	// Look up the class by name.
	String className           = type + "DataLinkLayer";
	Class  dataLinkLayerClass  = null;
	try {
	    dataLinkLayerClass = Class.forName(className);
	} catch (ClassNotFoundException e) {
	    throw new RuntimeException("Unknown data link layer subclass " +
				       className);
	}

	// Make one of these objects, and then see if it really is a
	// DataLinkLayer subclass.
	Object o = null;
	try {
	    o = dataLinkLayerClass.newInstance();
	} catch (InstantiationException e) {
	    throw new RuntimeException("Could not instantiate " + className);
	} catch (IllegalAccessException e) {
	    throw new RuntimeException("Could not access " + className);
	}
	DataLinkLayer dataLinkLayer = null;
	try {
	    dataLinkLayer = (DataLinkLayer)o;
	} catch (ClassCastException e) {
	    throw new RuntimeException(className +
				       " is not a subclass of DataLinkLayer");
	}


	// Register this new data link layer with the physical layer.
	dataLinkLayer.physicalLayer = physicalLayer;
	physicalLayer.register(dataLinkLayer);
	dataLinkLayer.register(host);
	
	return dataLinkLayer;

    } // create ()
    // =========================================================================



    // =========================================================================
    /**
     * Default constructor.  Set up the buffers for sending and receiving.
     */
    public DataLinkLayer () {

	// Create incoming buffer space.
	bitBuffer     = new LinkedList<Boolean>();
	receiveBuffer = new LinkedList<Byte>();
	sendBuffer    = new ConcurrentLinkedQueue<Byte>();
        
    } // DataLinkLayer ()
    // =========================================================================
    



    // =========================================================================
    /**
     * Allow a host to register as the client of this data link layer.
     *
     * @param client The host client of this data link layer.
     */
    public void register (Host client) {

	// Is there already a client registered?
	if (this.client != null) {
	    throw new RuntimeException("Attempt to double-register");
	}

	// Hold a pointer to the client.
	this.client = client;

    } // register ()
    // =========================================================================



    // =========================================================================
    /**
     * The event loop.  If there is buffered data to send, frame and transmit
     * it; if bits are received, process and deliver them one frame at a time.
     */
    public void go () {

        // Event loop.
        doEventLoop = true;
        while (doEventLoop) {

            // If there is buffered data to send, then frame and send it.
            if (sendBuffer.peek() != null) {
                Queue<Byte> framedData = sendNextFrame();
                if (framedData != null) {
                    finishFrameSend(framedData);
                }
            }

            // If there are received buffered bits, process them.
            receive();

            // If there are received buffered bytes, try to process a frame.
            if (receiveBuffer.peek() != null) {
                Queue<Byte> receivedFrame = processFrame();
                if (receivedFrame != null) {
                    finishFrameReceive(receivedFrame);
                }
            }

	    // Check whether a timeout action needs to be taken.
	    checkTimeout();

        } // Event loop

    } // go ()
    // =========================================================================



    // =========================================================================
    /**
     * End the event loop.
     */
    public void stop () {

        doEventLoop = false;

    } // stop ()
    // =========================================================================
    


    // =========================================================================
    /**
     * Send a sequence of bytes through the physical layer.  Expected to be
     * called by the client.  Buffers the data; actual sending is triggered by
     * the event loop.
     *
     * @param data The sequence of bytes to send.
     * @see   go()
     */
    public void send (byte[] data) {

	// Add each byte to the sending buffer.
	for (int i = 0; data != null && i < data.length; i += 1) {
	    sendBuffer.add(data[i]);
	}
	
    }
    // =========================================================================



    // =========================================================================
    /**
     * Extract the next frame-worth of data from the sending buffer, frame it,
     * and then send it.
     *
     * @return the frame of bytes transmitted.
     */
    protected Queue<Byte> sendNextFrame () {

        if (sendBuffer.isEmpty()) {
            return null;
        }
        
	// Extract a frame-worth of data from the sending buffer.
	int frameSize = ((sendBuffer.size() < MAX_FRAME_SIZE)
			 ? sendBuffer.size()
			 : MAX_FRAME_SIZE);
	Queue<Byte> data = new LinkedList<Byte>();
	for (int j = 0; j < frameSize; j += 1) {
	    data.add(sendBuffer.remove());
	}

	// Create a frame from the data and transmit it.
	Queue<Byte> framedData = createFrame(data);
	transmit(framedData);

        return framedData;

    } // sendNextFrame ()
    // =========================================================================



    // =========================================================================
    /**
     * Transmit a sequence of bytes as bits.
     *
     * @param data The sequence of bytes to send.
     */
    protected void transmit (Queue<Byte> data) {

	for (byte b : data) {

	    // Transmit one bit at a time, most to least significant.
	    for (int i = Byte.SIZE - 1; i >= 0; i -= 1) {

		// Grab the current bit...
		boolean bit = ((1 << i) & b) != 0;

		// ...and send it.
		physicalLayer.send(bit);

	    }

	}

    } // transmit ()
    // =========================================================================



    // =========================================================================
    /**
     * Deliver a bit into this layer.  Expected to be called by the physical
     * layer.  Accumulate bits into a buffer, and with each full byte received,
     * accumulate those bits into a byte buffer.  Each byte added to the buffer
     * is examined to determine whether a whole frame has been received, and if
     * so, then processed.
     *
     * @param bit The value to receive, where <code>false</code> indicates a
     *            <code>0</code>, and <code>true</code> indicates a
     *            <code>1</code>.
     */
    public void receive () {

        // Transfer any available bits in the physical layer into our buffer.
        Boolean bit = null;
        while ((bit = physicalLayer.retrieve()) != null) {
            bitBuffer.add(bit);
        }

	// If there are whole bytes of bits buffered, then transfer them to the
	// byte buffer.
	while (bitBuffer.size() >= Byte.SIZE) {

	    // Build up one byte from the bits...
	    byte newByte = 0;
	    for (int i = 0; i < Byte.SIZE; i += 1) {
		bit = bitBuffer.remove();
		newByte = (byte)((newByte << 1) | (bit ? 1 : 0));
	    }

	    // ...and add it to the byte buffer.
	    receiveBuffer.add(newByte);
	    if (debug) {
		System.out.printf("DataLinkLayer.receive(): Got new byte = %c\n",
				  newByte);
	    }

	}

    } // receive ()
    // =========================================================================



    // =========================================================================
    /**
     * Embed a raw sequence of bytes into a framed sequence.
     *
     * @param  data The raw sequence of bytes to be framed.
     * @return A complete frame.
     */
    abstract protected Queue<Byte> createFrame (Queue<Byte> data);
    // =========================================================================



    // =========================================================================
    /**
     * Determine whether the byte buffer contains a complete frame.  If so,
     * extract its contents, removing all metadata and (if applicable) checking
     * its correctness, then returning (if possible) the contained data.
     *
     * @return if possible, the extracted data from the frame; <code>null</code>
     *         otherwise.
     */
    abstract protected Queue<Byte> processFrame ();
    // =========================================================================



    // =========================================================================
    /**
     * After sending a frame, do any bookkeeping (e.g., buffer the frame in case
     * a resend is required).
     *
     * @param frame The framed data that was transmitted.
     */ 
    abstract protected void finishFrameSend (Queue<Byte> frame);
    // =========================================================================



    // =========================================================================
    /**
     * After receiving a frame, do any bookkeeping (e.g., deliver the frame to
     * the client, if appropriate) and responding (e.g., send an
     * acknowledgment).
     *
     * @param frame The frame of bytes received.
     */
    abstract protected void finishFrameReceive (Queue<Byte> frame);
    // =========================================================================



    // =========================================================================
    /**
     * Determine whether a timeout should occur and be processed.  This method
     * is called regularly in the event loop, and should check whether too much
     * time has passed since some kind of response is expected.
     */
    abstract protected void checkTimeout ();
    // =========================================================================
    


    // =========================================================================
    // INSTANCE DATA MEMBERS

    /** The physical layer used by this layer. */
    protected PhysicalLayer  physicalLayer;

    /** The host that is using this layer. */
    protected Host           client;

    /** The buffer of bits recently received, building up the current byte. */
    protected Queue<Boolean> bitBuffer;

    /** The buffer of bytes recently received, building up the current frame. */
    protected Queue<Byte>    receiveBuffer;

    /** The buffer of data yet to be sent. */
    protected Queue<Byte>    sendBuffer;

    /** Whether to continue the event loop. */
    private   boolean        doEventLoop;
    // =========================================================================



    // =========================================================================
    // CLASS DATA MEMBERS

    /** The maximum number of original data bytes that a frame may contain. */
    public static final int     MAX_FRAME_SIZE   = 8;

    /** Whether to emit debugging information. */
    public static final boolean debug            = false;
    // =========================================================================


    
// =============================================================================
} // class DataLinkLayer
// =============================================================================
