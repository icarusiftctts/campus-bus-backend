package com.campusbus.repository;

import com.campusbus.entity.Booking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import com.campusbus.util.AuthTokenUtil; // Your JWT utility

import java.util.Optional;

@Repository
public interface BookingRepository extends JpaRepository<Booking, String> {
    
    Optional<Booking> findByStudentIdAndTripId(String studentId, String tripId);
    
    @Query("SELECT COUNT(b) FROM Booking b WHERE b.tripId = ?1 AND b.status = ?2")
    int countByTripIdAndStatus(String tripId, String status);
    
    @Query("SELECT b FROM Booking b WHERE b.tripId = ?1 AND b.status = 'WAITLIST' ORDER BY b.bookedAt ASC")
    Optional<Booking> findFirstWaitlistedBooking(String tripId);
    
    @Modifying
    @Transactional
    @Query("UPDATE Booking b SET b.status = 'CONFIRMED', b.waitlistPosition = null WHERE b.bookingId = ?1")
    int promoteFromWaitlist(String bookingId);
    
    @Modifying
    @Transactional
    @Query("UPDATE Booking b SET b.status = 'SCANNED', b.scannedAt = CURRENT_TIMESTAMP WHERE b.bookingId = ?1")
    int markAsScanned(String bookingId);

    @Modifying
    @Query("UPDATE Booking b SET b.status = 'CANCELLED' WHERE b.bookingId = ?1 AND b.tripId = ?2 AND b.status IN ('CONFIRMED', 'WAITLIST')")
    int markBookingCancelled(String bookingId, String tripId);

    // Promote next waitlisted
    @Modifying
    @Query(value = "UPDATE bookings SET status = 'CONFIRMED', waitlist_position = NULL " +
            "WHERE trip_id = ?1 AND status = 'WAITLIST' " +
            "ORDER BY booked_at ASC LIMIT 1", nativeQuery = true)
    void promoteNextWaitlisted(String tripId);

    // Renumber waitlist
    @Modifying
    @Query(value = "UPDATE bookings SET waitlist_position = waitlist_position - 1 " +
            "WHERE trip_id = ?1 AND status = 'WAITLIST' AND waitlist_position > 1", nativeQuery = true)
    void renumberWaitlist(String tripId);

    // Method aliases for consistency
    default void promoteNextWaitlist(String tripId) {
        promoteNextWaitlisted(tripId);
    }

    default void updateWaitlistPositions(String tripId) {
        renumberWaitlist(tripId);
    }

    @Modifying
    @Query("UPDATE Booking b SET b.status = 'SCANNED', b.scannedAt = CURRENT_TIMESTAMP WHERE b.bookingId = ?1 AND b.tripId = ?2")
    int markBookingScanned(String bookingId, String tripId);

    @Query("SELECT b FROM Booking b WHERE b.bookingId = ?1 AND b.tripId = ?2")
    Booking findBookingForValidation(String bookingId, String tripId);
}