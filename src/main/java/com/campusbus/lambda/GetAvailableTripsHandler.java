package com.campusbus.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.campusbus.repository.TripRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import java.time.LocalDate;
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
    private static long lastInitTime = 0;

    private void initializeSpringContext() {
        if (context == null || System.currentTimeMillis() - lastInitTime > 300000) {
            System.setProperty("spring.main.web-application-type", "none");
            System.setProperty("spring.main.lazy-initialization", "true");
            context = SpringApplication.run(com.campusbus.booking_system.BookingSystemApplication.class);
            tripRepository = context.getBean(TripRepository.class);
            lastInitTime = System.currentTimeMillis();
        }
    }

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> event, Context context) {
        long startTime = System.currentTimeMillis();
        
        try {
            initializeSpringContext();

            Map<String, String> queryStringParams = (Map<String, String>) event.get("queryStringParameters");
            if (queryStringParams == null) {
                return createErrorResponse(400, "Missing query parameters");
            }

            String route = queryStringParams.get("route");
            String dateStr = queryStringParams.get("date");

            if (route == null || dateStr == null) {
                return createErrorResponse(400, "Missing 'route' or 'date' parameter");
            }

            LocalDate tripDate = LocalDate.parse(dateStr);
            String dayType = isWeekend(tripDate) ? "WEEKEND" : "WEEKDAY";
            List<Map<String, Object>> trips = tripRepository.findTripAvailabilityWithCounts(tripDate, route, dayType);

            System.out.println("Request completed in " + (System.currentTimeMillis() - startTime) + "ms");
            return createSuccessResponse(200, trips);

        } catch (Exception e) {
            e.printStackTrace();
            return createErrorResponse(500, "Error: " + e.getMessage());
        }
    }

    private Map<String, Object> createSuccessResponse(int statusCode, Object body) {
        try {
            return Map.of(
                "statusCode", statusCode,
                "headers", Map.of(
                    "Content-Type", "application/json",
                    "Access-Control-Allow-Origin", "*"
                ),
                "body", objectMapper.writeValueAsString(body)
            );
        } catch (Exception e) {
            return createErrorResponse(500, "Error serializing response");
        }
    }

    private Map<String, Object> createErrorResponse(int statusCode, String message) {
        return Map.of(
            "statusCode", statusCode,
            "headers", Map.of(
                "Content-Type", "application/json",
                "Access-Control-Allow-Origin", "*"
            ),
            "body", "{\"error\":\"" + message + "\"}"
        );
    }

    private boolean isWeekend(LocalDate date) {
        int dayOfWeek = date.getDayOfWeek().getValue();
        return dayOfWeek == 6 || dayOfWeek == 7;
    }
}