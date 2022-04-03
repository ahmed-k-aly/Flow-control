// =============================================================================
// IMPORTS

import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;
// =============================================================================
import java.util.Timer;
import java.util.logging.Logger;
import java.util.logging.Level;

// =============================================================================
/**
 * @file ParityDataLinkLayer.java
 * @author Scott F. Kaplan (sfkaplan@cs.amherst.edu)
 * @date February 2020
 *
 *       A data link layer that uses start/stop tags and byte packing to frame
 *       the
 *       data, and that performs error management with a parity bit. It employs
 *       no
 *       flow control; damaged frames are dropped.
 */
public class PARDataLinkLayer extends DataLinkLayer {
	// =============================================================================

	// =========================================================================
	// DATA MEMBERS

	/** The start tag. */
	private final byte startTag = (byte) '{';

	/** The stop tag. */
	private final byte stopTag = (byte) '}';

	/** The escape tag. */
	private final byte escapeTag = (byte) '\\';

	/** The Acknowledgment tag */
	private final byte acknowledgmentTag = (byte) 6;

	private final int TIMEOUT_INTERVAL_MS = 100;

	/** signals if we created the sender class yet or not. */
	private Sender sender = new Sender();
	/** signals if we created the receiver class yet or not. */
	private Receiver receiver = new Receiver();

	/** creates a volatile logger instance to be accessed by both threads. */
	private static volatile Logger LOGGER = Logger.getLogger(PARDataLinkLayer.class.getName());

	private static final boolean logging = false;
	// =========================================================================

	// =========================================================================
	/**
	 * Embed a raw sequence of bytes into a framed sequence.
	 *
	 * @param data The raw sequence of bytes to be framed.
	 * @return A complete frame.
	 */
	protected Queue<Byte> createFrame(Queue<Byte> data) {

		// Calculate the parity.
		
		// add the frame number as either zero or one to the data to 
		// be calculated in the parity.
		data.add((byte) (sender.currFrameNumber));
		byte parity = calculateParity(data);

		// Begin with the start tag.
		Queue<Byte> framingData = new LinkedList<Byte>();
		framingData.add(startTag);

		// Add each byte of original data.
		for (byte currentByte : data) {

			// If the current data byte is itself a metadata tag, then precede
			// it with an escape tag.
			if ((currentByte == startTag) ||
					(currentByte == stopTag) ||
					(currentByte == escapeTag)) {

				framingData.add(escapeTag);

			}

			// Add the data byte itself.
			framingData.add(currentByte);

		}
		// Add the parity byte.
		framingData.add(parity);

		// End with a stop tag.
		framingData.add(stopTag);

		return framingData;

	} // createFrame ()
		// =========================================================================

