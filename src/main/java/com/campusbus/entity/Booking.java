package com.campusbus.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "bookings",
       uniqueConstraints = @UniqueConstraint(columnNames = {"studentId", "tripId"}),
       indexes = @Index(name = "idx_student_status", columnList = "studentId, status"))
public class Booking {
    @Id
    private String bookingId;
    
    @Column(nullable = false)
    private String studentId;
    
    @Column(nullable = false)
    private String tripId;
    
    private String status = "CONFIRMED";
    private String qrToken;
    private LocalDateTime bookedAt = LocalDateTime.now();
    private LocalDateTime scannedAt;
    private Integer waitlistPosition;

    // Constructors
    public Booking() {}
    public Booking(String bookingId, String studentId, String tripId, String status) {
        this.bookingId = bookingId;
        this.studentId = studentId;
        this.tripId = tripId;
        this.status = status;
    }

    // Getters & Setters
    public String getBookingId() { return bookingId; }
    public void setBookingId(String bookingId) { this.bookingId = bookingId; }
    public String getStudentId() { return studentId; }
    public void setStudentId(String studentId) { this.studentId = studentId; }
    public String getTripId() { return tripId; }
    public void setTripId(String tripId) { this.tripId = tripId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getQrToken() { return qrToken; }
    public void setQrToken(String qrToken) { this.qrToken = qrToken; }
    public LocalDateTime getBookedAt() { return bookedAt; }
    public void setBookedAt(LocalDateTime bookedAt) { this.bookedAt = bookedAt; }
    public LocalDateTime getScannedAt() { return scannedAt; }
    public void setScannedAt(LocalDateTime scannedAt) { this.scannedAt = scannedAt; }
    public Integer getWaitlistPosition() { return waitlistPosition; }
    public void setWaitlistPosition(Integer waitlistPosition) { this.waitlistPosition = waitlistPosition; }
}