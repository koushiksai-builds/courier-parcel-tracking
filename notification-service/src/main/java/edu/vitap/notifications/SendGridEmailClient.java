package edu.vitap.notifications;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

class SendGridException extends RuntimeException {
    SendGridException(String message) {
        super(message);
    }

    SendGridException(String message, Throwable cause) {
        super(message, cause);
    }
}

record OutboundEmail(String to, String subject, String html, String replyTo) {}

@Component
class SendGridEmailClient {
    private static final URI SEND_URL = URI.create("https://api.sendgrid.com/v3/mail/send");

    private final HttpClient http;
    private final String apiKey;
    private final String from;
    private final String fromName;
    private final Duration timeout;

    SendGridEmailClient(
            @Value("${notifications.email.sendgrid.api-key:}") String apiKey,
            @Value("${notifications.email.from:noreply@courier.local}") String from,
            @Value("${notifications.email.from-name:Courier Parcel Tracking}") String fromName,
            @Value("${notifications.email.sendgrid.timeout-ms:10000}") long timeoutMs) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.from = from;
        this.fromName = fromName;
        this.timeout = Duration.ofMillis(timeoutMs);
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    boolean isConfigured() {
        return !apiKey.isBlank();
    }

    void send(OutboundEmail email) {
        if (!isConfigured()) {
            throw new SendGridException("SendGrid API key is not configured");
        }
        if (email == null || email.to() == null || email.to().isBlank()) {
            throw new SendGridException("Recipient address is missing");
        }
        HttpRequest request = HttpRequest.newBuilder(SEND_URL)
                .timeout(timeout)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload(email), StandardCharsets.UTF_8))
                .build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            int code = response.statusCode();
            if (code < 200 || code >= 300) {
                throw new SendGridException("SendGrid rejected the message with HTTP " + code);
            }
        } catch (SendGridException ex) {
            throw ex;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new SendGridException("Interrupted while sending email", ex);
        } catch (Exception ex) {
            throw new SendGridException("Could not reach SendGrid", ex);
        }
    }

    private String payload(OutboundEmail email) {
        StringBuilder json = new StringBuilder(256);
        json.append("{\"personalizations\":[{\"to\":[{\"email\":").append(quote(email.to())).append("}]}],");
        json.append("\"from\":{\"email\":").append(quote(from)).append(",\"name\":").append(quote(fromName)).append("},");
        if (email.replyTo() != null && !email.replyTo().isBlank()) {
            json.append("\"reply_to\":{\"email\":").append(quote(email.replyTo().trim())).append("},");
        }
        json.append("\"subject\":").append(quote(email.subject())).append(',');
        json.append("\"content\":[{\"type\":\"text/html\",\"value\":").append(quote(email.html())).append("}]}");
        return json.toString();
    }

    private String quote(String value) {
        String raw = value == null ? "" : value;
        StringBuilder out = new StringBuilder(raw.length() + 2);
        out.append('"');
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }
}
