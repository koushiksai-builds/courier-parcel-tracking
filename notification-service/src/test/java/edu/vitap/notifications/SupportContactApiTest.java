package edu.vitap.notifications;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import edu.vitap.common.AppUser;
import edu.vitap.common.Role;
import edu.vitap.common.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

class SupportContactApiTest {
    @Test
    void acceptsContactDetailsThatMatchSignedInCustomer() {
        Fixture fixture = new Fixture();
        SupportContactRequest request = new SupportContactRequest(
                "Customer", "customer@example.test", null, "I need help with a booking.");

        SupportContactResponse response = fixture.api.contact(request, fixture.authentication);

        assertEquals("Your support request has been sent successfully. Our support team will contact you soon.",
                response.message());
        verify(fixture.emailSender).send(org.mockito.ArgumentMatchers.eq(request), any());
    }

    @Test
    void rejectsAttemptToUseAnotherCustomersIdentity() {
        Fixture fixture = new Fixture();
        SupportContactRequest request = new SupportContactRequest(
                "Other Person", "other@example.test", null, "I need help.");

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> fixture.api.contact(request, fixture.authentication));

        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
        verify(fixture.emailSender, never()).send(any(), any());
    }

    @Test
    void returnsGenericUnavailableMessageWhenMailSendingFails() {
        Fixture fixture = new Fixture();
        doThrow(new SupportEmailException()).when(fixture.emailSender).send(any(), any());
        SupportContactRequest request = new SupportContactRequest(
                "Customer", "customer@example.test", null, "I need help.");

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> fixture.api.contact(request, fixture.authentication));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.getStatusCode());
        assertFalse(error.getReason().contains("SMTP"));
    }

    private static class Fixture {
        final UserRepository users = org.mockito.Mockito.mock(UserRepository.class);
        final SupportEmailSender emailSender = org.mockito.Mockito.mock(SupportEmailSender.class);
        final Authentication authentication = org.mockito.Mockito.mock(Authentication.class);
        final SupportContactApi api = new SupportContactApi(users, emailSender);

        Fixture() {
            when(authentication.getName()).thenReturn("customer@example.test");
            when(users.findByEmail("customer@example.test"))
                    .thenReturn(Optional.of(new AppUser(
                            "customer@example.test", "encoded", "Customer", Role.CUSTOMER)));
        }
    }
}