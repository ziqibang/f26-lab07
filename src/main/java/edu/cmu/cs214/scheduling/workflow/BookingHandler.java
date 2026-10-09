package edu.cmu.cs214.scheduling.workflow;

import edu.cmu.cs214.scheduling.domain.Booking;
import edu.cmu.cs214.scheduling.domain.BookingOutcome;
import edu.cmu.cs214.scheduling.domain.BookingRequest;
import edu.cmu.cs214.scheduling.domain.Room;

/**
 * What one booking type does at each step of the workflow. The workflow
 * resolves the room and the booking, then hands off to the handler for the
 * type.
 */
interface BookingHandler {

    /** Validates and writes a request for a room that is known to exist. */
    BookingOutcome submit(BookingRequest request, Room room);

    /** Releases a live booking. */
    boolean cancel(Booking booking, String roomName, boolean adminOverride);

    /** What the holder owes, in dollars. */
    double price(Booking booking);

    /** A one-line summary. */
    String describe(Booking booking, String roomName);
}
