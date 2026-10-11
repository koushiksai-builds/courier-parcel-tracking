package edu.vitap.notifications;

import edu.vitap.common.AppUser;
import edu.vitap.common.ParcelEvent;
import edu.vitap.common.PaymentNotificationEvent;
import edu.vitap.common.UserRepository;
import jakarta.mail.internet.MimeMessage;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Component
class CustomerEmailNotifier {
    private final boolean enabled;
    private final String from;
    private final String publicBaseUrl;
    private final UserRepository users;
    private final EmailDeliveryRepository deliveries;
    private final ObjectProvider<JavaMailSender> mailSenders;
    private final int maxAttempts;
    private final String smtpUsername;
    private final String smtpPassword;

    CustomerEmailNotifier(
            @Value("${notifications.email.enabled:false}") boolean enabled,
            @Value("${notifications.email.from:noreply@courier.local}") String from,
            @Value("${notifications.public-base-url:http://localhost:8081}") String publicBaseUrl,
            UserRepository users,
            EmailDeliveryRepository deliveries,
            ObjectProvider<JavaMailSender> mailSenders,
            @Value("${notifications.email.max-attempts:8}") int maxAttempts,
            @Value("${spring.mail.username:}") String smtpUsername,
            @Value("${spring.mail.password:}") String smtpPassword) {
        this.enabled = enabled;
        this.from = from;
        this.publicBaseUrl = publicBaseUrl;
        this.users = users;
        this.deliveries = deliveries;
        this.mailSenders = mailSenders;
        this.maxAttempts = maxAttempts;
        this.smtpUsername = smtpUsername;
        this.smtpPassword = smtpPassword;
    }

    @Transactional
    void send(ParcelEvent event) {
        if (!enabled) return;
        String note = event.note() == null ? "Parcel status updated" : event.note();
        boolean created = "Parcel booked".equalsIgnoreCase(note);
        String type = created ? "PARCEL_BOOKED" : "DELIVERY_UPDATE";
        String subject = (created ? "Your parcel is booked: " : "Delivery update: ") + event.trackingId();
        Map<String, String> details = new LinkedHashMap<>();
        details.put("Tracking ID", event.trackingId());
        details.put("Status", event.status() == null ? "Not provided" : event.status().name());
        details.put("Update", note);
        details.put("Expected delivery", display(event.expectedDelivery()));
        details.put("Updated at", display(event.occurredAt()));
        enqueue(event.eventId(), type, event.customerId(), event.trackingId(), subject,
                created ? "Your parcel has been booked successfully." : "There is a new update on your delivery.", details);
    }

    @Transactional
    void send(PaymentNotificationEvent event) {
        if (!enabled) return;
        boolean success = "SUCCESS".equalsIgnoreCase(event.status());
        String type = success ? "PAYMENT_SUCCESS" : "PAYMENT_DECLINED";
        String subject = (success ? "Payment received: " : "Payment declined: ") + event.trackingId();
        Map<String, String> details = new LinkedHashMap<>();
        details.put("Tracking ID", event.trackingId());
        details.put("Payment status", event.status());
        details.put("Method", event.method());
        details.put("Amount", event.amount() + " " + event.currency());
        details.put("Updated at", display(event.occurredAt()));
        enqueue(event.eventId(), type, event.customerId(), event.trackingId(), subject,
                success ? "Your payment was received successfully." : "Your simulated payment was declined. No payment was collected.", details);
    }

    @Transactional
    void retryFailed() {
        if (!enabled) return;
        for (EmailDelivery delivery : deliveries.findByStatusAndAttemptsLessThanOrderByUpdatedAtAsc("FAILED", maxAttempts)) {
            attempt(delivery);
        }
    }

    @Transactional
    void retry(long id) {
        EmailDelivery delivery = deliveries.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Email delivery not found"));
        if (!"SENT".equals(delivery.status)) attempt(delivery);
    }