	// =========================================================================
	/**
	 * Determine whether the received, buffered data constitutes a complete
	 * frame. If so, then remove the framing metadata and return the original
	 * data. Note that any data preceding an escaped start tag is assumed to be
	 * part of a damaged frame, and is thus discarded.
	 *
	 * @return If the buffer contains a complete frame, the extracted, original
	 *			data; <code>null</code> otherwise.
	 */
	protected Queue<Byte> processFrame() {
		// Log information on the current method call.
		LOGGER.entering(PARDataLinkLayer.class.getName(), new Throwable().getStackTrace()[0].getMethodName());

		// Search for a start tag. Discard anything prior to it.
		boolean startTagFound = false;
		Iterator<Byte> i = receiveBuffer.iterator();
		while (!startTagFound && i.hasNext()) {
			byte current = i.next();
			if (current != startTag) {
				i.remove();
			} else {
				startTagFound = true;
			}
		}

		// If there is no start tag, then there is no frame.
		if (!startTagFound) {
			return null;
		}

		// Try to extract data while waiting for an unescaped stop tag.
		int index = 1;
		LinkedList<Byte> extractedBytes = new LinkedList<Byte>();
		boolean stopTagFound = false;
		while (!stopTagFound && i.hasNext()) {

			// Grab the next byte. If it is...
			// (a) An escape tag: Skip over it and grab what follows as
			// literal data.
			// (b) A stop tag: Remove all processed bytes from the buffer and
			// end extraction.
			// (c) A start tag: All that precedes is damaged, so remove it
			// from the buffer and restart extraction.
			// (d) Otherwise: Take it as literal data.
			byte current = i.next();
			index += 1;
			if (current == escapeTag) {
				if (i.hasNext()) {
					current = i.next();
					index += 1;
					extractedBytes.add(current);
				} else {
					// An escape was the last byte available, so this is not a
					// complete frame.
					return null;
				}
			} else if (current == stopTag) {
				cleanBufferUpTo(index);
				stopTagFound = true;
			} else if (current == startTag) {
				cleanBufferUpTo(index - 1);
				index = 1;
				extractedBytes = new LinkedList<Byte>();
			} else {
				extractedBytes.add(current);
			}

		}

		// If there is no stop tag, then the frame is incomplete.
		if (!stopTagFound) {
			return null;
		}

		if (debug) {
			System.out.println("ParityDataLinkLayer.processFrame(): Got whole frame!");
		}
		LOGGER.fine("Whole Frame Processed.");

		// Sender POV if ack received.
			if (extractedBytes.size() == 1) { // True if the only byte sent is an acknowledgment.
			// This is because we have at least one parity byte + data for normal frames.
			Byte ackByte = extractedBytes.remove();
			if (ackByte != acknowledgmentTag) { // This should always be true
				LOGGER.warning("SENDER: Acknowledgement Tag bits flipped\n");
			}
			// * This assumes that if we get one byte only, then it is acknowledgment since we are not doing NACKs here 
			// signal that we received an acknowledgement
			sender.acknowledgmentReceived();
			LOGGER.fine("SENDER: Sender received acknowledgement");
			// do nothing.
			return null;
			}
		
		// The final byte inside the frame is the parity. Compare it to a
		// recalculation.
		byte receivedParity = extractedBytes.remove(extractedBytes.size() - 1);
		byte calculatedParity = calculateParity(extractedBytes);
		if (receivedParity != calculatedParity) {
			// FIXME:
			//! A BUG HAPPENS LEADING TO AN ENTIRE FRAME BEING DROPPED AND NOT RESENT 
			
			//System.out.printf("ParityDataLinkLayer.processFrame():\tDamaged frame\n");
			LOGGER.warning("RECEIVER: Damaged frame: " + extractedBytes);
			receiver.corruptedFrameReceived();
			return null;
		}

		// * puts the frame number as the first byte in the extracted bytes.
		byte frameNumber = extractedBytes.removeLast();
		extractedBytes.addFirst(frameNumber);
		// we received a non-damaged frame.
		receiver.frameReceived();
		return extractedBytes;

	} // processFrame ()
		// =========================================================================

