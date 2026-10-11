-- Store ParcelStatus as VARCHAR so new states such as CANCELLED fit all services.
ALTER TABLE courier_delivery.delivery_records
    MODIFY COLUMN status VARCHAR(32) NOT NULL;
ALTER TABLE courier_parcels.parcels
    MODIFY COLUMN status VARCHAR(32) NOT NULL;
ALTER TABLE courier_tracking.tracking_parcels
    MODIFY COLUMN status VARCHAR(32) NOT NULL;
ALTER TABLE courier_tracking.tracking_history
    MODIFY COLUMN status VARCHAR(32) NOT NULL;
