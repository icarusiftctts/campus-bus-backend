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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * GET AVAILABLE TRIPS HANDLER
 * 
 * Purpose: Fetch available bus trips with real-time seat availability
 * 
 * API Gateway Integration:
 * - Method: GET
 * - Path: /api/trips/available
 * - Authorization: Required (JWT token)
 * 
 * Frontend Integration (Flutter):
 * 1. Make HTTP GET request to: https://your-api-gateway-url/api/trips/available?route=CAMPUS_TO_CITY&tripDate=2024-10-15
 * 2. Headers: {
 *      "Content-Type": "application/json",
 *      "Authorization": "Bearer <authToken>"
 *    }
 * 3. Query Parameters:
 *    - route: "CAMPUS_TO_CITY" or "CITY_TO_CAMPUS"
 *    - tripDate: "YYYY-MM-DD" format
 * 4. Success Response (200): [
 *      {
 *        "tripId": "T12AB34CD",
 *        "departureTime": "08:30",
 *        "capacity": 35,
 *        "confirmedBookings": 28,
 *        "waitlistCount": 5,
 *        "availableSeats": 2
 *      }
 *    ]
 * 5. Error Response (401): {"message": "Invalid or expired token"}
 * 
 * Usage Flow:
 * - Student opens trip selection screen in Flutter app
 * - Selects route (Campus to City / City to Campus)
 * - Selects date (today or future dates)
 * - Flutter sends GET request with route and date parameters
 * - Display list of available trips with seat counts
 * - Show "Book Now" button if availableSeats > 0
 * - Show "Join Waitlist" button if availableSeats = 0
 * - Update UI every 30 seconds to show real-time availability
 * 
 * UI Display Tips:
 * - Green: availableSeats > 5 (Good availability)
 * - Yellow: availableSeats 1-5 (Limited seats)
 * - Red: availableSeats = 0 (Full - show waitlist option)
 * - Show waitlist position if student is waitlisted
 */
public class GetAvailableTripsHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {

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

            // Validate JWT token
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

            // Parse query parameters
            Map<String, Object> queryParams = (Map<String, Object>) event.get("queryStringParameters");
            if (queryParams == null) {
                return createErrorResponse(400, "Missing query parameters: route and tripDate");
            }

            String route = (String) queryParams.get("route");
            String dateStr = (String) queryParams.get("tripDate");
            
            if (route == null || dateStr == null) {
                return createErrorResponse(400, "Missing required parameters: route and tripDate");
            }

            LocalDate tripDate = LocalDate.parse(dateStr);
            List<Trip> trips = tripRepository.findByRouteAndDate(route, tripDate);
            List<Map<String, Object>> result = new ArrayList<>();

            for (Trip trip : trips) {
                int confirmed = tripRepository.countConfirmedBookings(trip.getTripId());
                int waitlist = tripRepository.countWaitlistBookings(trip.getTripId());
                int available = trip.getCapacity() - trip.getFacultyReserved() - confirmed;

                result.add(Map.of(
                    "tripId", trip.getTripId(),
                    "departureTime", trip.getDepartureTime().toString(),
                    "capacity", trip.getCapacity(),
                    "confirmedBookings", confirmed,
                    "waitlistCount", waitlist,
                    "availableSeats", Math.max(0, available)
                ));
            }

            return createSuccessResponse(200, result);

        } catch (Exception e) {
            return createErrorResponse(500, "Error: " + e.getMessage());
        }
    }

    private Map<String, Object> createSuccessResponse(int statusCode, Object data) {
        try {
            return Map.of(
                "statusCode", statusCode,
                "headers", Map.of("Content-Type", "application/json"),
                "body", objectMapper.writeValueAsString(data)
            );
        } catch (Exception e) {
            return createErrorResponse(500, "Error serializing response");
        }
    }

    private Map<String, Object> createErrorResponse(int statusCode, String message) {
        return Map.of(
            "statusCode", statusCode,
            "headers", Map.of("Content-Type", "application/json"),
            "body", "{\"message\":\"" + message + "\"}"
        );
    }
}