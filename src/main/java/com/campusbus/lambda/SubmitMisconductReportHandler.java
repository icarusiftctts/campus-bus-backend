package com.campusbus.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.campusbus.entity.MisconductReport;
import com.campusbus.repository.MisconductReportRepository;
import com.campusbus.util.AuthTokenUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

/**
 * SUBMIT MISCONDUCT REPORT HANDLER
 * 
 * Purpose: Submit misconduct report with optional photo evidence
 * 
 * API Gateway Integration:
 * - Method: POST
 * - Path: /operator/reports
 * - Authorization: Required (Operator JWT token)
 * - Headers: Authorization: Bearer {token}
 * - Content-Type: application/json
 * 
 * Request Body:
 * {
 *   "studentId": "STU101",
 *   "tripId": "T401",
 *   "reason": "Misbehavior",
 *   "comments": "Student was disruptive during boarding",
 *   "photoBase64": "/9j/4AAQSkZJRgABAQAAAQABAAD..." // Optional
 * }
 * 
 * Success Response (201):
 * {
 *   "reportId": "MR12AB34",
 *   "message": "Report submitted successfully"
 * }
 * 
 * Error Responses:
 * - 400: Missing required fields
 * - 401: Missing or invalid authorization header
 * - 401: Invalid token
 * - 500: Server error or S3 upload failure
 * 
 * Frontend Integration (Mobile Operator App):
 * - ReportingModal calls this when operator submits report
 * - Photo captured by camera, converted to base64
 * - Dropdown selection for misconduct reasons
 * - Text area for detailed comments
 * - Success confirmation shown to operator
 * 
 * Misconduct Categories:
 * - "Misbehavior": General disruptive behavior
 * - "Attempted boarding without valid QR": Security violation
 * - "Other": Custom incidents requiring comments
 * 
 * Photo Upload Process:
 * 1. Frontend captures photo with camera
 * 2. Converts to base64 string
 * 3. Sends in request body
 * 4. Lambda decodes base64 to bytes
 * 5. Uploads to S3 bucket with organized folder structure
 * 6. Stores S3 URL in database
 * 
 * AWS S3 Configuration Required:
 * - Bucket: campusbus-misconduct-photos
 * - Folder structure: misconduct/{studentId}/{uuid}.jpg
 * - Lambda execution role needs s3:PutObject permission
 * - Lifecycle policy: Archive to Glacier after 90 days
 * - Environment variable: MISCONDUCT_PHOTOS_BUCKET
 * 
 * Database Operations:
 * - Creates MisconductReport entity
 * - Auto-generates reportId with "MR" prefix
 * - Sets status to PENDING
 * - Records reported_at timestamp
 * - Links student, trip, and operator
 * 
 * AWS Configuration:
 * - Lambda execution role needs RDS and S3 access
 * - Environment variables: DB_HOST, DB_NAME, DB_USERNAME, DB_PASSWORD, MISCONDUCT_PHOTOS_BUCKET
 * - CloudWatch logs: /aws/lambda/SubmitMisconductReportHandler
 */
