package com.campusbus.repository;

import com.campusbus.entity.Operator;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

/**
 * OPERATOR REPOSITORY
 * 
 * Data access layer for Operator entities.
 * Provides methods to query operators by various criteria.
 * 
 * Key Queries:
 * - findByEmployeeId: Used by OperatorLoginHandler for authentication
 * - findActiveOperator: Validates operator status before operations
 * 
 * Security Considerations:
 * - Employee ID queries are case-sensitive
 * - Status filtering ensures only ACTIVE operators can login
 * - No password queries exposed - authentication handled in service layer
 * 
 * Integration Points:
 * - OperatorLoginHandler: findByEmployeeId for login validation
 * - All operator Lambda handlers: findActiveOperator for token validation
 * - Admin functions: findAll for operator management
 */
@Repository
public interface OperatorRepository extends JpaRepository<Operator, String> {
    
    /**
     * Find operator by employee ID (case-sensitive).
     * Used by OperatorLoginHandler for authentication.
     * 
     * @param employeeId The unique employee identifier
     * @return Operator entity or null if not found
     */
    @Query("SELECT o FROM Operator o WHERE o.employeeId = ?1")
    Operator findByEmployeeId(String employeeId);
    
    /**
     * Find active operator by operator ID.
     * Used to validate operator status before operations.
     * 
     * @param operatorId The operator's unique identifier
     * @return Active operator or null if not found/inactive
     */
    @Query("SELECT o FROM Operator o WHERE o.operatorId = ?1 AND o.status = 'ACTIVE'")
    Operator findActiveOperator(String operatorId);
    
    /**
     * Check if employee ID exists (for validation).
     * 
     * @param employeeId The employee identifier to check
     * @return true if employee ID exists, false otherwise
     */
    @Query("SELECT COUNT(o) > 0 FROM Operator o WHERE o.employeeId = ?1")
    boolean existsByEmployeeId(String employeeId);
    
    /**
     * Find operators by status.
     * Used for admin functions and reporting.
     * 
     * @param status The operator status (ACTIVE, INACTIVE, SUSPENDED)
     * @return List of operators with specified status
     */
    @Query("SELECT o FROM Operator o WHERE o.status = ?1 ORDER BY o.name")
    java.util.List<Operator> findByStatus(String status);
    
    /**
     * Count operators by status.
     * Used for dashboard statistics.
     * 
     * @param status The operator status
     * @return Number of operators with specified status
     */
    @Query("SELECT COUNT(o) FROM Operator o WHERE o.status = ?1")
    long countByStatus(String status);
}
