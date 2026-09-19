package com.cliet_tableaux.api.core.controllers;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cliet_tableaux.api.core.exceptions.ResourceNotFoundException;
import com.cliet_tableaux.api.core.services.OrderService;
import com.cliet_tableaux.api.core.services.WebhookService;
import com.stripe.exception.SignatureVerificationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class OrderControllerWebhookTest {

    @InjectMocks
    private OrderController orderController;

    private MockMvc mockMvc;

    @Mock
    private OrderService orderService;

    @Mock
    private WebhookService webhookService;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(orderController).build();
    }

    @Test
    void handleStripeWebhook_withInvalidSignature_returns400() throws Exception {
        doThrow(new SignatureVerificationException("bad signature", "sig_header"))
                .when(webhookService).handleWebhook(anyString(), anyString());

        mockMvc.perform(post("/api/v1/orders/stripe-webhooks")
                        .header("Stripe-Signature", "sig_header")
                        .content("payload"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void handleStripeWebhook_withUnexpectedError_returns500() throws Exception {
        doThrow(new ResourceNotFoundException("Order id cs_test_x non trouvée"))
                .when(webhookService).handleWebhook(anyString(), anyString());

        mockMvc.perform(post("/api/v1/orders/stripe-webhooks")
                        .header("Stripe-Signature", "sig_header")
                        .content("payload"))
                .andExpect(status().isInternalServerError());
    }
}
