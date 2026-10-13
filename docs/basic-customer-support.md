# Basic Customer Support

Customers can send a support request from the Customer Support card after signing in. The Notification Service sends it to the configured support inbox through the existing SMTP connection. No ticket or database record is created.

## Configuration

Set SUPPORT_EMAIL in the private project-root .env file to the support inbox. Keep .env private and out of Git. The source-controlled .env.example contains placeholders only. Email delivery uses the SendGrid HTTPS API (`SENDGRID_API_KEY`, `MAIL_ENABLED`, `MAIL_FROM`, `MAIL_FROM_NAME`). Render free instances cannot use SMTP.

After changing configuration, rebuild and restart the gateway and notification service:

    docker compose -f compose.yaml up --build -d notification-service gateway
    docker compose -f compose.yaml ps

## API

POST /api/support/contact requires an authenticated CUSTOMER account. Submitted name and email must match the signed-in account. trackingId is optional. Message length is limited to 4,000 characters.

Example JSON: {"name":"Customer Name","email":"customer@example.com","trackingId":"CP123456789012","message":"My parcel status has not changed since yesterday."}

Expected success message: Your support request has been sent successfully. Our support team will contact you soon.

Omit trackingId or send null when the request is not tied to a parcel. A missing message or invalid email returns 400; contact details that do not match the signed-in customer return 403. Missing support/SMTP configuration or SMTP failure returns a generic 503; internal SMTP details are not sent to the customer.

## Testing

Use Postman with Basic Auth credentials for a customer and POST the example JSON to http://localhost:8081/api/support/contact. To check validation, omit message or set email to an invalid value; expect 400. To check identity protection, change the submitted email; expect 403. Check the configured SUPPORT_EMAIL inbox, including Spam, for the message. The subject includes the tracking ID when present and otherwise is Customer Support Request.
