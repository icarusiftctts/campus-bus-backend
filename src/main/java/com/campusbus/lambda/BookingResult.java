package com.campusbus.lambda;

public class BookingResult {
    private final String status;
    private final String bookingId;
    private final String message;
    private final String qrToken;
    private final Integer waitlistPosition;
    
    private BookingResult(String status, String bookingId, String message, String qrToken, Integer waitlistPosition) {
        this.status = status;
        this.bookingId = bookingId;
        this.message = message;
        this.qrToken = qrToken;
        this.waitlistPosition = waitlistPosition;
    }
    
    public static BookingResult confirmed(String bookingId, String qrToken) {
        return new BookingResult("CONFIRMED", bookingId, "Seat confirmed", qrToken, null);
    }
    
    public static BookingResult waitlisted(String bookingId, int position) {
        return new BookingResult("WAITLIST", bookingId, "Added to waitlist", null, position);
    }
    
    public static BookingResult error(String message) {
        return new BookingResult("ERROR", null, message, null, null);
    }
    
    // Getters
    public String getStatus() { return status; }
    public String getBookingId() { return bookingId; }
    public String getMessage() { return message; }
    public String getQrToken() { return qrToken; }
    public Integer getWaitlistPosition() { return waitlistPosition; }
}