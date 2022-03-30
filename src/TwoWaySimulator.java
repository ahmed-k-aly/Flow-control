// =============================================================================
// IMPORTS

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;
import java.util.Scanner;
import java.lang.InterruptedException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
// =============================================================================



// =============================================================================
/**
 * A simulator that has both hosts send _and_ receive simultaneously.
 *
 * @file   TwoWaySimulator.java
 * @author Scott F. Kaplan (sfkaplan@cs.amherst.edu)
 * @date   April 2020
 */
public class TwoWaySimulator {
// =============================================================================


    // =========================================================================
    /**
     * The entry point.  Interpret the command-line arguments, aborting if they
     * are invalid.  Set up the layers and start the simulation.
     *
     * @param args The command-line arguments.
     */
    public static void main (String[] args) {

	// Check the number of arguments passed.
	if (args.length != 4) {

	    System.err.println("Usage: java TwoWaySimulator "  +
			       "<medium type> "                +
			       "<data link layer type> "       +
			       "<transmission data file A> "   +
			       "<transmission data file B>"
			       );
	    System.exit(1);

	}

	// Assign names to the arguments.
	String mediumType        = args[0];
	String dataLinkLayerType = args[1];
	String transmissionPathA = args[2];
	String transmissionPathB = args[3];

	// Create the medium, then the sender and receiver.
	Medium medium = Medium.create(mediumType);
	Host   hostA  = new Host(medium, dataLinkLayerType);
	Host   hostB  = new Host(medium, dataLinkLayerType);

	// Read the contents of the data to be transmitted into a buffer.
	byte[] dataToTransmitA = readFile(transmissionPathA);
	byte[] dataToTransmitB = readFile(transmissionPathB);

	// Perform the simulation!
	simulate(hostA, hostB, dataToTransmitA, dataToTransmitB);

    } // main
    // =========================================================================



    // =========================================================================
    /**
     * Read the whole contents of a given file, returning it in a byte array.
     *
     * @param path The pathname of the file whose data to read.
     * @return a buffer contain the complete contents of the file.
     */
    private static byte[] readFile (String path) {

	// Does the path name a readable file?
	File file = new File(path);
	if (!file.canRead()) {
	    throw new RuntimeException(path + " is not a readable file");
	}

	// Read the entire file.
	if (file.length() > Integer.MAX_VALUE) {
	    throw new RuntimeException(path + " is too large a file");
	}
	int             length = (int)file.length();
	byte[]          buffer = new byte[length];
	try {
	    FileInputStream input  = new FileInputStream(file);
	    input.read(buffer);
	} catch (FileNotFoundException e) {
	    throw new RuntimeException("Unexpected file-not-found for " + path);
	} catch (IOException e) {
	    throw new RuntimeException("Unexpected failure in reading " + path);
	}

	return buffer;
	
    } // readFile()
    // =========================================================================



    // =========================================================================
    /**
     * Perform the simulation, having the sender transmit the given data to the
     * receiver.  Verify that the receiver fully receives the complete and
     * correct data.
     *
     * @param hostA The first host.
     * @param hostB The second host.
     * @param dataA The data that A will send to B.
     * @param dataB The data that B will send to A.
     */
    private static void simulate (Host hostA, Host hostB, byte[] dataA, byte[] dataB) {

        // Create the hosts as independent threads to perform communications.
        new Thread(hostA).start();
        new Thread(hostB).start();

        // Provide the data to send to the sender.
	hostA.send(dataA);
	hostB.send(dataB);

	System.out.printf("Pausing...");
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {}
	System.out.printf("done.\n");
	byte[] receivedA = hostA.retrieve();
	byte[] receivedB = hostB.retrieve();

	System.out.println("Transmission from A to B received:");
	System.out.println(new String(receivedB));
        if (Arrays.equals(dataA, receivedB)) {
            System.out.println("Transmission match");
        } else {
            System.out.println("Transmission mismatch");
            System.out.printf("\tsent length = %d\treceived length = %d\n",
                              dataA.length,
                              receivedB.length);
        }

	System.out.println();
	System.out.println("Transmission from B to A received:");
	System.out.println(new String(receivedA));
        if (Arrays.equals(dataB, receivedA)) {
            System.out.println("Transmission match");
        } else {
            System.out.println("Transmission mismatch");
            System.out.printf("\tsent length = %d\treceived length = %d\n",
                              dataB.length,
                              receivedA.length);
        }

        hostA.stop();
        hostB.stop();

    } // simulate()
    // =========================================================================



// =============================================================================
} // class TwoWaySimulator
// =============================================================================
