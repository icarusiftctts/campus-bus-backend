package com.campusbus.repository;

import com.campusbus.entity.Trip;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Repository
public interface TripRepository extends JpaRepository<Trip, String> {

    @Query("SELECT t FROM Trip t WHERE t.route = ?1 AND t.tripDate = ?2 AND t.status = 'ACTIVE' ORDER BY t.departureTime")
    List<Trip> findByRouteAndDate(String route, LocalDate date);

    @Query("SELECT COUNT(b) FROM Booking b WHERE b.tripId = ?1 AND b.status IN ('CONFIRMED','SCANNED')")
    int countConfirmedBookings(String tripId);

    @Query("SELECT COUNT(b) FROM Booking b WHERE b.tripId = ?1 AND b.status = 'WAITLIST'")
    int countWaitlistBookings(String tripId);

    @Query(value = """
        SELECT 
            t.trip_id as tripId,
            t.route, 
            t.trip_date as tripDate,
            t.departure_time as departureTime,
            t.capacity,
            t.faculty_reserved as facultyReserved,
            COALESCE(confirmed.booked_count, 0) as bookedCount,
            COALESCE(waitlist.waitlist_count, 0) as waitlistCount,
            (t.capacity - t.faculty_reserved - COALESCE(confirmed.booked_count, 0)) as availableSeats
        FROM trips t
        LEFT JOIN (
            SELECT trip_id, COUNT(*) as booked_count 
            FROM bookings 
            WHERE status IN ('CONFIRMED', 'SCANNED') 
            GROUP BY trip_id
        ) confirmed ON t.trip_id = confirmed.trip_id
        LEFT JOIN (
            SELECT trip_id, COUNT(*) as waitlist_count 
            FROM bookings 
            WHERE status = 'WAITLIST' 
            GROUP BY trip_id
        ) waitlist ON t.trip_id = waitlist.trip_id
        WHERE t.trip_date = ?1 AND t.route = ?2 AND t.status = 'ACTIVE'
        ORDER BY t.departure_time
        """, nativeQuery = true)
    List<Map<String, Object>> findTripAvailabilityWithCounts(LocalDate tripDate, String route);
}