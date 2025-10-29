package com.campusbus.lambda;

import com.amazonaws.services.iotdata.AWSIotData;
import com.amazonaws.services.iotdata.AWSIotDataClientBuilder;
import com.amazonaws.services.iotdata.model.PublishRequest;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.campusbus.util.AuthTokenUtil;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.ByteBuffer;
import java.util.Map;

/**
 * UPDATE GPS LOCATION HANDLER
 * 
 * Purpose: Receive and broadcast GPS location updates from operator
 * 
 * API Gateway Integration:
 * - Method: POST
 * - Path: /operator/gps
 * - Authorization: Required (Operator JWT token)
 * - Headers: Authorization: Bearer {token}
 * - Content-Type: application/json
 * 
 * Request Body:
 * {
 *   "tripId": "T401",
 *   "latitude": 12.9716,
 *   "longitude": 77.5946,
 *   "speed": 45.5,
 *   "timestamp": "2024-01-15T18:45:30Z"
 * }
 * 
 * Success Response (200):
 * {
 *   "message": "GPS location updated successfully",
 *   "timestamp": "2024-01-15T18:45:30Z"
 * }
 * 
 * Error Responses:
 * - 400: Missing required fields
 * - 401: Missing or invalid authorization header
 * - 401: Invalid token
 * - 500: Server error or IoT publish failure
 * 
 * Frontend Integration (Mobile Operator App):
 * - GpsTrackingService calls this every 30 seconds while trip is active
 * - Uses device GPS to get current location
 * - Sends location data with trip context
 * - Student apps receive real-time updates via IoT Core
 * 
 * GPS Data Flow:
 * 1. Operator app gets GPS coordinates from device
 * 2. Sends POST request with tripId and coordinates
 * 3. Lambda validates operator token
 * 4. Publishes GPS data to IoT Core topic
 * 5. Student apps subscribed to topic receive updates
 * 6. Student apps update map with bus location
 * 
 * AWS IoT Core Configuration Required:
 * - Topic: bus/location/{tripId}
 * - QoS Level: 1 (at least once delivery)
 * - Lambda execution role needs iot:Publish permission
 * - IoT Policy: Allow publish to bus/location/*
 * - Student apps subscribe to bus/location/{tripId}
 * 
 * IoT Topic Structure:
 * - bus/location/T401 (for trip T401)
 * - bus/location/T402 (for trip T402)
 * - Each trip has its own topic for isolation
 * 
 * GPS Data Format Published to IoT:
 * {
 *   "tripId": "T401",
 *   "latitude": 12.9716,
 *   "longitude": 77.5946,
 *   "speed": 45.5,
 *   "timestamp": "2024-01-15T18:45:30Z"
 * }
 * 
 * Student App Integration:
 * - Student apps subscribe to bus/location/{tripId}
 * - Receive GPS updates in real-time
 * - Update map markers with bus location
 * - Show estimated arrival time based on speed
 * 
 * AWS Configuration:
 * - Lambda execution role needs IoT Core publish permission
 * - IoT Policy: arn:aws:iot:region:account:topic/bus/location/*
 * - CloudWatch logs: /aws/lambda/UpdateGPSLocationHandler
 * - No database operations (stateless GPS broadcasting)
 */
public class UpdateGPSLocationHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    private static AWSIotData iotClient;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Initialize AWS IoT Data client.
     * Called once per Lambda container lifecycle.
     */
    static {
        iotClient = AWSIotDataClientBuilder.defaultClient();
    }

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> event, Context context) {
        try {
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

            // Parse request body
            String requestBody = (String) event.get("body");
            if (requestBody == null || requestBody.trim().isEmpty()) {
                return createErrorResponse(400, "Missing request body");
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> body = objectMapper.readValue(requestBody, Map.class);
            String tripId = (String) body.get("tripId");
            Object latitudeObj = body.get("latitude");
            Object longitudeObj = body.get("longitude");
            Object speedObj = body.get("speed");
            String timestamp = (String) body.get("timestamp");

            // Validate required fields
            if (tripId == null || tripId.trim().isEmpty()) {
                return createErrorResponse(400, "Missing tripId");
            }
            if (latitudeObj == null) {
                return createErrorResponse(400, "Missing latitude");
            }
            if (longitudeObj == null) {
                return createErrorResponse(400, "Missing longitude");
            }

            // Convert coordinates to double
            Double latitude, longitude, speed;
            try {
                latitude = ((Number) latitudeObj).doubleValue();
                longitude = ((Number) longitudeObj).doubleValue();
                speed = speedObj != null ? ((Number) speedObj).doubleValue() : 0.0;
            } catch (Exception e) {
                return createErrorResponse(400, "Invalid coordinate format");
            }

            // Validate coordinate ranges
            if (latitude < -90 || latitude > 90) {
                return createErrorResponse(400, "Latitude must be between -90 and 90");
            }
            if (longitude < -180 || longitude > 180) {
                return createErrorResponse(400, "Longitude must be between -180 and 180");
            }

            // Use current timestamp if not provided
            if (timestamp == null || timestamp.trim().isEmpty()) {
                timestamp = java.time.Instant.now().toString();
            }

            // Prepare GPS data for IoT Core
            Map<String, Object> gpsData = Map.of(
                "tripId", tripId.trim(),
                "latitude", latitude,
                "longitude", longitude,
                "speed", speed,
                "timestamp", timestamp
            );

            // Publish to IoT Core topic
            String topic = "bus/location/" + tripId.trim();
            String payload = objectMapper.writeValueAsString(gpsData);

            PublishRequest publishRequest = new PublishRequest()
                .withTopic(topic)
                .withQos(1) // At least once delivery
                .withPayload(ByteBuffer.wrap(payload.getBytes()));

            iotClient.publish(publishRequest);

            // Prepare success response
            Map<String, Object> responseData = Map.of(
                "message", "GPS location updated successfully",
                "timestamp", timestamp
            );

            return createSuccessResponse(200, responseData);

        } catch (Exception e) {
            // Log error for debugging
            System.err.println("UpdateGPSLocationHandler error: " + e.getMessage());
            e.printStackTrace();
            
            return createErrorResponse(500, "Internal server error. Please try again later.");
        }
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