    private void enqueue(String eventId, String eventType, Long customerId, String trackingId, String subject,
                         String summary, Map<String, String> details) {
        if (deliveries.existsByEventId(eventId)) return;
        AppUser customer = users.findById(customerId).orElse(null);
        String recipient = customer == null || customer.getEmail() == null ? "" : customer.getEmail();
        EmailDelivery delivery = new EmailDelivery(eventId, eventType, customerId, recipient, trackingId, subject,
                render(subject, summary, details));
        deliveries.saveAndFlush(delivery);
        attempt(delivery);
    }

    private void attempt(EmailDelivery delivery) {
        delivery.attempts++;
        delivery.updatedAt = Instant.now();
        delivery.errorDetails = null;
        try {
            if (delivery.recipient.isBlank()) {
                AppUser customer = users.findById(delivery.customerId).orElse(null);
                if (customer == null || customer.getEmail() == null || customer.getEmail().isBlank()) {
                    throw new IllegalStateException("Customer email address is unavailable");
                }
                delivery.recipient = customer.getEmail();
            }
            JavaMailSender mailSender = mailSenders.getIfAvailable();
            if (mailSender == null) throw new IllegalStateException("SMTP mail sender is not configured");
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            helper.setFrom(from);
            helper.setTo(delivery.recipient);
            helper.setSubject(delivery.subject);
            helper.setText(delivery.htmlBody, true);
            mailSender.send(message);
            delivery.status = "SENT";
            delivery.sentAt = Instant.now();
        } catch (Exception ex) {
            delivery.status = "FAILED";
            delivery.errorDetails = errorDetails(ex);
        }
        deliveries.save(delivery);
    }

    private String render(String title, String summary, Map<String, String> details) {
        StringBuilder rows = new StringBuilder();
        details.forEach((label, value) -> rows.append("<tr><th align=\"left\" style=\"padding:10px 12px;border-bottom:1px solid #e5e7eb;color:#475569;font-weight:600\">")
                .append(escape(label)).append("</th><td style=\"padding:10px 12px;border-bottom:1px solid #e5e7eb;color:#0f172a\">")
                .append(escape(value)).append("</td></tr>"));
        return "<!doctype html><html><body style=\"margin:0;background:#f1f5f9;font-family:Arial,Helvetica,sans-serif;color:#0f172a\"><table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\"><tr><td align=\"center\" style=\"padding:32px 16px\"><table role=\"presentation\" width=\"600\" style=\"max-width:600px;background:#ffffff;border:1px solid #dbe3eb;border-radius:8px\" cellspacing=\"0\" cellpadding=\"0\"><tr><td style=\"padding:24px 28px;background:#123b4a;color:#ffffff;font-size:14px;font-weight:700\">COURIER PARCEL SERVICE</td></tr><tr><td style=\"padding:28px\"><h1 style=\"margin:0 0 12px;font-size:22px;line-height:1.3\">" + escape(title) + "</h1><p style=\"margin:0 0 20px;line-height:1.6;color:#475569\">" + escape(summary) + "</p><table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" style=\"border-collapse:collapse\">" + rows + "</table><p style=\"margin:24px 0 0\"><a href=\"" + escape(publicBaseUrl) + "\" style=\"display:inline-block;padding:11px 16px;background:#0f766e;color:#ffffff;text-decoration:none;border-radius:4px;font-weight:700\">View your parcel</a></p></td></tr><tr><td style=\"padding:16px 28px;border-top:1px solid #e5e7eb;color:#64748b;font-size:12px\">This is an automated service message. Please do not reply to this email.</td></tr></table></td></tr></table></body></html>";
    }

    private String errorDetails(Exception ex) {
        String message = ex.getMessage() == null ? "No additional error details" : ex.getMessage();
        if (!smtpUsername.isBlank()) message = message.replace(smtpUsername, "[redacted]");
        if (!smtpPassword.isBlank()) message = message.replace(smtpPassword, "[redacted]");
        String details = ex.getClass().getSimpleName() + ": " + message;
        return details.substring(0, Math.min(2000, details.length()));
    }

    private String display(Object value) {
        return value == null ? "Not provided" : value.toString();
    }

    private String escape(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
