# Campus Bus Booking System

## Project Purpose
A comprehensive, cloud-native campus transportation ecosystem that enables seamless booking, real-time tracking, and intelligent management of bus trips between campus and city locations. The system enhances student experience, ensures operational efficiency, and enforces accountability through smart policies like no-show penalties and waitlist automation—all built on a scalable, serverless architecture.

## Key Features

### Student Experience
- **Secure Onboarding & Authentication**: College email-verified registration with student ID, hostel, room number, and phone validation.
- **Smart Trip Booking**: Intuitive interface for booking seats on “Campus → City” and “City → Campus” routes with real-time seat availability and waitlist positioning.
- **Dynamic QR Code System**: Color-coded QR codes (blue = active, green = scanned, red = expired) for boarding validation and journey tracking.
- **Flexible Cancellation & Swapping**:
  - Penalty-free cancellation if done >1 hour before departure.
  - Swap token issuance within 1 hour of departure to offer seat to waitlisted students and avoid penalties.
- **Real-Time Live Bus Tracking**: Students view live bus location on their “My Trip” screen to reduce uncertainty and optimize arrival timing.
- **Smart “Leave Now” Alerts**: Location-aware push notifications triggered by bus GPS, traffic conditions, and student proximity to boarding point.
- **SOS Emergency Feature**: One-tap emergency alert with location sharing to campus security during active trips.
- **Post-Trip Feedback**: Structured feedback collection on bus conditions, punctuality, and driver behavior.
- **Trip History & Penalty Dashboard**: View past trips, cancellation reasons, and current penalty status (e.g., “Blocked for 4 more trips”).

### Operator Tools
- **Simplified Scanning Interface**: Full-screen QR scanner with instant color-coded feedback (green = valid, red = invalid, yellow = duplicate).
- **Live GPS Tracking**: Automatic transmission of operator location during trips for real-time bus tracking.
- **Passenger Management**: Searchable list of booked students with scan status and manual check-in option.
- **Misconduct Reporting**: In-app photo capture and incident reporting linked to student profiles.

### Administrative Control
- **Executive Dashboard**: Real-time overview of bookings, active trips, waitlists, no-shows, and SOS alerts.
- **Trip Management**: Full CRUD operations for scheduling trips, assigning buses, and managing capacity.
- **Waitlist Automation**: Automatic alerts when waitlist exceeds threshold (e.g., 40 students), with one-click approval to add extra buses.
- **Student & Penalty Management**: View student profiles, booking history, penalty logs, and operator reports; unblock users after penalty resolution.
- **Faculty Reservation Rules**: Configure reserved seats per trip (e.g., 5 seats) with configurable release times (e.g., 30 mins before departure).
- **Advanced Reporting & Analytics**:
  - Occupancy trends
  - No-show frequency analysis
  - Aggregated feedback with driver ratings
  - Exportable reports (CSV/PDF)

### System-Wide Capabilities
- **Serverless Architecture**: Built on AWS Lambda for auto-scaling, cost efficiency, and high availability.
- **Relational Data Backend**: MySQL database with JPA/Hibernate for structured storage of users, trips, bookings, and reports.
- **Real-Time Data Sync**: Instant updates across student, operator, and admin interfaces (e.g., seat confirmation, QR status, live tracking).
- **Robust Policy Enforcement**: Automated no-show penalties, waitlist promotions, and swap token logic to maximize seat utilization.

## Target Users
- **Students**: Primary users who book, track, and manage daily or event-based trips with safety and convenience features.
- **Bus Operators**: Frontline staff using a streamlined mobile app for scanning, tracking, and reporting.
- **Administrators**: Transport office personnel managing schedules, monitoring operations, and generating insights.
- **Faculty** *(indirectly supported)*: Benefit from reserved seating on designated trips, though they do not interact directly with the app.

## Use Cases
- Daily commuting for off-campus students with real-time bus visibility.
- Emergency response via SOS during transit.
- Efficient seat reallocation using waitlists and swap tokens.
- Dynamic fleet scaling during peak demand (e.g., exams, events).
- Data-driven route and schedule optimization based on occupancy and feedback.
- Accountability enforcement through automated penalty and reporting systems.

## Value Proposition
Transforms campus transportation from a static service into an intelligent, responsive, and user-centric ecosystem. By integrating live tracking, predictive alerts, policy automation, and multi-role coordination, the system reduces student anxiety, minimizes empty seats, lowers administrative burden, and enhances safety—all powered by a modern, serverless, and scalable cloud architecture.
