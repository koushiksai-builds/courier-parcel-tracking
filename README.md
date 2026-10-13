# Courier Parcel Tracking System

A courier and parcel tracking system built with microservices and event-driven architecture, deployed as a single
Spring Boot application so it fits on Render's free tier.

| Layer | Technology |
|---|---|
| Backend | Java 21, Spring Boot 3.4, Spring Data JPA |
| Database | MySQL 8 (Aiven free tier) |
| Messaging | RabbitMQ (CloudAMQP free tier) |
| Frontend | Single-page HTML/JS app (Vercel static site) |
| Build | Maven |
| Hosting | Render (backend) + Vercel (frontend) |

## How this repo is laid out

Each microservice is still a Maven module and can be run on its own. The `app` module compiles all of them into one
Spring Boot application, which is what gets deployed.

| Module | Responsibility |
|---|---|
| `app` | **Deployed artifact.** One Spring Boot app containing everything below |
| `user-service` | Registration, login, roles, customer/courier/admin accounts |
| `parcel-service` | Booking, shipping cost, customer parcel list |
| `tracking-service` | Public tracking and history; consumes parcel events |
| `delivery-service` | Courier assignment, status updates, delay/exception detection |
| `notification-service` | In-app notifications, optional email, support form |
| `payment-service` | Demo checkout orders and admin payment history |
| `gateway` | Spring Cloud Gateway + web UI, for running the services separately |
| `common` | Shared identity and event contracts |
| `frontend` | The web UI, deployed as a static site on Vercel |

The UI calls relative `/api/...` paths. On Vercel a rewrite proxies `/api/*` to the Render service, so the browser only
ever talks to one origin.

## Deploy (free)

Step-by-step walkthrough: **[DEPLOY.md](DEPLOY.md)**. Live site:
https://courier-parcel-tracking-frontend.vercel.app

1. Push this repo to GitHub.
2. Create a free MySQL database on **Aiven** and a free RabbitMQ instance on **CloudAMQP**.
3. On **Render**, create a Blueprint from this repo. It picks up `render.yaml` and builds `Dockerfile`.
4. Copy the backend URL Render gives you into `frontend/vercel.json`, then deploy the `frontend` folder on **Vercel**.
5. Log in at the Vercel URL as `admin@courier.local` with the `ADMIN_PASSWORD` you set on Render.

### Environment variables (set in Render)

| Variable | Where the value comes from |
|---|---|
| `DB_HOST` | Aiven → MySQL → Overview → Host |
| `DB_PORT` | Aiven → Overview → Port (Aiven uses TLS, e.g. `25827`) |
| `DB_NAME` | `defaultdb` (created automatically by Aiven) |
| `DB_USER` | Aiven → Overview → Username (`avnadmin`) |
| `DB_PASSWORD` | Aiven → Overview → Password |
| `DB_SSL_MODE` | `REQUIRED` |
| `RABBIT_HOST` | CloudAMQP → instance → Host |
| `RABBIT_PORT` | CloudAMQP → Port for TLS (usually `5671`) |
| `RABBIT_USERNAME` | CloudAMQP → Username |
| `RABBIT_PASSWORD` | CloudAMQP → Password |
| `RABBIT_VHOST` | CloudAMQP → Virtual Host |
| `RABBIT_SSL_ENABLED` | `true` for the `5671` TLS port |
| `ADMIN_EMAIL` | `admin@courier.local` |
| `ADMIN_PASSWORD` | Pick a strong password. This is the app's admin login |
| `PUBLIC_BASE_URL` | Your Vercel URL, e.g. `https://your-app.vercel.app` |
| `MAIL_ENABLED` | `false` unless you configure SMTP |
| `SUPPORT_EMAIL` | Inbox that receives the support form |

## Run locally with Docker

```powershell
docker compose up --build
```

Open http://localhost:8080. Log in as `admin@courier.local` / `admin12345` (override with `$env:ADMIN_PASSWORD`).

The UI lives in `frontend/index.html`; serve that folder with any static server while developing.

## Notes on the demo data

- The database schema is created automatically on startup (`spring.jpa.hibernate.ddl-auto: update`). All tables land in
  the single database named by `DB_NAME`.
- Online payments are demo-only. Selecting Pay records a successful payment for the displayed fee without contacting a
  bank. `PAYMENT_DEMO_AMOUNT` changes the default fee of INR 100.00. Cash on delivery is recorded as `PAY_ON_DELIVERY`.
- Email delivery is off unless `MAIL_ENABLED=true`. Gmail needs an App Password, not your account password.
- Delay detection uses a one-hour grace period after the expected delivery time. Prolonged inactivity is 24 hours.

## API endpoints

- Registration/login: `POST /api/auth/register`, authenticated `GET /api/auth/login`
- Booking and customer parcels: `POST /api/parcels`, `GET /api/parcels`, `POST /api/parcels/calculate-cost`
- Tracking: public `GET /api/tracking/{trackingId}`; authenticated `GET /api/parcels/{trackingId}/history`
- Delivery: admin `PUT /api/delivery/{trackingId}/assignment`; courier/admin `PUT /api/delivery/{trackingId}/status`; courier `GET /api/delivery/mine`
- Notifications: `GET /api/notifications/mine`, `GET /api/notifications/history`, `POST /api/notifications/mine/clear`
- Payment: `GET /api/payments/{trackingId}`, then `POST /api/payments/{trackingId}/pay` with `{"method":"UPI"}`, `CARD`, `WALLET`, `CASH_ON_DELIVERY` or `DECLINE`
- Admin: `GET /api/admin/users`, `/api/admin/parcels`, `/api/admin/deliveries`, `/api/admin/delayed`, `/api/admin/exceptions`, `/api/admin/activities`, `/api/admin/events/failed`, `/api/admin/email-deliveries`, `/api/payments/admin/all`
- Failed-event recovery: admin `POST /api/admin/events/failed/{id}/retry`
- Support form: authenticated `POST /api/support/contact`
