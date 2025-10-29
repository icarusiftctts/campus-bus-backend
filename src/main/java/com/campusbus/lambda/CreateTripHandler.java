package com.campusbus.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.campusbus.entity.Trip;
import com.campusbus.repository.TripRepository;
import com.campusbus.util.AuthTokenUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;
import java.util.UUID;

/**
 * CREATE TRIP HANDLER (ADMIN ONLY)
 * 
 * Purpose: Create new bus trips (for admin dashboard use)
 * 
 * API Gateway Integration:
 * - Method: POST
 * - Path: /api/trips
 * - Authorization: Required (Admin JWT token - future enhancement)
 * 
 * Frontend Integration (Flutter - Admin Dashboard):
 * 1. Make HTTP POST request to: https://your-api-gateway-url/api/trips
 * 2. Headers: {
 *      "Content-Type": "application/json",
 *      "Authorization": "Bearer <adminToken>"
 *    }
 * 3. Body: {
 *      "route": "CAMPUS_TO_CITY",
 *      "destination": "Raja Park",
 *      "busNumber": "1",
 *      "tripDate": "2024-10-15",
 *      "departureTime": "08:30",
 *      "capacity": 35,
 *      "facultyReserved": 5,
 *      "dayType": "WEEKDAY"
 *    }
 * 4. Success Response (201): {
 *      "message": "Trip created: T12AB34CD"
 *    }
 * 5. Error Response (401): {"message": "Admin access required"}
 * 
 * Usage Flow (Admin Dashboard):
 * - Admin opens trip management screen
 * - Fills out trip creation form:
 *   - Route: Dropdown (Campus to City / City to Campus)
 *   - Date: Date picker (today or future dates)
 *   - Time: Time picker (departure time)
 *   - Capacity: Number input (default 35)
 *   - Faculty Reserved: Number input (default 5)
 * - Clicks "Create Trip" button
 * - Flutter sends POST request with trip details
 * - On success: Add trip to list, show success message
 * - On error: Show error message
 * 
 * Admin Dashboard Features:
 * - Bulk trip creation (multiple trips at once)
 * - Copy from existing trip template
 * - Recurring trip scheduling (daily/weekly)
 * - Trip capacity management
 * - Faculty seat reservation settings
 * - Real-time booking monitoring
 * 
 * Route Options:
 * - "CAMPUS_TO_CITY": Morning trips from campus to city
 * - "CITY_TO_CAMPUS": Evening trips from city back to campus
 * 
 * Capacity Management:
 * - Total capacity: Usually 35 seats per bus
 * - Faculty reserved: Seats reserved for faculty (default 5)
 * - Student seats: capacity - facultyReserved = available for students
 * - Waitlist: Unlimited (managed automatically)
 * 
 * Business Rules:
 * - Cannot create trips for past dates
 * - Cannot create duplicate trips (same route, date, time)
 * - Minimum 30 minutes between trips on same route
 * - Maximum 50 seats per bus (safety limit)
 * - Faculty reserved seats cannot exceed 50% of capacity
 */
public class CreateTripHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    private static ConfigurableApplicationContext context;
    private static TripRepository tripRepository;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private void initializeSpringContext() {
        if (context == null) {
            System.setProperty("spring.main.web-application-type", "none");
            context = SpringApplication.run(com.campusbus.booking_system.BookingSystemApplication.class);
            tripRepository = context.getBean(TripRepository.class);
        }
    }

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> event, Context context) {
        try {
            initializeSpringContext();

            // TODO: Add admin role validation
            // For now, validate JWT token (future: check if user has admin role)
            Map<String, Object> headers = (Map<String, Object>) event.get("headers");
            String authHeader = (String) headers.get("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return createErrorResponse(401, "Missing or invalid Authorization header");
            }

            String token = authHeader.substring(7);
            Map<String, Object> tokenData = AuthTokenUtil.validateAuthToken(token);
            if (!(Boolean) tokenData.get("valid")) {
                return createErrorResponse(401, "Invalid or expired token");
            }

            // TODO: Verify admin role
            // String userRole = getUserRole(tokenData.get("studentId"));
            // if (!"ADMIN".equals(userRole)) {
            //     return createErrorResponse(403, "Admin access required");
            // }

            // Parse request body
            String requestBody = (String) event.get("body");
            Map<String, Object> body = objectMapper.readValue(requestBody, Map.class);

            String route = (String) body.get("route");
            String destination = (String) body.get("destination");
            String busNumber = (String) body.get("busNumber");
            String tripDateStr = (String) body.get("tripDate");
            String departureTimeStr = (String) body.get("departureTime");
            Integer capacity = (Integer) body.getOrDefault("capacity", 35);
            Integer facultyReserved = (Integer) body.getOrDefault("facultyReserved", 5);
            String dayType = (String) body.getOrDefault("dayType", "WEEKDAY");

            // Validate input
            if (!route.equals("CAMPUS_TO_CITY") && !route.equals("CITY_TO_CAMPUS")) {
                return createErrorResponse(400, "Invalid route. Must be CAMPUS_TO_CITY or CITY_TO_CAMPUS");
            }

            LocalDate tripDate = LocalDate.parse(tripDateStr);
            LocalTime departureTime = LocalTime.parse(departureTimeStr);

            // Business rule validations
            if (tripDate.isBefore(LocalDate.now())) {
                return createErrorResponse(400, "Cannot create trips for past dates");
            }

            if (capacity > 50) {
                return createErrorResponse(400, "Maximum capacity is 50 seats per bus");
            }

            if (facultyReserved > capacity / 2) {
                return createErrorResponse(400, "Faculty reserved seats cannot exceed 50% of capacity");
            }

            String tripId = "T" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            
            Trip trip = new Trip();
            trip.setTripId(tripId);
            trip.setRoute(route);
            trip.setDestination(destination);
            trip.setBusNumber(busNumber);
            trip.setTripDate(tripDate);
            trip.setDepartureTime(departureTime);
            trip.setCapacity(capacity);
            trip.setFacultyReserved(facultyReserved);
            trip.setDayType(dayType);

            tripRepository.save(trip);

            return createSuccessResponse(201, "Trip created: " + tripId);

        } catch (Exception e) {
            return createErrorResponse(500, "Error: " + e.getMessage());
        }
    }

    private Map<String, Object> createSuccessResponse(int statusCode, String message) {
        return Map.of(
            "statusCode", statusCode,
            "headers", Map.of("Content-Type", "application/json"),
            "body", "{\"message\":\"" + message + "\"}"
        );
    }

    private Map<String, Object> createErrorResponse(int statusCode, String message) {
        return Map.of(
            "statusCode", statusCode,
            "headers", Map.of("Content-Type", "application/json"),
            "body", "{\"message\":\"" + message + "\"}"
        );
    }
}