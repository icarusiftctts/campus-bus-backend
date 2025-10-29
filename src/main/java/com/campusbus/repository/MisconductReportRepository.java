package com.campusbus.repository;

import com.campusbus.entity.MisconductReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * MISCONDUCT REPORT REPOSITORY
 * 
 * Data access layer for MisconductReport entities.
 * Manages incident reports with photo evidence and status tracking.
 * 
 * Key Queries:
 * - findByStudentId: Get all reports for a specific student
 * - findByTripId: Get all reports for a specific trip
 * - findByOperatorId: Get reports submitted by an operator
 * - findByStatus: Get reports by status (PENDING/REVIEWED/RESOLVED)
 * 
 * Business Rules:
 * - Reports are immutable once submitted
 * - Status transitions: PENDING → REVIEWED → RESOLVED
 * - Photo URLs reference AWS S3 storage
 * - All reports include audit timestamps
 * 
 * Integration Points:
 * - SubmitMisconductReportHandler: save new reports
 * - Admin dashboard: findByStatus for pending reports
 * - Student management: findByStudentId for penalty tracking
 * - Trip analysis: findByTripId for incident patterns
 * 
 * AWS S3 Integration:
 * - Photo URLs stored in photo_url field
 * - S3 bucket: campusbus-misconduct-photos
 * - Folder structure: misconduct/{studentId}/{uuid}.jpg
 * - Lifecycle policy: Archive to Glacier after 90 days
 */
@Repository
public interface MisconductReportRepository extends JpaRepository<MisconductReport, String> {
    
    /**
     * Find reports by student ID.
     * Used for student penalty tracking and history.
     * 
     * @param studentId The student's unique identifier
     * @return List of reports for the student, ordered by most recent
     */
    @Query("SELECT mr FROM MisconductReport mr WHERE mr.studentId = ?1 ORDER BY mr.reportedAt DESC")
    List<MisconductReport> findByStudentId(String studentId);
    
    /**
     * Find reports by trip ID.
     * Used for trip incident analysis and reporting.
     * 
     * @param tripId The trip identifier
     * @return List of reports for the trip
     */
    @Query("SELECT mr FROM MisconductReport mr WHERE mr.tripId = ?1 ORDER BY mr.reportedAt ASC")
    List<MisconductReport> findByTripId(String tripId);
    
    /**
     * Find reports by operator ID.
     * Used for operator performance tracking.
     * 
     * @param operatorId The operator's unique identifier
     * @return List of reports submitted by the operator
     */
    @Query("SELECT mr FROM MisconductReport mr WHERE mr.operatorId = ?1 ORDER BY mr.reportedAt DESC")
    List<MisconductReport> findByOperatorId(String operatorId);
    
    /**
     * Find reports by status.
     * Used for admin dashboard and workflow management.
     * 
     * @param status The report status (PENDING/REVIEWED/RESOLVED)
     * @return List of reports with specified status
     */
    @Query("SELECT mr FROM MisconductReport mr WHERE mr.status = ?1 ORDER BY mr.reportedAt ASC")
    List<MisconductReport> findByStatus(String status);
    
    /**
     * Find reports by reason category.
     * Used for incident pattern analysis.
     * 
     * @param reason The misconduct reason
     * @return List of reports with specified reason
     */
    @Query("SELECT mr FROM MisconductReport mr WHERE mr.reason = ?1 ORDER BY mr.reportedAt DESC")
    List<MisconductReport> findByReason(String reason);
    
    /**
     * Count reports by student and date range.
     * Used for penalty calculation and student monitoring.
     * 
     * @param studentId The student's unique identifier
     * @param startDate Start of date range
     * @param endDate End of date range
     * @return Number of reports in date range
     */
    @Query("SELECT COUNT(mr) FROM MisconductReport mr WHERE mr.studentId = ?1 AND mr.reportedAt BETWEEN ?2 AND ?3")
    long countByStudentIdAndDateRange(String studentId, LocalDateTime startDate, LocalDateTime endDateTime);
    
    /**
     * Find reports with photos.
     * Used for evidence review and photo management.
     * 
     * @return List of reports that include photo evidence
     */
    @Query("SELECT mr FROM MisconductReport mr WHERE mr.photoUrl IS NOT NULL AND mr.photoUrl != '' ORDER BY mr.reportedAt DESC")
    List<MisconductReport> findReportsWithPhotos();
    
    /**
     * Find reports by date range.
     * Used for reporting and analytics.
     * 
     * @param startDate Start of date range
     * @param endDate End of date range
     * @return List of reports in date range
     */
    @Query("SELECT mr FROM MisconductReport mr WHERE mr.reportedAt BETWEEN ?1 AND ?2 ORDER BY mr.reportedAt DESC")
    List<MisconductReport> findByDateRange(LocalDateTime startDate, LocalDateTime endDateTime);
    
    /**
     * Count reports by status.
     * Used for dashboard statistics.
     * 
     * @param status The report status
     * @return Number of reports with specified status
     */
    @Query("SELECT COUNT(mr) FROM MisconductReport mr WHERE mr.status = ?1")
    long countByStatus(String status);
    
    /**
     * Find recent reports for operator.
     * Used for operator dashboard and recent activity.
     * 
     * @param operatorId The operator's unique identifier
     * @param limit Maximum number of reports to return
     * @return List of recent reports submitted by operator
     */
    @Query("SELECT mr FROM MisconductReport mr WHERE mr.operatorId = ?1 ORDER BY mr.reportedAt DESC")
    List<MisconductReport> findRecentReportsByOperatorId(String operatorId, int limit);
    
    /**
     * Find reports requiring review.
     * Used for admin workflow and pending tasks.
     * 
     * @param cutoffTime Reports older than this time need review
     * @return List of reports that may need attention
     */
    @Query("SELECT mr FROM MisconductReport mr WHERE mr.status = 'PENDING' AND mr.reportedAt < ?1 ORDER BY mr.reportedAt ASC")
    List<MisconductReport> findReportsRequiringReview(LocalDateTime cutoffTime);
}
