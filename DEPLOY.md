# Deployment

How this project went from a 7-service local setup to a free public site.

## What runs where

```
User's browser
   |
   v
https://courier-parcel-tracking-frontend.vercel.app     <- Vercel, static frontend
   |  /api/* is rewritten to
   v
https://courier-parcel-tracking-1.onrender.com          <- Render, one Spring Boot app (free, 512MB)
   |                    |
   v                    v
Aiven MySQL (free)    CloudAMQP RabbitMQ (free)
```

The browser only ever talks to Vercel. Vercel proxies `/api/*` to Render, so
there is no CORS and no need to expose the backend URL to users.

**Public URL to share: `https://courier-parcel-tracking-frontend.vercel.app`**

## Why one app instead of seven

The original layout was an API gateway, a user service, a parcel service, a
tracking service, a delivery service, a notification service and a payment
service. That needs seven ports and more memory than a free instance has.

The `app` module compiles all six services into one Spring Boot application
using `build-helper-maven-plugin`. Each service is still its own Maven module,
so they can be split apart again later — nothing was rewritten.

Measured peak: **312 MiB** of Render's 512 MiB limit.

## The five steps

### 1. Push to GitHub

The repo is public, so no credentials are committed. Real secrets were removed
before the first push — the old `src/main/resources/application.yml` had live
Aiven and CloudAMQP passwords hard-coded and that file was deleted.

### 2. Aiven MySQL (free, no credit card)

Create a MySQL service on the Hobbyist plan. Aiven creates `defaultdb`
automatically and sets the IP allowlist to open. Copy the host, port, user and
password.

All nine tables land in that one database. The old code had each service
pointing at its own MySQL *schema* via `@Table(catalog = ...)`; those
attributes were removed so everything shares `defaultdb`.

### 3. CloudAMQP (free, no credit card)

Create a "Loyal Lemming" / "Little Lemur" instance. Use the **AMQP** port for
TLS (5671), not the MQTT port. The AMQP username is just `xleszxzc` — the
`xleszxzc:xleszxzc` form shown under MQTT is MQTT-only.

### 4. Render (free web service)

Service created from the repo root `Dockerfile`. `render.yaml` pins the free
plan and sets the non-secret defaults. Because the repo is public, the secret
values are **not** in `render.yaml` — they are set in the dashboard under
Environment, which is why they show as `sync: false` in that file.

Environment variables:

| Variable | Value |
|---|---|
| `DB_HOST` | Aiven host, e.g. `mysql-....i.aivencloud.com` |
| `DB_PORT` | Aiven port, e.g. `25827` |
| `DB_NAME` | `defaultdb` |
| `DB_USER` | `avnadmin` |
| `DB_PASSWORD` | Aiven password |
| `DB_SSL_MODE` | `REQUIRED` |
| `RABBIT_HOST` | CloudAMQP cluster host |
| `RABBIT_PORT` | `5671` (TLS) |
| `RABBIT_USERNAME` | CloudAMQP AMQP user |
| `RABBIT_PASSWORD` | CloudAMQP password |
| `RABBIT_VHOST` | CloudAMQP virtual host |
| `RABBIT_SSL_ENABLED` | `true` (because of port 5671) |
| `ADMIN_EMAIL` | `admin@courier.local` |
| `ADMIN_PASSWORD` | admin login for the app |
| `PUBLIC_BASE_URL` | the Vercel URL |
| `MAIL_ENABLED` | `false` unless SMTP is configured |
| `SUPPORT_EMAIL` | inbox for the support form |

If these are missing the app falls back to `localhost` and dies with
`java.net.ConnectException: Connection refused` — that is what the first
failed deploy looked like.

### 5. Vercel (free static site)

`frontend/` is imported as its own Vercel project with **Root Directory** set
to `frontend`. `frontend/vercel.json` does the work:

```json
{
  "rewrites": [
    {
      "source": "/api/:path*",
      "destination": "https://courier-parcel-tracking-1.onrender.com/api/:path*"
    }
  ]
}
```

Pushing to `main` redeploys both services automatically.

## Running locally

```powershell
docker compose up --build
```

Then open http://localhost:8080 and log in as `admin@courier.local` /
`admin12345`.

## Things to know

- **Render free spins down after 15 minutes of no traffic.** The first request
  after that takes ~50 seconds. Subsequent requests are normal.
- **CloudAMQP deletes free instances after 2 months with no messages.** Use the
  app occasionally or send a test parcel monthly.
- **Email is off by default.** Setting `MAIL_ENABLED=true` needs a Gmail App
  Password, not the account password.
- **Payments are demo-only.** Selecting Pay records a successful payment for
  the displayed fee without contacting a bank.
- The admin account is created on first startup from `ADMIN_EMAIL` and
  `ADMIN_PASSWORD`.
