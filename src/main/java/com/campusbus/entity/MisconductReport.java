package com.campusbus.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * MISCONDUCT REPORT ENTITY
 * 
 * Represents reports of student misconduct during bus trips.
 * Includes photo evidence stored in AWS S3 and detailed incident information.
 * 
 * Database Table: misconduct_reports
 * 
 * Key Features:
 * - Links students, trips, and operators involved in incidents
 * - Categorizes misconduct reasons (Misbehavior, Invalid QR attempt, Other)
 * - Stores optional photo evidence with S3 URLs
 * - Tracks report status (PENDING/REVIEWED/RESOLVED)
 * - Detailed comments for incident description
 * - Audit trail with timestamps
 * 
 * Misconduct Categories:
 * 1. "Misbehavior" - General disruptive behavior
 * 2. "Attempted boarding without valid QR" - Security violation
 * 3. "Other" - Custom incidents requiring comments
 * 
 * Photo Evidence:
 * - Photos are uploaded to AWS S3 bucket: campusbus-misconduct-photos
 * - Folder structure: misconduct/{studentId}/{uuid}.jpg
 * - Base64 encoded in request, decoded and stored server-side
 * - S3 URLs stored in photo_url field
 * - Optional field - reports can be submitted without photos
 * 
 * Integration with Mobile Operator App:
 * - ReportingModal allows operators to submit reports
 * - Camera integration for photo capture
 * - Dropdown selection for misconduct reasons
 * - Text area for detailed comments
 * - SubmitMisconductReportHandler processes submissions
 * 
 * Database Relationships:
 * - Many-to-One with Student (many reports per student)
 * - Many-to-One with Trip (many reports per trip)
 * - Many-to-One with Operator (many reports per operator)
 * 
 * Business Rules:
 * - Report ID is auto-generated with "MR" prefix
 * - All reports start as PENDING status
 * - "Other" reason requires comments field
 * - Photo upload is optional but recommended
 * - Reports are immutable once submitted
 * 
 * AWS S3 Configuration Required:
 * - Bucket: campusbus-misconduct-photos
 * - Folder: misconduct/
 * - Permissions: Lambda execution role needs s3:PutObject
 * - Lifecycle: Archive to Glacier after 90 days
 */
@Entity
@Table(name = "misconduct_reports")
public class MisconductReport {
    
    @Id
    @Column(name = "report_id")
    private String reportId;
    
    @Column(name = "student_id", nullable = false)
    private String studentId;
    
    @Column(name = "trip_id", nullable = false)
    private String tripId;
    
    @Column(name = "operator_id", nullable = false)
    private String operatorId;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private String reason;
    
    @Column(columnDefinition = "TEXT")
    private String comments;
    
    @Column(name = "photo_url")
    private String photoUrl;
    
    @Column(name = "reported_at")
    private LocalDateTime reportedAt = LocalDateTime.now();
    
    @Enumerated(EnumType.STRING)
    private String status = "PENDING";

    // Constructors
    public MisconductReport() {}

    public MisconductReport(String reportId, String studentId, String tripId, String operatorId, String reason) {
        this.reportId = reportId;
        this.studentId = studentId;
        this.tripId = tripId;
        this.operatorId = operatorId;
        this.reason = reason;
    }

    // Getters and Setters
    public String getReportId() { 
        return reportId; 
    }
    
    public void setReportId(String reportId) { 
        this.reportId = reportId; 
    }
    
    public String getStudentId() { 
        return studentId; 
    }
    
    public void setStudentId(String studentId) { 
        this.studentId = studentId; 
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
    
    public String getReason() { 
        return reason; 
    }
    
    public void setReason(String reason) { 
        this.reason = reason; 
    }
    
    public String getComments() { 
        return comments; 
    }
    
    public void setComments(String comments) { 
        this.comments = comments; 
    }
    
    public String getPhotoUrl() { 
        return photoUrl; 
    }
    
    public void setPhotoUrl(String photoUrl) { 
        this.photoUrl = photoUrl; 
    }
    
    public LocalDateTime getReportedAt() { 
        return reportedAt; 
    }
    
    public void setReportedAt(LocalDateTime reportedAt) { 
        this.reportedAt = reportedAt; 
    }
    
    public String getStatus() { 
        return status; 
    }
    
    public void setStatus(String status) { 
        this.status = status; 
    }

    // Helper methods
    public boolean isPending() {
        return "PENDING".equals(this.status);
    }

    public boolean isReviewed() {
        return "REVIEWED".equals(this.status);
    }

    public boolean isResolved() {
        return "RESOLVED".equals(this.status);
    }

    public boolean hasPhoto() {
        return photoUrl != null && !photoUrl.trim().isEmpty();
    }

    public boolean isMisbehavior() {
        return "Misbehavior".equals(this.reason);
    }

    public boolean isInvalidQRAttempt() {
        return "Attempted boarding without valid QR".equals(this.reason);
    }

    public boolean isOtherReason() {
        return "Other".equals(this.reason);
    }

    @Override
    public String toString() {
        return "MisconductReport{" +
                "reportId='" + reportId + '\'' +
                ", studentId='" + studentId + '\'' +
                ", tripId='" + tripId + '\'' +
                ", operatorId='" + operatorId + '\'' +
                ", reason='" + reason + '\'' +
                ", status='" + status + '\'' +
                ", reportedAt=" + reportedAt +
                ", hasPhoto=" + hasPhoto() +
                '}';
    }
}
