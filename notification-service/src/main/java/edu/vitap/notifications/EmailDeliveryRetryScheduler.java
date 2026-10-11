package edu.vitap.notifications;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class EmailDeliveryRetryScheduler {
    private final CustomerEmailNotifier emailNotifier;

    EmailDeliveryRetryScheduler(CustomerEmailNotifier emailNotifier) {
        this.emailNotifier = emailNotifier;
    }

    @Scheduled(fixedDelayString = "${notifications.email.retry-interval-ms:60000}")
    void retryFailedDeliveries() {
        emailNotifier.retryFailed();
    }
}