	// =========================================================================
	/**
	 * Extract the next frame-worth of data from the sending buffer, frame it,
	 * and then send it.
	 *
	 * @return the frame of bytes transmitted.
	 */
	@Override
	protected Queue<Byte> sendNextFrame() {
		if (!logging){
			LOGGER.setLevel(Level.OFF);
		}
		// Log information on the current method call.
		LOGGER.entering(PARDataLinkLayer.class.getName(), new Throwable().getStackTrace()[0].getMethodName());
		
		if (!sender.confirmationReceived){
			// if we didn't receive a confirmation on the last frame
			// don't do anything.
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

	}

	// =========================================================================
	/**
	 * After sending a frame, do any bookkeeping (e.g., buffer the frame in case
	 * a resend is required).
	 *
	 * @param frame The framed data that was transmitted.
	 */
	protected void finishFrameSend(Queue<Byte> frame) {
		sender.frameSent(frame);
		// COMPLETE ME WITH FLOW CONTROL

	} // finishFrameSend ()
		// =========================================================================

	/**
	 * @brief Creates a new frame that has an acknowledgment byte.
	 *        It transmits the frame across the medium then. It also
	 *        updates the fields of receiver to reflect the acknowledgment.
	 */
	private void sendAcknowledgment() {
		Queue<Byte> framingData = new LinkedList<Byte>();
		// create frame.
		framingData.add(startTag);
		framingData.add(acknowledgmentTag);
		framingData.add(stopTag);
		// transmit frame.
		transmit(framingData);
		// updates the fields of receiver to reflect the acknowledgment
		receiver.acknowledgmentSent();

	}

	// =========================================================================
	/**
	 * After receiving a frame, do any bookkeeping (e.g., deliver the frame to
	 * the client, if appropriate) and responding (e.g., send an
	 * acknowledgment).
	 *
	 * @param frame The frame of bytes received.
	 */
	protected void finishFrameReceive (Queue<Byte> frame) {
		LOGGER.entering(PARDataLinkLayer.class.getName(), new Throwable().getStackTrace()[0].getMethodName());

		if (receiver.correctFrameReceived){
			LOGGER.info("Checking acknowledgement for received frame.");
			// True if we just received a frame in processFrame().

			receiver.correctFrameReceived = false; // we have not received the NEXT frame yet.
			// Retrieves the frame number from the queue's top.
			byte frameNumber = frame.remove();
			// check if the frame numbers match
			if (frameNumber == receiver.getFrameNumber()){
				// send ack.
				sendAcknowledgment();
			} else {
				// if frameNumbers mismatch, assume it is a duplicate
				LOGGER.fine("RECEIVER: FRAME NUMBER MISMATCH. " + "Sent Frame Number: " 
				+  frameNumber + " Receiver Frame number: " + receiver.getFrameNumber() + "\n");
				// return ack and wait for the next frame.
				sendAcknowledgment();

				// two frame number increments equal no change. 
				// see sendAcknowledgment().
				receiver.incrementFrameNumber();

				return;
			}
		} else{
			return;
		}
		// * if no frame is received, this is unreachable since
		// * processFrame() would return null and not enter this method.

		// Deliver frame to the client.
		byte[] deliverable = new byte[frame.size()];
		for (int i = 0; i < deliverable.length; i += 1) {
			deliverable[i] = frame.remove();
		}
		client.receive(deliverable);

	} // finishFrameReceive ()
		// =========================================================================

	// =========================================================================
	/**
	 * Determine whether a timeout should occur and be processed. This method
	 * is called regularly in the event loop, and should check whether too much
	 * time has passed since some kind of response is expected.
	 */
	protected void checkTimeout() {
		if (sender.confirmationReceived){
			// if we received a confirmation, no need
			// to check the timeout.
			return;
		}
		long timeDuration = sender.timerDuration();
		if (timeDuration > TIMEOUT_INTERVAL_MS) {
			LOGGER.fine("TIMEOUT OCCURED: " + timeDuration +"\n");
			// signal that a timeout has occurred if 100 milliseconds have passed since we
			// sent out message.
			//  resend the message.
			resendMessage();
			// message should be resent in sendNextFrame()
		}
	} // checkTimeout ()
		// =========================================================================


	/** 
	 * @brief Method that resends the last sent message.
	 * @throws IllegalStateException if the last sent frame is null
	 * 								 This happens when we are sending frame #0.
	*/
	private void resendMessage() {
		if (sender.lastFrame == null) {
			LOGGER.severe("SENDER: Resending null frame");
			throw new IllegalStateException();
		}
		
		// resend the lost frame.
		LOGGER.warning("SENDER: Resending Frame: " + sender.lastFrame);
		
		transmit(sender.lastFrame);
		// reset the timer to zero.
		sender.endTimer();
		// return the sent frame.
		finishFrameSend(sender.lastFrame);
	}
	



	// =========================================================================
	/**
	 * For a sequence of bytes, determine its parity.
	 *
	 * @param data The sequence of bytes over which to calculate.
	 * @return <code>1</code> if the parity is odd; <code>0</code> if the parity
	 *         is even.
	 */
	private byte calculateParity(Queue<Byte> data) {

		int parity = 0;
		for (byte b : data) {
			for (int j = 0; j < Byte.SIZE; j += 1) {
				if (((1 << j) & b) != 0) {
					parity ^= 1;
				}
			}
		}

		return (byte) parity;

	} // calculateParity ()
		// =========================================================================

	// =========================================================================
	/**
	 * Remove a leading number of elements from the receive buffer.
	 *
	 * @param index The index of the position up to which the bytes are to be
	 *              removed.
	 */
	private void cleanBufferUpTo(int index) {

		for (int i = 0; i < index; i += 1) {
			receiveBuffer.remove();
		}

	} // cleanBufferUpTo ()
		// =========================================================================

	
	// =========================================================================
	// LOCAL CLASSES	

	class Sender {
		// stores current frame number to send using createFrame.
		private int currFrameNumber = 0;
		// boolean that flags that we got confirmation on the last sent frame.
		public boolean confirmationReceived = true;
		// queue that holds the last sent frame
		public Queue<Byte> lastFrame = null;
		// a long that holds the time unit where we started a timer
		private long timerStart;

		public void frameSent(Queue<Byte> frame) {
			// we have not received the confirmation message yet.
			confirmationReceived = false;
			// we can not send the next frame yet.
			// updates the current last frame.
			lastFrame = frame;
			
			// starts a new timer
			endTimer();
			startNewTimer();
		}

		public void acknowledgmentReceived() {
			// update that we should send the next frame.
			// no need to keep track of the old frame.
			lastFrame = null;
			confirmationReceived = true;
			incrementFrameNumber();
			// reset the timer to zero.
			endTimer();
		}

		/**
		 * @brief increments the current frame number to be sent using createFrame.
		 */
		public void incrementFrameNumber() {
			currFrameNumber++;
			currFrameNumber = currFrameNumber % 2; // prevent overflow.
		}


		/**
		 * @brief starts a timer for this instance once called.
		 */
		public void startNewTimer() {
			timerStart = System.currentTimeMillis();
		}

		/**
		 * @brief returns how long the timer has been running for
		 * @return a long denoting how many milliseconds have passed since the timer was
		 *         started.
		 */
		public long timerDuration() {
			if (timerStart == 0) {
				// ensure that the timer has started before
				throw new IllegalStateException("Timer has not been started yet.");
			}
			return System.currentTimeMillis() - timerStart;
		}

		/**
		 * @brief resets the timer to its initial state of zero.
		 */
		public void endTimer() {
			timerStart = 0;
		}

	}
	class Receiver {
		// stores current frame number to send using createFrame.
		private int currFrameNumber = 0;
		// boolean signaling that we received the correct frame.
		public boolean correctFrameReceived = false;

		// stores the last frame that arrived.
		private Queue<Byte> lastFrameReceived = null;


		public void storeLastFrame(Queue<Byte> frame){
			lastFrameReceived = frame;
		}

		public Queue<Byte> getLastFrameReceived(){
			return lastFrameReceived;
		}

		/** 
		 * @brief Evaluates whether the passed in frame is identical to the
		 * 		  last frame received.
		 * @param frameToCompare The frame to compare with the last frame received.
		 * */ 
		public boolean isDuplicateFrame(Queue<Byte> frameToCompare){
			return frameToCompare!= null && lastFrameReceived!=null && lastFrameReceived.containsAll(frameToCompare);
		}

		/**
		 * @brief increments the current frame number to be sent using createFrame.
		 */
		public void incrementFrameNumber() {
			currFrameNumber++;
			currFrameNumber = currFrameNumber % 2;
		}

		/**
		 * @brief increments the current frame number to be sent using createFrame.
		 */
		public void acknowledgmentSent() {
			// waiting on next frame.
			correctFrameReceived = false;
			// next frame
			incrementFrameNumber();
		}

		/**
		 * P
		 *
		 * @brief returns the current frame number.
		 */
		public int getFrameNumber() {
			return currFrameNumber;
		}

		public void frameReceived() {
			correctFrameReceived = true;
		}

		public void corruptedFrameReceived() {
			correctFrameReceived = false;
		}
	}

	// =============================================================================
} // class ParityDataLinkLayer
	// =============================================================================
