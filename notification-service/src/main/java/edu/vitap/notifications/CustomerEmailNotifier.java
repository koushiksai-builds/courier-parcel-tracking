package edu.vitap.notifications;

import edu.vitap.common.AppUser;
import edu.vitap.common.ParcelEvent;
import edu.vitap.common.UserRepository;
import java.time.Instant;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
class CustomerEmailNotifier {
    private final boolean enabled;
    private final String from;
    private final String publicBaseUrl;
    private final UserRepository users;
    private final ObjectProvider<JavaMailSender> mailSenders;

    CustomerEmailNotifier(
            @Value("${notifications.email.enabled:false}") boolean enabled,
            @Value("${notifications.email.from:noreply@courier.local}") String from,
            @Value("${notifications.public-base-url:http://localhost:8081}") String publicBaseUrl,
            UserRepository users,
            ObjectProvider<JavaMailSender> mailSenders) {
        this.enabled = enabled;
        this.from = from;
        this.publicBaseUrl = publicBaseUrl;
        this.users = users;
        this.mailSenders = mailSenders;
    }

    void send(ParcelEvent event) {
        if (!enabled) return;

        AppUser customer = users.findById(event.customerId()).orElse(null);
        if (customer == null) return;

        JavaMailSender mailSender = mailSenders.getIfAvailable();
        if (mailSender == null) {
            throw new IllegalStateException("Email is enabled but no SMTP mail sender is configured");
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(customer.getEmail());
        String note = event.note() == null ? "Parcel status updated" : event.note();
        boolean created = "Parcel booked".equalsIgnoreCase(note);
        message.setSubject(created
                ? "Your parcel was created: " + event.trackingId()
                : "Parcel status update: " + event.trackingId());
        message.setText((created
                ? "Your parcel was created and booked successfully."
                : "Your parcel has a new delivery status update.") + "\n\n"
                + "Tracking ID: " + event.trackingId() + "\n"
                + "Status: " + event.status() + "\n"
                + "Update: " + note + "\n"
                + "Updated at: " + (event.occurredAt() == null ? Instant.now() : event.occurredAt()) + "\n\n"
                + "Track your parcel: " + publicBaseUrl);
        mailSender.send(message);
    }
}
