package com.campusbus.repository;

import com.campusbus.entity.Trip;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface TripRepository extends JpaRepository<Trip, String> {

    @Query("SELECT t FROM Trip t WHERE t.route = ?1 AND t.tripDate = ?2 AND t.status = 'ACTIVE' ORDER BY t.departureTime")
    List<Trip> findByRouteAndDate(String route, LocalDate date);

    @Query("SELECT COUNT(b) FROM Booking b WHERE b.tripId = ?1 AND b.status IN ('CONFIRMED','SCANNED')")
    int countConfirmedBookings(String tripId);

    @Query("SELECT COUNT(b) FROM Booking b WHERE b.tripId = ?1 AND b.status = 'WAITLIST'")
    int countWaitlistBookings(String tripId);
}