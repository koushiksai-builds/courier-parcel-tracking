<<<<<<< HEAD
# Courier Parcel Tracking System

This is a Spring Boot microservices project based on the SRS. The services are built and deployed separately and communicate through the API Gateway, MySQL, and RabbitMQ events.

## Services

| Service | Responsibility | Internal port | MySQL schema |
|---|---|---:|---|
| API Gateway | Serves the web UI and routes API requests | 8080 | — |
| User Service | Registration, login, roles, customers, couriers, and admin user management | 8081 | `courier_users` |
| Parcel Service | Booking, parcel details, and customer parcel list | 8082 | `courier_parcels` |
| Tracking Service | Tracking status and history; consumes parcel events | 8083 | `courier_tracking` |
| Delivery Service | Courier assignment, status updates, delay and exception detection | 8084 | `courier_delivery` |
| Notification Service | Customer notifications and failed-event review/retry | 8085 | `courier_notifications` |
| Payment Service | Customer payment orders, demo checkout, and admin payment history | 8086 | `courier_payments` |

Each service is its own Maven module and Spring Boot application. The shared `common` module contains identity and event contracts. RabbitMQ publishes parcel events to the tracking, delivery, notification, and payment queues. The services use separate MySQL schemas; the User Service owns the user table.

## Run with Docker Desktop

1. Open PowerShell in this project folder.
2. Set an admin password for this local run:

   ```powershell
   $env:ADMIN_PASSWORD = "Choose-A-Strong-Password"
   ```

3. Build and start the services:

   ```powershell
   docker compose up --build -d
   ```

   The first build compiles the Maven modules and may take a few minutes.

   If you already have this project running with its existing MySQL volume, create the new payment schema once before starting:

   ```powershell
   docker compose run --rm database-setup
   docker compose up --build -d
   ```

4. Wait for the services to start, then open **http://localhost:8081**. Docker Compose maps that host port to the API Gateway. The app login is `admin@courier.local` and the password is the value you set in step 2.
5. Register a customer from the page. The admin can create courier accounts and monitor users, parcels, deliveries, exceptions, activities, and failed events.

After a customer books a parcel, the page creates a checkout order and offers UPI, card, wallet, and cash on delivery. Online methods are demo-only: selecting Pay records a successful payment for the displayed fee without contacting a bank or charging an account. Set `PAYMENT_DEMO_AMOUNT` before `docker compose up` to change the default fee of INR 100.00. Cash on delivery is recorded as `PAY_ON_DELIVERY`.

## Email notifications

The Notification Service keeps saving in-app notifications. Email delivery is optional and is disabled by default. To enable SMTP email, set these environment variables in the same PowerShell window before starting or rebuilding the notification service:

```powershell
$env:MAIL_ENABLED = "true"
$env:MAIL_HOST = "smtp.example.com"
$env:MAIL_PORT = "587"
$env:MAIL_USERNAME = "your-smtp-username"
$env:MAIL_PASSWORD = "your-smtp-password"
$env:MAIL_FROM = "your-verified-sender@example.com"
$env:MAIL_SMTP_AUTH = "true"
$env:MAIL_SMTP_STARTTLS_ENABLE = "true"
$env:PUBLIC_BASE_URL = "http://localhost:8081"
docker compose up --build -d notification-service
```

Use the SMTP host, port, credentials, and verified sender supplied by your mail provider. Do not commit real credentials to the project. When SMTP is enabled, parcel events send an email to the parcel customer's registered email address; delivery failures use the existing RabbitMQ retry and failed-event flow.

Useful commands:

```powershell
docker compose ps
  docker compose logs -f gateway user-service parcel-service tracking-service delivery-service notification-service payment-service
docker compose down
```

`docker compose down` keeps the MySQL data volume. Do not add `--volumes` unless you intend to erase the stored users, parcels, deliveries, tracking history, and notifications.

## Ports

| Host port | Service |
|---:|---|
| 8081 | API Gateway and web UI |
| 3307 | MySQL |
| 5672 | RabbitMQ AMQP |
| 15672 | RabbitMQ management UI |

Payment Service listens on port 8086 inside the Compose network and is accessed through the gateway; it does not claim a host port.

Ports 8081 and 3307 are used on the host to avoid the common local conflicts on 8080 and 3306. Backend service ports are only reachable inside the Docker network.

## Build without Docker

Install Java 21, Maven, MySQL, and RabbitMQ. Create the six schemas listed above and grant the `courier` database user access to them. From the repository root:

```powershell
mvn -DskipTests package
```

Run the services with their module-specific Spring configuration and environment variables. `DB_URL` should point at the `courier_users` database; each entity uses its owning schema. Set `RABBIT_HOST` for the parcel, tracking, delivery, and notification services.

## SRS endpoint map

- Registration/login: `POST /api/auth/register`, authenticated `GET /api/auth/login`
- Booking and customer parcels: `POST /api/parcels`, `GET /api/parcels`
- Tracking: public `GET /api/tracking/{trackingId}`; authenticated history `GET /api/parcels/{trackingId}/history`
- Delivery: admin `PUT /api/delivery/{trackingId}/assignment`; courier/admin `PUT /api/delivery/{trackingId}/status`; courier `GET /api/delivery/mine`
- Notifications: `GET /api/notifications/mine`
- Payment: authenticated customer `GET /api/payments/{trackingId}`, then `POST /api/payments/{trackingId}/pay` with `{"method":"UPI"}`, `CARD`, `WALLET`, or `CASH_ON_DELIVERY`; admin history `GET /api/payments/admin/all`
- Admin: `GET /api/admin/users`, `/api/admin/parcels`, `/api/admin/delayed`, `/api/admin/exceptions`, `/api/admin/activities`, `/api/admin/events/failed`
- Failed-event recovery: admin `POST /api/admin/events/failed/{id}/retry`

Delay detection uses a one-hour grace period after the expected delivery time. Prolonged inactivity is defined as 24 hours; the SRS does not specify these thresholds.
=======
\# Courier and Parcel Tracking System



A courier and parcel tracking system built using Microservices and Event-Driven Architecture.



\## Technology Stack



| Layer | Technology |

|---|---|

| Backend | Java 21, Spring Boot, Spring Data JPA |

| API Gateway | Spring Cloud Gateway |

| Database | MySQL 8 |

| Messaging | RabbitMQ |

| Frontend | React + Vite (JavaScript/JSX) |

| Build | Maven |

| Deployment | Docker and Docker Compose |



\## Services and Ports



| Component | Port | Database |

|---|---:|---|

| API Gateway | 8080 | - |

| User Service | 8081 | courier\_users |

| Parcel Service | 8082 | courier\_parcels |

| Tracking Service | 8083 | courier\_tracking |

| Delivery Service | 8084 | courier\_delivery |

| Notification Service | 8085 | courier\_notifications |

| Frontend | 5173 | - |

| RabbitMQ | 5672\* | - |

| RabbitMQ Management UI | 15672\* | - |

| MySQL | 3306 | - |



\\\* RabbitMQ host ports may be changed later because of Windows port restrictions. The internal Docker RabbitMQ port will remain 5672.



\## Repository Structure



```text

courier-parcel-tracking/

├── frontend/

├── api-gateway/

├── user-service/

├── parcel-service/

├── tracking-service/

├── delivery-service/

├── notification-service/

├── docs/

│   ├── architecture/

│   ├── uml/

│   ├── api/

│   └── screenshots/

├── docker-compose.yml

├── .gitignore

└── README.md

>>>>>>> fd01c513dc0fde8c52ba6f75f8dbe35fe0ecab7c
