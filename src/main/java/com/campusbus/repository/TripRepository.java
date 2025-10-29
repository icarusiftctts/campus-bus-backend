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

    @Query("SELECT t FROM Trip t WHERE t.route = ?1 AND t.tripDate = ?2 AND t.dayType = ?3 AND t.status = 'ACTIVE' ORDER BY t.departureTime")
    List<Trip> findByRouteAndDate(String route, LocalDate date, String dayType);

    @Query("SELECT COUNT(b) FROM Booking b WHERE b.tripId = ?1 AND b.status IN ('CONFIRMED','SCANNED')")
    int countConfirmedBookings(String tripId);

    @Query("SELECT COUNT(b) FROM Booking b WHERE b.tripId = ?1 AND b.status = 'WAITLIST'")
    int countWaitlistBookings(String tripId);

    @Query(value = """
        SELECT 
            t.trip_id as tripId,
            t.route,
            t.destination,
            t.bus_number as busNumber,
            t.trip_date as tripDate,
            t.departure_time as departureTime,
            t.capacity,
            t.faculty_reserved as facultyReserved,
            t.day_type as dayType,
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
        WHERE t.trip_date = ?1 AND t.route = ?2 AND t.day_type = ?3 AND t.status = 'ACTIVE'
            AND (t.trip_date > CURDATE() OR (t.trip_date = CURDATE() AND t.departure_time > CURTIME()))
        ORDER BY t.departure_time
        """, nativeQuery = true)
    List<Map<String, Object>> findTripAvailabilityWithCounts(LocalDate tripDate, String route, String dayType);

    /**
     * Find trips assigned to operator for a specific date.
     * Used by GetOperatorTripsHandler to show operator's daily trips.
     * 
     * Returns trip information including:
     * - Trip ID, time, bus number, route
     * - Status derived from trip assignments (Active/Upcoming/Completed)
     * - Only shows trips assigned to the operator
     * 
     * @param operatorId The operator's unique identifier
     * @param tripDate The date to query trips for
     * @return List of trips with status information
     */
    @Query(value = """
        SELECT 
            t.trip_id as id,
            TIME_FORMAT(t.departure_time, '%H:%i:%s') as time,
            COALESCE(t.bus_number, 'Unassigned') as busNumber,
            CASE 
                WHEN t.route = 'CAMPUS_TO_CITY' THEN 'Campus → City'
                WHEN t.route = 'CITY_TO_CAMPUS' THEN 'City → Campus'
                ELSE t.route
            END as route,
            CASE 
                WHEN ta.status = 'IN_PROGRESS' THEN 'Active'
                WHEN ta.status = 'COMPLETED' THEN 'Completed'
                WHEN t.departure_time < CURRENT_TIME() THEN 'Completed'
                ELSE 'Upcoming'
            END as status
        FROM trips t
        LEFT JOIN trip_assignments ta ON t.trip_id = ta.trip_id AND ta.operator_id = ?1
        WHERE t.trip_date = ?2 AND t.status = 'ACTIVE'
        ORDER BY t.departure_time
        """, nativeQuery = true)
    List<Map<String, Object>> findOperatorTrips(String operatorId, LocalDate tripDate);
}