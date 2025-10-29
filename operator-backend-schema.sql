-- =====================================================
-- CAMPUS BUS OPERATOR BACKEND - DATABASE MIGRATION
-- =====================================================
-- 
-- This script creates the database schema required for the
-- Mobile Operator App backend implementation.
-- 
-- Tables Created:
-- 1. operators - Bus operator authentication and management
-- 2. trip_assignments - Operator-trip assignments and tracking
-- 3. misconduct_reports - Incident reports with photo evidence
-- 
-- Modifications:
-- 1. trips table - Added bus_number and assigned_operator_id columns
-- 
-- Indexes:
-- - Performance indexes for common queries
-- - Foreign key constraints for data integrity
-- 
-- Sample Data:
-- - Test operator account for development/testing
-- 
-- Usage:
-- 1. Connect to your RDS MySQL instance
-- 2. Select the campusbus database
-- 3. Run this script: source operator-backend-schema.sql
-- 
-- Prerequisites:
-- - Existing campusbus database
-- - Existing tables: students, trips, bookings
-- - MySQL 8.0 or higher
-- =====================================================

-- =====================================================
-- 1. OPERATORS TABLE
-- =====================================================
-- Stores bus operator credentials and information
-- Used by OperatorLoginHandler for authentication

CREATE TABLE IF NOT EXISTS operators (
    operator_id VARCHAR(50) PRIMARY KEY COMMENT 'Unique operator identifier',
    employee_id VARCHAR(20) UNIQUE NOT NULL COMMENT 'Employee ID for login (e.g., op101)',
    name VARCHAR(100) NOT NULL COMMENT 'Operator full name',
    password_hash VARCHAR(255) NOT NULL COMMENT 'BCrypt hashed password',
    phone VARCHAR(15) COMMENT 'Contact phone number',
    email VARCHAR(100) COMMENT 'Contact email address',
    status ENUM('ACTIVE', 'INACTIVE', 'SUSPENDED') DEFAULT 'ACTIVE' COMMENT 'Account status',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT 'Account creation timestamp',
    last_login TIMESTAMP NULL COMMENT 'Last successful login timestamp',
    
    -- Indexes for performance
    INDEX idx_employee_id (employee_id),
    INDEX idx_status (status),
    INDEX idx_last_login (last_login)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Bus operators authentication and management';

-- =====================================================
-- 2. TRIP ASSIGNMENTS TABLE
-- =====================================================
-- Links operators to trips and tracks trip lifecycle
-- Used by StartTripHandler and trip status management

CREATE TABLE IF NOT EXISTS trip_assignments (
    assignment_id VARCHAR(50) PRIMARY KEY COMMENT 'Unique assignment identifier',
    trip_id VARCHAR(50) NOT NULL COMMENT 'Reference to trips table',
    operator_id VARCHAR(50) NOT NULL COMMENT 'Reference to operators table',
    bus_number VARCHAR(20) NOT NULL COMMENT 'Bus number assigned to trip',
    assigned_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT 'Assignment creation timestamp',
    started_at TIMESTAMP NULL COMMENT 'Trip start timestamp',
    completed_at TIMESTAMP NULL COMMENT 'Trip completion timestamp',
    status ENUM('ASSIGNED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED') DEFAULT 'ASSIGNED' COMMENT 'Assignment status',
    
    -- Foreign key constraints
    FOREIGN KEY (trip_id) REFERENCES trips(trip_id) ON DELETE CASCADE,
    FOREIGN KEY (operator_id) REFERENCES operators(operator_id) ON DELETE CASCADE,
    
    -- Indexes for performance
    INDEX idx_trip_operator (trip_id, operator_id),
    INDEX idx_operator_status (operator_id, status),
    INDEX idx_trip_status (trip_id, status),
    INDEX idx_assigned_at (assigned_at),
    INDEX idx_started_at (started_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Operator-trip assignments and lifecycle tracking';

-- =====================================================
-- 3. MISCONDUCT REPORTS TABLE
-- =====================================================
-- Stores incident reports with photo evidence
-- Used by SubmitMisconductReportHandler

CREATE TABLE IF NOT EXISTS misconduct_reports (
    report_id VARCHAR(50) PRIMARY KEY COMMENT 'Unique report identifier',
    student_id VARCHAR(50) NOT NULL COMMENT 'Reference to students table',
    trip_id VARCHAR(50) NOT NULL COMMENT 'Reference to trips table',
    operator_id VARCHAR(50) NOT NULL COMMENT 'Reference to operators table',
    reason ENUM('Misbehavior', 'Attempted boarding without valid QR', 'Other') NOT NULL COMMENT 'Incident category',
    comments TEXT COMMENT 'Detailed incident description',
    photo_url VARCHAR(500) COMMENT 'S3 URL of photo evidence',
    reported_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT 'Report submission timestamp',
    status ENUM('PENDING', 'REVIEWED', 'RESOLVED') DEFAULT 'PENDING' COMMENT 'Report processing status',
    
    -- Foreign key constraints
    FOREIGN KEY (student_id) REFERENCES students(student_id) ON DELETE CASCADE,
    FOREIGN KEY (trip_id) REFERENCES trips(trip_id) ON DELETE CASCADE,
    FOREIGN KEY (operator_id) REFERENCES operators(operator_id) ON DELETE CASCADE,
    
    -- Indexes for performance
    INDEX idx_student_reports (student_id, reported_at DESC),
    INDEX idx_trip_reports (trip_id),
    INDEX idx_operator_reports (operator_id, reported_at DESC),
    INDEX idx_status (status),
    INDEX idx_reported_at (reported_at),
    INDEX idx_reason (reason)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Student misconduct incident reports with photo evidence';

-- =====================================================
-- 4. MODIFY EXISTING TRIPS TABLE
-- =====================================================
-- Add bus number and assigned operator columns
-- Used for trip management and operator assignment

-- Add bus_number column
ALTER TABLE trips 
ADD COLUMN IF NOT EXISTS bus_number VARCHAR(20) COMMENT 'Bus number for this trip' 
AFTER capacity;

-- Add assigned_operator_id column
ALTER TABLE trips 
ADD COLUMN IF NOT EXISTS assigned_operator_id VARCHAR(50) COMMENT 'Currently assigned operator' 
AFTER bus_number;

-- Add foreign key constraint for assigned operator
ALTER TABLE trips 
ADD CONSTRAINT fk_trips_assigned_operator 
FOREIGN KEY (assigned_operator_id) REFERENCES operators(operator_id) 
ON DELETE SET NULL;

-- Add index for operator queries
ALTER TABLE trips 
ADD INDEX IF NOT EXISTS idx_assigned_operator (assigned_operator_id);

-- Add index for bus number queries
ALTER TABLE trips 
ADD INDEX IF NOT EXISTS idx_bus_number (bus_number);

-- =====================================================
-- 5. SAMPLE DATA FOR TESTING
-- =====================================================
-- Insert test operator account for development

-- Test operator account
-- Employee ID: op101
-- Password: buspass
-- BCrypt hash: $2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy

INSERT IGNORE INTO operators VALUES 
('OP001', 'op101', 'Test Operator', 
 '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
 '+919876543210', 'test.operator@campus.edu', 'ACTIVE', NOW(), NULL);

-- Additional test operators (optional)
INSERT IGNORE INTO operators VALUES 
('OP002', 'op102', 'Rajesh Kumar', 
 '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
 '+919876543211', 'rajesh.operator@campus.edu', 'ACTIVE', NOW(), NULL),
('OP003', 'op103', 'Priya Sharma', 
 '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
 '+919876543212', 'priya.operator@campus.edu', 'ACTIVE', NOW(), NULL);

-- =====================================================
-- 6. VERIFICATION QUERIES
-- =====================================================
-- Run these queries to verify the schema was created correctly

-- Check operators table
-- SELECT * FROM operators;

-- Check trip_assignments table structure
-- DESCRIBE trip_assignments;

-- Check misconduct_reports table structure
-- DESCRIBE misconduct_reports;

-- Check trips table modifications
-- DESCRIBE trips;

-- Check foreign key constraints
-- SELECT 
--     TABLE_NAME,
--     COLUMN_NAME,
--     CONSTRAINT_NAME,
--     REFERENCED_TABLE_NAME,
--     REFERENCED_COLUMN_NAME
-- FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE
-- WHERE REFERENCED_TABLE_SCHEMA = 'campusbus'
-- AND REFERENCED_TABLE_NAME IN ('operators', 'trips', 'students');

-- =====================================================
-- 7. CLEANUP (OPTIONAL)
-- =====================================================
-- Uncomment these lines to remove the schema if needed

-- DROP TABLE IF EXISTS misconduct_reports;
-- DROP TABLE IF EXISTS trip_assignments;
-- DROP TABLE IF EXISTS operators;
-- ALTER TABLE trips DROP COLUMN IF EXISTS assigned_operator_id;
-- ALTER TABLE trips DROP COLUMN IF EXISTS bus_number;

-- =====================================================
-- MIGRATION COMPLETE
-- =====================================================
-- 
-- Next Steps:
-- 1. Deploy Lambda functions to AWS
-- 2. Configure API Gateway endpoints
-- 3. Set up S3 bucket for photo storage
-- 4. Configure IoT Core for GPS tracking
-- 5. Update frontend with API endpoints
-- 6. Test end-to-end functionality
-- 
-- Environment Variables Required:
-- - DB_HOST: RDS endpoint
-- - DB_NAME: campusbus
-- - DB_USERNAME: database username
-- - DB_PASSWORD: database password
-- - MISCONDUCT_PHOTOS_BUCKET: S3 bucket name
-- 
-- AWS Permissions Required:
-- - Lambda execution role: RDS access, S3 PutObject, IoT Publish
-- - API Gateway: CORS configuration
-- - S3 bucket: Lifecycle policy for photo archiving
-- - IoT Core: Topic permissions for GPS broadcasting
-- =====================================================
