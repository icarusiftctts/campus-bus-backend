package com.campusbus.repository;

import com.campusbus.entity.Booking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

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
}