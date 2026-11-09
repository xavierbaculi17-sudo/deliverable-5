# Online Event Catering and Booking System (PF201 - OOP, IT2F)

Console app: Java 17 + SQLite (JDBC) + Maven. Single entry point: `com.yourdomain.Main`.

## Run
    mvn compile exec:java        (or run Main.java from IntelliJ)

`catering.db` is created automatically on first run (DatabaseInitializer).

## Demo accounts
| Username | Password | Role | Status |
|---|---|---|---|
| admin | admin123 | ADMIN | APPROVED |
| staff1 | staff123 | STAFF | APPROVED |
| customer1 | cust123 | CUSTOMER | APPROVED |
| maria.santos / juan.delacruz | pass1234 | CUSTOMER | PENDING |
| ana.reyes | pass1234 | STAFF | PENDING |

## Deliverable 5 screenshots
1. Login as `admin` -> option 2 (View Pending) -> option 3 (Approve) -> enter an ID -> success message.
2. Open `catering.db` in DB Browser / SQLite Studio -> `users` table -> status is now APPROVED.
