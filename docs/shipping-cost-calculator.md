# Dynamic shipping cost calculator

This feature keeps pricing in the existing Parcel Service. The customer enters parcel weight, pickup/delivery addresses, an estimated route distance, and delivery speed. The page requests a quote through the existing API Gateway, then Parcel Service recalculates the amount when it creates the booking. The frontend never supplies a price.

## Pricing

- Base: INR 50
- Weight: INR 0 up to 1 kg; above 1 kg, INR 20 for each additional kilogram or fraction (rounded up)
- Estimated distance: INR 20 through 50 km; INR 40 above 50 through 100 km; INR 70 above 100 through 200 km; INR 100 above 200 km
- STANDARD: no extra charge; SAME_DAY: INR 50 extra

For 2 kg and 75 km: STANDARD is INR 110; SAME_DAY is INR 160.

The existing project has address strings but no coordinates, route-distance field, or map provider. Therefore distance is a customer-entered estimate in kilometers; it is not geocoded or independently verified. Source and destination remain the existing parcel addresses.

## APIs

Both calls go through the gateway at `http://localhost:8081` and require customer Basic Authentication.

`POST /api/parcels/calculate-cost`

```json
{
  "source": "Vijayawada",
  "destination": "Guntur",
  "weightKg": 2,
  "distanceKm": 75,
  "deliverySpeed": "STANDARD"
}
```

The response includes weight, distance, base price, each charge, delivery speed, and `totalShippingCost`. Omit `deliverySpeed` to use STANDARD. Invalid or missing locations, non-positive weight, negative distance, and unsupported speed are rejected with HTTP 400.

Booking remains `POST /api/parcels`; it now requires `weightKg` and `distanceKm`, accepts optional `deliverySpeed` (default STANDARD), and does not accept a price field. It persists the quote on the existing parcel row and includes the calculated `shippingCost` in the existing `parcel.created` RabbitMQ event. Payment Service uses that amount, retaining its old configured demo amount only for older booking events that lack the new field.

Hibernate uses `ddl-auto: update`; it adds nullable `weightKg`, `distanceKm`, `deliverySpeed`, and `shippingCost` columns to the existing parcel table. Existing parcel rows remain readable with those fields null.

## Local checks

From the repository root run:

```powershell
mvn -pl parcel-service,payment-service -am test
mvn -pl parcel-service,payment-service -am package -DskipTests
```

In the existing booking form, sign in as a customer and enter a weight and estimated route distance to see the live breakdown. Payment remains the existing demo flow and does not charge real funds.
