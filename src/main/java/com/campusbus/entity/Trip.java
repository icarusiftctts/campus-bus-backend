package com.campusbus.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "trips")
public class Trip {
    @Id
    private String tripId;

    @Column(nullable = false)
    private String route; // "CAMPUS_TO_CITY" or "CITY_TO_CAMPUS"

    @Column(length = 50)
    private String destination;

    @Column(name = "bus_number", length = 10)
    private String busNumber;

    private LocalDate tripDate;
    private LocalTime departureTime;
    private int capacity = 35;
    private int facultyReserved = 5;
    private String status = "ACTIVE";

    @Column(name = "day_type", length = 10)
    private String dayType = "WEEKDAY";

    private LocalDateTime createdAt = LocalDateTime.now();

    // Constructors
    public Trip() {}
    public Trip(String tripId, String route, LocalDate tripDate, LocalTime departureTime) {
        this.tripId = tripId;
        this.route = route;
        this.tripDate = tripDate;
        this.departureTime = departureTime;
    }

    public Trip(String tripId, String route, String destination, String busNumber, LocalDate tripDate, LocalTime departureTime, String dayType) {
        this.tripId = tripId;
        this.route = route;
        this.destination = destination;
        this.busNumber = busNumber;
        this.tripDate = tripDate;
        this.departureTime = departureTime;
        this.dayType = dayType;
    }

    // Getters & Setters
    public String getTripId() { return tripId; }
    public void setTripId(String tripId) { this.tripId = tripId; }
    public String getRoute() { return route; }
    public void setRoute(String route) { this.route = route; }
    public LocalDate getTripDate() { return tripDate; }
    public void setTripDate(LocalDate tripDate) { this.tripDate = tripDate; }
    public LocalTime getDepartureTime() { return departureTime; }
    public void setDepartureTime(LocalTime departureTime) { this.departureTime = departureTime; }
    public int getCapacity() { return capacity; }
    public void setCapacity(int capacity) { this.capacity = capacity; }
    public int getFacultyReserved() { return facultyReserved; }
    public void setFacultyReserved(int facultyReserved) { this.facultyReserved = facultyReserved; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getDestination() { return destination; }
    public void setDestination(String destination) { this.destination = destination; }
    public String getBusNumber() { return busNumber; }
    public void setBusNumber(String busNumber) { this.busNumber = busNumber; }
    public String getDayType() { return dayType; }
    public void setDayType(String dayType) { this.dayType = dayType; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}