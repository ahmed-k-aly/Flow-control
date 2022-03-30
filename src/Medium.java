// =============================================================================
// IMPORTS

import java.util.Collection;
import java.util.LinkedList;
import java.util.Queue;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
// =============================================================================



// =============================================================================
/**
 * A medium carries bits from one physical layer to others.
 *
 * @file   Medium.java
 * @author Scott F. Kaplan (sfkaplan@cs.amherst.edu)
 * @date   September 2018, original September 2004
 */
public abstract class Medium {
// =============================================================================



    // =========================================================================
    // PUBLIC METHODS
    // =========================================================================



    // =========================================================================
    // Create the requested medium type and return it.
    public static Medium create (String type) {

	// Look up the class by name.
	String className   = type + "Medium";
	Class  mediumClass = null;
	try {
	    mediumClass = Class.forName(className);
	} catch (ClassNotFoundException e) {
	    throw new RuntimeException("Unknown medium subclass " + className);
	}

	// Make a className object, and then see if it really is a
	// Medium subclass.
	Object o = null;
	try {
	    o = mediumClass.newInstance();
	} catch (InstantiationException e) {
	    throw new RuntimeException("Could not instantiate " + className);
	} catch (IllegalAccessException e) {
	    throw new RuntimeException("Could not access " + className);
	}
	Medium medium = null;
	try {
	    medium = (Medium)o;
	} catch (ClassCastException e) {
	    throw new RuntimeException(className +
				       " is not a subclass of Medium");
	}

	return medium;

    } // create ()
    // =========================================================================



    // =========================================================================
    public Medium () {

	clients = new LinkedList<PhysicalLayer>();

    } // Medium ()
    // =========================================================================



    // =========================================================================
    /**
     * Register the given client as connected to the medium.  If the client is
     * already registered, do nothing (no multiple registrations).
     *
     * @param client The physical layer of a stack to connect to this medium.
     */
    public void register (PhysicalLayer client) {

	// Only add this client if it is not already registered.
	if (!clients.contains(client)) {
	    clients.add(client);
	}

    } // register ()
    // =========================================================================



    // =========================================================================
    // Send a bit from one physical layer to others.
    abstract public void transmit (PhysicalLayer sender, boolean bit);
    // =========================================================================



    // =========================================================================
    // DATA MEMBERS

    /** The physical layer clients connected to the medium. */
    protected Collection<PhysicalLayer> clients;    

    /** Whether to emit debugging information. */
    protected static final boolean debug = false;
    // =========================================================================
    


// =============================================================================
} // class Medium
// =============================================================================
