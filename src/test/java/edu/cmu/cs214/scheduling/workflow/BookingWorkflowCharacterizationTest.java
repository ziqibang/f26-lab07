package edu.cmu.cs214.scheduling.workflow;

import edu.cmu.cs214.scheduling.domain.Booking;
import edu.cmu.cs214.scheduling.domain.BookingOutcome;
import edu.cmu.cs214.scheduling.domain.BookingRequest;
import edu.cmu.cs214.scheduling.domain.BookingStore;
import edu.cmu.cs214.scheduling.domain.Member;
import edu.cmu.cs214.scheduling.domain.MembershipTier;
import edu.cmu.cs214.scheduling.domain.Room;
import edu.cmu.cs214.scheduling.notify.NotificationHub;
import edu.cmu.cs214.scheduling.pricing.PriceCalculator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins what BookingWorkflow does today, written before the refactor. These
 * assert observed behavior, not intended behavior.
 */
class BookingWorkflowCharacterizationTest {

    private static final LocalDateTime MON_8AM = LocalDateTime.of(2026, 10, 5, 8, 0);
    private static final LocalDateTime MON_9AM = LocalDateTime.of(2026, 10, 5, 9, 0);
    private static final LocalDateTime MON_10AM = LocalDateTime.of(2026, 10, 5, 10, 0);

    private BookingStore store;
    private NotificationHub hub;
    private BookingWorkflow workflow;

    @BeforeEach
    void setUp() {
        store = new BookingStore();
        store.addRoom(new Room("C-200", "Cedar Hall", 20));
        store.addMember(new Member("m-1", "Ada", "ada@rooms.example.edu", MembershipTier.BASIC));
        store.addMember(new Member("m-2", "Grace", "grace@rooms.example.edu",
                MembershipTier.PREMIER));
        hub = new NotificationHub();
        workflow = new BookingWorkflow(store, new PriceCalculator(), hub);
    }

    /**
     * A recurring occurrence that starts exactly when an existing booking ends
     * is skipped (the series overlap check is inclusive), even though a regular
     * booking in the same slot would be accepted.
     */
    @Test
    void recurringSubmitSkipsAnOccurrenceThatStartsWhenAnotherEnds() {
        workflow.submit(BookingRequest.regular("C-200", "m-2", MON_8AM, MON_9AM, 2));

        BookingOutcome outcome = workflow.submit(
                BookingRequest.recurring("C-200", "m-1", MON_9AM, MON_10AM, 2, 6));

        assertTrue(outcome.isAccepted());
        assertEquals(1, outcome.getSkipped().size());
        assertEquals(MON_9AM, outcome.getSkipped().get(0).start());
        assertEquals(1, outcome.getBooked().size());
        Booking booked = outcome.getBooking();
        assertEquals(MON_9AM.plusWeeks(1), booked.getStart());
        assertEquals(2, booked.getOccurrenceIndex());
        assertEquals("series S-1: 1 booked, 1 skipped", outcome.getMessage());
        assertEquals(2, store.activeInRoom("C-200").size());
        assertEquals(2, hub.getOutbox().size());
    }
}
