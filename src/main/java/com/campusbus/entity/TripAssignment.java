package com.campusbus.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * TRIP ASSIGNMENT ENTITY
 * 
 * Represents the assignment of operators to specific trips.
 * Tracks the lifecycle of a trip from assignment to completion.
 * 
 * Database Table: trip_assignments
 * 
 * Key Features:
 * - Links operators to specific trips
 * - Tracks trip status (ASSIGNED/IN_PROGRESS/COMPLETED/CANCELLED)
 * - Records bus number for the assignment
 * - Timestamps for assignment, start, and completion
 * - Foreign key relationships to operators and trips tables
 * 
 * Status Flow:
 * 1. ASSIGNED - Operator assigned to trip, not yet started
 * 2. IN_PROGRESS - Operator started the trip (scanning active)
 * 3. COMPLETED - Trip finished successfully
 * 4. CANCELLED - Trip cancelled or operator changed
 * 
 * Integration with Mobile Operator App:
 * - BusSelectionScreen shows available trips for assignment
 * - StartTripHandler creates assignment when operator starts scanning
 * - ScannerScreen uses assignment to track active trip
 * - GPS tracking is tied to active assignments
 * 
 * Database Relationships:
 * - Many-to-One with Operator (many assignments per operator)
 * - Many-to-One with Trip (many assignments per trip, but only one active)
 * 
 * Business Rules:
 * - Only one active assignment per trip at a time
 * - Operator can have multiple assignments on different dates
 * - Assignment ID is auto-generated with "TA" prefix
 * - Bus number must be provided when starting trip
 */
@Entity
@Table(name = "trip_assignments")
public class TripAssignment {
    
    @Id
    @Column(name = "assignment_id")
    private String assignmentId;
    
    @Column(name = "trip_id", nullable = false)
    private String tripId;
    
    @Column(name = "operator_id", nullable = false)
    private String operatorId;
    
    @Column(name = "bus_number", nullable = false)
    private String busNumber;
    
    @Column(name = "assigned_at")
    private LocalDateTime assignedAt = LocalDateTime.now();
    
    @Column(name = "started_at")
    private LocalDateTime startedAt;
    
    @Column(name = "completed_at")
    private LocalDateTime completedAt;
    
    @Enumerated(EnumType.STRING)
    private String status = "ASSIGNED";

    // Constructors
    public TripAssignment() {}

    public TripAssignment(String assignmentId, String tripId, String operatorId, String busNumber) {
        this.assignmentId = assignmentId;
        this.tripId = tripId;
        this.operatorId = operatorId;
        this.busNumber = busNumber;
    }

    // Getters and Setters
    public String getAssignmentId() { 
        return assignmentId; 
    }
    
    public void setAssignmentId(String assignmentId) { 
        this.assignmentId = assignmentId; 
    }
    
    public String getTripId() { 
        return tripId; 
    }
    
    public void setTripId(String tripId) { 
        this.tripId = tripId; 
    }
    
    public String getOperatorId() { 
        return operatorId; 
    }
    
    public void setOperatorId(String operatorId) { 
        this.operatorId = operatorId; 
    }
    
    public String getBusNumber() { 
        return busNumber; 
    }
    
    public void setBusNumber(String busNumber) { 
        this.busNumber = busNumber; 
    }
    
    public LocalDateTime getAssignedAt() { 
        return assignedAt; 
    }
    
    public void setAssignedAt(LocalDateTime assignedAt) { 
        this.assignedAt = assignedAt; 
    }
    
    public LocalDateTime getStartedAt() { 
        return startedAt; 
    }
    
    public void setStartedAt(LocalDateTime startedAt) { 
        this.startedAt = startedAt; 
    }
    
    public LocalDateTime getCompletedAt() { 
        return completedAt; 
    }
    
    public void setCompletedAt(LocalDateTime completedAt) { 
        this.completedAt = completedAt; 
    }
    
    public String getStatus() { 
        return status; 
    }
    
    public void setStatus(String status) { 
        this.status = status; 
    }

    // Helper methods
    public boolean isAssigned() {
        return "ASSIGNED".equals(this.status);
    }

    public boolean isInProgress() {
        return "IN_PROGRESS".equals(this.status);
    }

    public boolean isCompleted() {
        return "COMPLETED".equals(this.status);
    }

    public boolean isCancelled() {
        return "CANCELLED".equals(this.status);
    }

    // Calculate trip duration if completed
    public Long getTripDurationMinutes() {
        if (startedAt != null && completedAt != null) {
            return java.time.Duration.between(startedAt, completedAt).toMinutes();
        }
        return null;
    }

    @Override
    public String toString() {
        return "TripAssignment{" +
                "assignmentId='" + assignmentId + '\'' +
                ", tripId='" + tripId + '\'' +
                ", operatorId='" + operatorId + '\'' +
                ", busNumber='" + busNumber + '\'' +
                ", status='" + status + '\'' +
                ", assignedAt=" + assignedAt +
                ", startedAt=" + startedAt +
                ", completedAt=" + completedAt +
                '}';
    }
}