public class SubmitMisconductReportHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    private static ConfigurableApplicationContext context;
    private static MisconductReportRepository reportRepository;
    private static AmazonS3 s3Client;
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final String S3_BUCKET = System.getenv("MISCONDUCT_PHOTOS_BUCKET");

    /**
     * Initialize AWS S3 client and Spring context.
     * Called once per Lambda container lifecycle.
     */
    static {
        s3Client = AmazonS3ClientBuilder.defaultClient();
    }

    private void initializeSpringContext() {
        if (context == null) {
            System.setProperty("spring.main.web-application-type", "none");
            context = SpringApplication.run(com.campusbus.booking_system.BookingSystemApplication.class);
            reportRepository = context.getBean(MisconductReportRepository.class);
        }
    }

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> event, Context context) {
        try {
            initializeSpringContext();

            // Extract and validate authorization header
            @SuppressWarnings("unchecked")
            Map<String, Object> headers = (Map<String, Object>) event.get("headers");
            String authHeader = (String) headers.get("Authorization");
            
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return createErrorResponse(401, "Missing or invalid authorization header");
            }

            // Validate operator token
            String token = authHeader.substring(7);
            Map<String, Object> tokenData = AuthTokenUtil.validateOperatorToken(token);
            
            if (!(Boolean) tokenData.get("valid")) {
                return createErrorResponse(401, "Invalid token");
            }

            String operatorId = (String) tokenData.get("operatorId");

            // Parse request body
            String requestBody = (String) event.get("body");
            if (requestBody == null || requestBody.trim().isEmpty()) {
                return createErrorResponse(400, "Missing request body");
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> body = objectMapper.readValue(requestBody, Map.class);
            String studentId = (String) body.get("studentId");
            String tripId = (String) body.get("tripId");
            String reason = (String) body.get("reason");
            String comments = (String) body.get("comments");
            String photoBase64 = (String) body.get("photoBase64");

            // Validate required fields
            if (studentId == null || studentId.trim().isEmpty()) {
                return createErrorResponse(400, "Missing studentId");
            }
            if (tripId == null || tripId.trim().isEmpty()) {
                return createErrorResponse(400, "Missing tripId");
            }
            if (reason == null || reason.trim().isEmpty()) {
                return createErrorResponse(400, "Missing reason");
            }

            // Validate reason is one of the allowed values
            if (!isValidReason(reason)) {
                return createErrorResponse(400, "Invalid reason. Must be one of: Misbehavior, Attempted boarding without valid QR, Other");
            }

            // Validate "Other" reason requires comments
            if ("Other".equals(reason) && (comments == null || comments.trim().isEmpty())) {
                return createErrorResponse(400, "Comments required for 'Other' reason");
            }

            // Upload photo to S3 if provided
            String photoUrl = null;
            if (photoBase64 != null && !photoBase64.trim().isEmpty()) {
                try {
                    photoUrl = uploadPhotoToS3(photoBase64, studentId.trim());
                } catch (Exception e) {
                    System.err.println("Photo upload failed: " + e.getMessage());
                    // Continue without photo if upload fails
                    photoUrl = null;
                }
            }

            // Create misconduct report
            String reportId = "MR" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            
            MisconductReport report = new MisconductReport();
            report.setReportId(reportId);
            report.setStudentId(studentId.trim());
            report.setTripId(tripId.trim());
            report.setOperatorId(operatorId);
            report.setReason(reason.trim());
            report.setComments(comments != null ? comments.trim() : null);
            report.setPhotoUrl(photoUrl);
            report.setReportedAt(LocalDateTime.now());
            report.setStatus("PENDING");

            // Save report to database
            reportRepository.save(report);

            // Prepare success response
            Map<String, Object> responseData = Map.of(
                "reportId", reportId,
                "message", "Report submitted successfully"
            );

            return createSuccessResponse(201, responseData);

        } catch (Exception e) {
            // Log error for debugging
            System.err.println("SubmitMisconductReportHandler error: " + e.getMessage());
            e.printStackTrace();
            
            return createErrorResponse(500, "Internal server error. Please try again later.");
        }
    }

    /**
     * Upload photo to S3 bucket.
     * 
     * @param base64Photo Base64 encoded photo data
     * @param studentId Student ID for folder organization
     * @return S3 URL of uploaded photo
     * @throws Exception if upload fails
     */
    private String uploadPhotoToS3(String base64Photo, String studentId) throws Exception {
        if (S3_BUCKET == null || S3_BUCKET.trim().isEmpty()) {
            throw new RuntimeException("S3 bucket not configured. Set MISCONDUCT_PHOTOS_BUCKET environment variable.");
        }

        try {
            // Decode base64 to bytes
            byte[] photoBytes = Base64.getDecoder().decode(base64Photo);
            
            // Generate unique filename
            String fileName = "misconduct/" + studentId + "/" + UUID.randomUUID() + ".jpg";

            // Set metadata
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(photoBytes.length);
            metadata.setContentType("image/jpeg");

            // Upload to S3
            s3Client.putObject(S3_BUCKET, fileName, new ByteArrayInputStream(photoBytes), metadata);

            // Return S3 URL
            return s3Client.getUrl(S3_BUCKET, fileName).toString();
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to upload photo to S3: " + e.getMessage());
        }
    }

    /**
     * Validate misconduct reason.
     * 
     * @param reason The reason to validate
     * @return true if valid, false otherwise
     */
    private boolean isValidReason(String reason) {
        return "Misbehavior".equals(reason) || 
               "Attempted boarding without valid QR".equals(reason) || 
               "Other".equals(reason);
    }

    /**
     * Create success response with data.
     * 
     * @param statusCode HTTP status code
     * @param data Response data
     * @return API Gateway response format
     */
    private Map<String, Object> createSuccessResponse(int statusCode, Map<String, Object> data) {
        try {
            return Map.of(
                "statusCode", statusCode,
                "headers", Map.of(
                    "Content-Type", "application/json",
                    "Access-Control-Allow-Origin", "*",
                    "Access-Control-Allow-Headers", "Content-Type,Authorization",
                    "Access-Control-Allow-Methods", "GET,POST,PUT,DELETE,OPTIONS"
                ),
                "body", objectMapper.writeValueAsString(data)
            );
        } catch (Exception e) {
            return createErrorResponse(500, "Error serializing response");
        }
    }

    /**
     * Create error response with message.
     * 
     * @param statusCode HTTP status code
     * @param message Error message
     * @return API Gateway response format
     */
    private Map<String, Object> createErrorResponse(int statusCode, String message) {
        return Map.of(
            "statusCode", statusCode,
            "headers", Map.of(
                "Content-Type", "application/json",
                "Access-Control-Allow-Origin", "*",
                "Access-Control-Allow-Headers", "Content-Type,Authorization",
                "Access-Control-Allow-Methods", "GET,POST,PUT,DELETE,OPTIONS"
            ),
            "body", "{\"message\":\"" + message + "\"}"
        );
    }
}
