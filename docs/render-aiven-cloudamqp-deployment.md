# Deploying on Render with Aiven MySQL and CloudAMQP

The services now take their database, RabbitMQ, server-port, and gateway destinations from environment variables. Local Compose can continue using the local MySQL and RabbitMQ containers. For the cloud deployment, put real credentials in the Render Dashboard; do not commit them in `.env`, `render.yaml`, or Java/YAML source.

## 1. Prepare Aiven MySQL

1. Create and start an Aiven for MySQL service.
2. In the Aiven Console, open **Connect → Databases** and create these six databases:

   - `courier_users`
   - `courier_parcels`
   - `courier_tracking`
   - `courier_delivery`
   - `courier_notifications`
   - `courier_payments`

3. Create/use an Aiven database user with access to all six databases. The user needs table creation and normal read/write privileges because Hibernate uses `ddl-auto: update`. Services also read `app_users` from `courier_users` for authorization, so grant access to that database to every service user.
4. Copy the host, port, username, and password from Aiven's **Connection information**. The app constructs TLS JDBC URLs using `DB_SSL_MODE=REQUIRED`.
5. If Aiven's IP filter is restricted, allow the outbound CIDR ranges shown under **Connect → Outbound** for your Render service/region. Avoid opening the database to all IPs for production.

## 2. Prepare CloudAMQP

Create a RabbitMQ instance and copy its AMQP URL from the instance overview. Set the scheme to `amqps://` (TLS). Use the full URL including its virtual-host path; do not separately split username/password. If the credentials contain reserved URL characters, use CloudAMQP's generated/encoded URL.

## 3. Create Render services

Deploy each module from the same repository as a Docker service. Render's Docker environment variables are also available as build arguments, and this Dockerfile uses `APP_MODULE` to build only that module and its Maven dependencies.

Create these six **Private Services** and one public **Web Service**, all in the same Render region:

| Render service | `APP_MODULE` | Internal `PORT` |
|---|---|---:|
| user service | `user-service` | `8081` |
| parcel service | `parcel-service` | `8082` |
| tracking service | `tracking-service` | `8083` |
| delivery service | `delivery-service` | `8084` |
| notification service | `notification-service` | `8085` |
| payment service | `payment-service` | `8086` |
| gateway (public Web Service) | `gateway` | use Render's `PORT` (normally `10000`) |

For each service, use the repository root as the Docker context and `./Dockerfile` as its Dockerfile. Set the corresponding `APP_MODULE` environment variable. Set `PORT` to the listed value for each private service; let Render provide `PORT` for the gateway.

Add these Aiven variables to every service that connects to MySQL:

```text
DB_HOST=<Aiven hostname>
DB_PORT=<Aiven port>
DB_USER=<Aiven user>
DB_PASSWORD=<Aiven password>
DB_SSL_MODE=REQUIRED
```

Add this to parcel, tracking, delivery, notification, and payment services:

```text
RABBITMQ_URL=amqps://<CloudAMQP-generated connection URL>
```

The user service does not use RabbitMQ. The notification service also needs `MAIL_ENABLED`, `SENDGRID_API_KEY`, `MAIL_FROM`, `MAIL_FROM_NAME`, and `SUPPORT_EMAIL` if you want email notifications/support to work in production. Delivery goes through the SendGrid HTTPS API; Render free instances block outbound SMTP.

In the gateway's environment, set each `*_HOSTPORT` variable to the private address and port shown by Render for the matching service (format `hostname:port`, with no `http://`):

```text
USER_SERVICE_HOSTPORT=<user-service-private-host>:8081
PARCEL_SERVICE_HOSTPORT=<parcel-service-private-host>:8082
TRACKING_SERVICE_HOSTPORT=<tracking-service-private-host>:8083
DELIVERY_SERVICE_HOSTPORT=<delivery-service-private-host>:8084
NOTIFICATION_SERVICE_HOSTPORT=<notification-service-private-host>:8085
PAYMENT_SERVICE_HOSTPORT=<payment-service-private-host>:8086
```

Render's private network is for service-to-service traffic; keep the microservices private and expose only the gateway. Use the public gateway URL in your browser.

## 4. Verify deployment

1. Deploy the six private services, then deploy/redeploy the gateway after setting all six private addresses.
2. Check each Render service's deploy/runtime logs. Database failures usually indicate a wrong Aiven host/port/database/user/password, missing schema, missing grants, or a blocked Render outbound IP. RabbitMQ failures usually indicate an incorrect AMQPS URL/vhost or an allowlist issue.
3. Open the gateway's public `https://...onrender.com` URL. Test sign-in, booking, tracking, status updates, and payment. Check CloudAMQP's connections/queues and Aiven's query/activity view while exercising event-driven flows.

## Local Compose

`docker compose up --build -d` continues to use its local `mysql` and `rabbitmq` services and local database URLs. The cloud variables above are for services deployed on Render. Do not set `RABBITMQ_URL` in a local Compose run unless you also intend the app containers to connect to CloudAMQP.

## Provider references

- [Render Docker deployments and environment variables](https://render.com/docs/docker)
- [Render private networking](https://render.com/docs/private-network)
- [Render outbound IP ranges](https://render.com/docs/outbound-ip-addresses)
- [CloudAMQP Java connection details](https://www.cloudamqp.com/docs/java/)
- [CloudAMQP TLS](https://www.cloudamqp.com/docs/faq/)
- [Aiven MySQL Java connection](https://aiven.io/docs/products/mysql/howto/connect-with-java)
- [Create Aiven MySQL databases](https://aiven.io/docs/products/mysql/howto/create-database)
