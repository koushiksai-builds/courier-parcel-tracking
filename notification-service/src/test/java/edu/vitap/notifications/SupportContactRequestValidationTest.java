package edu.vitap.notifications;

import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class SupportContactRequestValidationTest {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void rejectsInvalidEmailAndMissingMessage() {
        SupportContactRequest request =
                new SupportContactRequest("Customer", "not-an-email", null, " ");
        Set<String> invalidFields = validator.validate(request).stream()
                .map(error -> error.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertTrue(invalidFields.contains("email"));
        assertTrue(invalidFields.contains("message"));
    }

    @Test
    void rejectsMessagesOverConfiguredLimit() {
        SupportContactRequest request =
                new SupportContactRequest("Customer", "customer@example.test", null, "x".repeat(4001));

        assertTrue(validator.validate(request).stream()
                .anyMatch(error -> error.getPropertyPath().toString().equals("message")));
    }
}
