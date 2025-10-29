package com.campusbus.repository;

import com.campusbus.entity.TripAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * TRIP ASSIGNMENT REPOSITORY
 * 
 * Data access layer for TripAssignment entities.
 * Manages operator-trip assignments and tracks trip lifecycle.
 * 
 * Key Queries:
 * - findByOperatorIdAndTripDate: Get operator's trips for a specific date
 * - findActiveAssignmentByTripId: Check if trip has active assignment
 * - findByOperatorIdAndStatus: Get assignments by operator and status
 * 
 * Business Rules:
 * - Only one active assignment per trip at a time
 * - Assignments track trip status transitions
 * - Historical assignments preserved for audit
 * 
 * Integration Points:
 * - GetOperatorTripsHandler: findByOperatorIdAndTripDate
 * - StartTripHandler: findActiveAssignmentByTripId, save new assignment
 * - Trip completion: update status to COMPLETED
 * - Admin functions: findAll for reporting
 */
@Repository
public interface TripAssignmentRepository extends JpaRepository<TripAssignment, String> {
    
    /**
     * Find assignments for operator on specific date.
     * Used by GetOperatorTripsHandler to show operator's daily trips.
     * 
     * @param operatorId The operator's unique identifier
     * @param tripDate The date to query assignments for
     * @return List of assignments for the operator on the specified date
     */
    @Query(value = """
        SELECT ta.* FROM trip_assignments ta
        JOIN trips t ON ta.trip_id = t.trip_id
        WHERE ta.operator_id = ?1 AND t.trip_date = ?2
        ORDER BY t.departure_time
        """, nativeQuery = true)
    List<TripAssignment> findByOperatorIdAndTripDate(String operatorId, LocalDate tripDate);
    
    /**
     * Find active assignment for a trip.
     * Used to check if trip already has an active operator.
     * 
     * @param tripId The trip identifier
     * @return Active assignment or empty if none found
     */
    @Query("SELECT ta FROM TripAssignment ta WHERE ta.tripId = ?1 AND ta.status = 'IN_PROGRESS'")
    Optional<TripAssignment> findActiveAssignmentByTripId(String tripId);
    
    /**
     * Find assignments by operator and status.
     * Used for operator dashboard and status tracking.
     * 
     * @param operatorId The operator's unique identifier
     * @param status The assignment status
     * @return List of assignments matching criteria
     */
    @Query("SELECT ta FROM TripAssignment ta WHERE ta.operatorId = ?1 AND ta.status = ?2 ORDER BY ta.assignedAt DESC")
    List<TripAssignment> findByOperatorIdAndStatus(String operatorId, String status);
    
    /**
     * Find all assignments for a trip (including historical).
     * Used for audit trails and trip history.
     * 
     * @param tripId The trip identifier
     * @return List of all assignments for the trip
     */
    @Query("SELECT ta FROM TripAssignment ta WHERE ta.tripId = ?1 ORDER BY ta.assignedAt DESC")
    List<TripAssignment> findByTripId(String tripId);
    
    /**
     * Count active assignments for operator.
     * Used to prevent multiple simultaneous trip assignments.
     * 
     * @param operatorId The operator's unique identifier
     * @return Number of active assignments
     */
    @Query("SELECT COUNT(ta) FROM TripAssignment ta WHERE ta.operatorId = ?1 AND ta.status = 'IN_PROGRESS'")
    long countActiveAssignmentsByOperatorId(String operatorId);
    
    /**
     * Update assignment status to completed.
     * Used when trip finishes successfully.
     * 
     * @param assignmentId The assignment identifier
     * @param completedAt The completion timestamp
     * @return Number of updated records
     */
    @Modifying
    @Transactional
    @Query("UPDATE TripAssignment ta SET ta.status = 'COMPLETED', ta.completedAt = ?2 WHERE ta.assignmentId = ?1")
    int markAssignmentCompleted(String assignmentId, LocalDateTime completedAt);
    
    /**
     * Find assignments by date range.
     * Used for reporting and analytics.
     * 
     * @param startDate Start of date range
     * @param endDate End of date range
     * @return List of assignments in date range
     */
    @Query(value = """
        SELECT ta.* FROM trip_assignments ta
        JOIN trips t ON ta.trip_id = t.trip_id
        WHERE t.trip_date BETWEEN ?1 AND ?2
        ORDER BY t.trip_date DESC, t.departure_time DESC
        """, nativeQuery = true)
    List<TripAssignment> findByDateRange(LocalDate startDate, LocalDate endDate);
    
    /**
     * Find assignments needing completion.
     * Used for cleanup jobs and status monitoring.
     * 
     * @param cutoffTime Trips started before this time should be completed
     * @return List of assignments that may need completion
     */
    @Query("SELECT ta FROM TripAssignment ta WHERE ta.status = 'IN_PROGRESS' AND ta.startedAt < ?1")
    List<TripAssignment> findAssignmentsNeedingCompletion(LocalDateTime cutoffTime);
}
