package com.aquaalert;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "app.notifications.email.enabled=true", "app.notifications.email.test-recipient=test@example.test",
        "spring.mail.username=sender@example.test", "spring.mail.password=fake-test-password"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TestEmailControllerTest {
    @Autowired MockMvc mvc;
    @MockBean JavaMailSender sender;

    @Test
    void anonymousCannotSend() throws Exception {
        mvc.perform(post("/api/v1/notifications/test-email")).andExpect(status().isUnauthorized());
        verifyNoInteractions(sender);
    }

    @Test
    @WithMockUser(roles = "OPERATOR")
    void operatorCannotSend() throws Exception {
        mvc.perform(post("/api/v1/notifications/test-email")).andExpect(status().isForbidden());
        verifyNoInteractions(sender);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void arbitraryRecipientsAndMessagesAreRejected() throws Exception {
        mvc.perform(post("/api/v1/notifications/test-email").contentType("application/json")
                .content("{\"to\":\"other@example.test\",\"message\":\"anything\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/notifications/test-email").param("to", "other@example.test"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(sender);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanSendAndImmediateDuplicateIsRejected() throws Exception {
        mvc.perform(post("/api/v1/notifications/test-email"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));
        mvc.perform(post("/api/v1/notifications/test-email"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.success").value(false));
        verify(sender, times(1)).send(any(SimpleMailMessage.class));
    }
}
