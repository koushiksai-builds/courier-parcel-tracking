package edu.vitap.notifications;

import edu.vitap.common.AppUser;
import edu.vitap.common.CurrentUser;
import edu.vitap.common.UserRepository;
import jakarta.mail.internet.MimeMessage;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

record SupportContactRequest(
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Email @Size(max = 254) String email,
        @Size(max = 80) @Pattern(regexp = "^[A-Za-z0-9._-]*$") String trackingId,
        @NotBlank @Size(max = 4000) String message) {}

record SupportContactResponse(String message, Instant sentAt) {}

@RestController
@RequestMapping("/api/support")
class SupportContactApi {
    private static final String SUCCESS_MESSAGE =
            "Your support request has been sent successfully. Our support team will contact you soon.";

    private final UserRepository users;
    private final SupportEmailSender emailSender;

    SupportContactApi(UserRepository users, SupportEmailSender emailSender) {
        this.users = users;
        this.emailSender = emailSender;
    }

    @PostMapping("/contact")
    @PreAuthorize("hasRole('CUSTOMER')")
    SupportContactResponse contact(@Valid @RequestBody SupportContactRequest request,
                                   Authentication authentication) {
        AppUser customer = CurrentUser.get(users, authentication);
        boolean sameEmail = customer.getEmail() != null
                && customer.getEmail().trim().equalsIgnoreCase(request.email().trim());
        boolean sameName = customer.getName() != null
                && customer.getName().trim().equalsIgnoreCase(request.name().trim());
        if (!sameEmail || !sameName) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Contact name and email must match your signed-in account");
        }

        Instant sentAt = Instant.now();
        try {
            emailSender.send(request, sentAt);
        } catch (SupportEmailException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Support email is temporarily unavailable. Please try again later.");
        }
        return new SupportContactResponse(SUCCESS_MESSAGE, sentAt);
    }
}

@Component
class SupportEmailSender {
    private static final Logger log = LoggerFactory.getLogger(SupportEmailSender.class);

    private final String recipient;
    private final String from;
    private final ObjectProvider<JavaMailSender> mailSenders;

    SupportEmailSender(
            @Value("${support.email.recipient:}") String recipient,
            @Value("${notifications.email.from:noreply@courier.local}") String from,
            ObjectProvider<JavaMailSender> mailSenders) {
        this.recipient = recipient == null ? "" : recipient.trim();
        this.from = from;
        this.mailSenders = mailSenders;
    }

    void send(SupportContactRequest request, Instant requestedAt) {
        if (recipient.isBlank()) {
            log.error("Support email is not configured; set SUPPORT_EMAIL");
            throw new SupportEmailException();
        }
        JavaMailSender mailSender = mailSenders.getIfAvailable();
        if (mailSender == null) {
            log.error("SMTP mail sender is not configured for support requests");
            throw new SupportEmailException();
        }

        String trackingId = request.trackingId() == null ? "" : request.trackingId().trim();
        if (trackingId.isEmpty()) trackingId = null;
        String subject = trackingId == null
                ? "Customer Support Request"
                : "Customer Support Request - " + trackingId;
        String html = render(request, trackingId, requestedAt);
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(from);
            helper.setTo(recipient);
            helper.setReplyTo(request.email().trim());
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(message);
        } catch (Exception ex) {
            log.error("Failed to send customer support email", ex);
            throw new SupportEmailException();
        }
    }

    private String render(SupportContactRequest request, String trackingId, Instant requestedAt) {
        String trackingValue = trackingId == null ? "Not provided" : trackingId;
        return "<!doctype html><html><body style=\"font-family:Arial,Helvetica,sans-serif;color:#14213d\">"
                + "<h2>Customer Support Request</h2>"
                + "<table cellpadding=\"8\" cellspacing=\"0\" style=\"border-collapse:collapse\">"
                + row("Customer name", request.name().trim())
                + row("Customer email", request.email().trim())
                + row("Tracking ID", trackingValue)
                + row("Date/time (UTC)", requestedAt.toString())
                + "</table><h3>Issue / message</h3><p style=\"white-space:pre-wrap\">"
                + escape(request.message().trim()) + "</p></body></html>";
    }

    private String row(String label, String value) {
        return "<tr><th align=\"left\">" + escape(label) + "</th><td>" + escape(value) + "</td></tr>";
    }

    private String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}

class SupportEmailException extends RuntimeException {
    SupportEmailException() {
        super("Support email delivery is unavailable");
    }
}
