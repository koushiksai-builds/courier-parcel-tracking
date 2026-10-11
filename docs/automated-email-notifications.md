# Automated Email Notifications

Feature 1 adds optional email notifications while retaining the existing in-app notifications and history. The Notification Service consumes parcel booking and delivery updates from RabbitMQ and payment success or simulated decline events from the same exchange. Every event has an event ID; `courier_notifications.email_delivery_history` stores the recipient, event type and ID, status, attempt count, and error details. Duplicate event IDs are ignored. The existing notification table also records its event ID to avoid duplicate in-app entries on redelivery.

Email is disabled unless `MAIL_ENABLED=true` and a working SMTP host and credentials are configured. The Compose default is false, so unset SMTP values intentionally result in no email being sent. The repository includes a secret-free `.env.example` for Gmail SMTP setup.

Email is disabled by default. To enable it, create a private `.env` from the safe template, replace the Gmail placeholders, then start the stack from the project root. Gmail requires an App Password where available; do not use your normal account password.

```powershell
if (-not (Test-Path .env)) { Copy-Item .env.example .env }
notepad .env
# Save your actual SMTP values in .env, then:
docker compose -f compose.yaml up --build -d
docker compose -f compose.yaml ps
docker compose -f compose.yaml logs -f notification-service
```

Do not commit `.env`; it is ignored by Git. If another copy of this project is already using the same ports, stop that Compose stack from its own project folder first with `docker compose -f compose.yaml down` (do not add `-v`, which would delete its database volume).

The Notification Service records a failed send without rejecting the RabbitMQ event. It retries failed email deliveries every `MAIL_RETRY_INTERVAL_MS` while attempts are below `MAIL_MAX_ATTEMPTS`. Administrators can inspect history at `GET /api/admin/email-deliveries` and request a retry with `POST /api/admin/email-deliveries/{id}/retry`. A retry updates the existing event row; it does not create another history row. A delivery already marked `SENT` cannot be manually retried.

Payment checkout accepts the existing `UPI`, `CARD`, `WALLET`, and `CASH_ON_DELIVERY` methods. For local testing, `{"method":"DECLINE"}` records `DECLINED` with method `SIMULATED_DECLINE`, then publishes a payment-declined event. Payment event publishing is best-effort; SMTP delivery occurs later in Notification Service and is not part of parcel, delivery, or payment request processing.

## Manual Verification

1. Start the services with a working SMTP relay and `MAIL_ENABLED=true`. Register a customer using an address you can inspect, then book a parcel and move it through a delivery status update. Confirm the booking and delivery emails arrive and `GET /api/admin/email-deliveries` shows `SENT` entries with distinct event IDs.
2. Sign in as the customer and pay a pending order with `UPI` (or another online method). Confirm a payment-success email. To test a decline, call the same authenticated `POST /api/payments/{trackingId}/pay` endpoint with `{"method":"DECLINE"}` for a separate pending order; confirm a decline email and `DECLINED` payment history.
3. To test retry behavior, temporarily point SMTP at an unavailable host/port, generate a new notification event, and confirm the admin history row becomes `FAILED` with an incremented attempt count and error details. Restore working SMTP settings and either wait one configured retry interval or call the admin retry endpoint for that row; verify the same row becomes `SENT` and its attempt count increases.
4. To test duplicate suppression, publish the same JSON event twice through RabbitMQ Management at `http://localhost:15672` to exchange `parcel.events` with routing key `parcel.created`. Replace the customer ID with a real customer ID, and publish the exact same body (including event ID) both times:

	 ```json
	 {
		 "trackingId": "CP123456789012",
		 "customerId": 1,
		 "status": "BOOKED",
		 "note": "Parcel booked",
		 "expectedDelivery": null,
		 "occurredAt": "2026-10-10T12:00:00Z",
		 "eventId": "manual-replay-001"
	 }
	 ```

	 Confirm only one email history row and one in-app notification exist for `manual-replay-001`.

Spring JPA `ddl-auto: update` adds the nullable `event_id` column to existing in-app notification rows and creates the `email_delivery_history` table in `courier_notifications`; no existing notification/history rows are intentionally removed.
