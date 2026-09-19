package com.cliet_tableaux.api.core.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import com.cliet_tableaux.api.core.daos.OrderDao;
import com.cliet_tableaux.api.core.daos.PaintingDao;
import com.cliet_tableaux.api.core.daos.UserDao;
import com.cliet_tableaux.api.core.enums.PaymentStatutEnum;
import com.cliet_tableaux.api.core.model.Order;
import com.cliet_tableaux.api.core.model.Painting;
import com.cliet_tableaux.api.core.model.User;
import java.time.Instant;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class WebhookServiceIntegrationTest {
    private static final String WEBHOOK_SECRET = "whsec_dummy";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private WebhookService webhookService;

    @Autowired
    private OrderDao orderDao;

    @Autowired
    private UserDao userDao;

    @Autowired
    private PaintingDao paintingDao;

    private String buildSignatureHeader(String payload) throws Exception {
        long timestamp = Instant.now().getEpochSecond();
        String signedPayload = timestamp + "." + payload;

        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(WEBHOOK_SECRET.getBytes(), "HmacSHA256"));
        byte[] rawHmac = mac.doFinal(signedPayload.getBytes());

        StringBuilder hex = new StringBuilder();
        for (byte b : rawHmac) {
            hex.append(String.format("%02x", b));
        }

        return "t=" + timestamp + ",v1=" + hex;
    }

    @Test
    void handleWebhook_checkoutSessionCompleted_survivesApiVersionMismatch() throws Exception {
        User user = new User();
        user.setEmail("webhook-buyer@example.com");
        user.setPassword("hashed-password");
        user = userDao.save(user);

        Painting painting = new Painting();
        painting.setTitle("Tableau webhook api_version");
        painting.setDescription("Description");
        painting.setSell(false);
        painting.setPriceCents(1000L);
        painting = paintingDao.save(painting);

        Order order = Order.create(1000L, "1 rue de Paris", "EUR", user, painting);
        order.setCheckoutSessionId("cs_test_apiversion_mismatch");
        order = orderDao.save(order);
        Long orderId = order.getId();

        String payload = "{"
                + "\"id\": \"evt_test_apiversion\","
                + "\"object\": \"event\","
                + "\"api_version\": \"2023-10-16\","
                + "\"type\": \"checkout.session.completed\","
                + "\"data\": { \"object\": {"
                + "  \"id\": \"cs_test_apiversion_mismatch\","
                + "  \"object\": \"checkout.session\","
                + "  \"payment_status\": \"paid\","
                + "  \"mode\": \"payment\""
                + "} } }";

        String sigHeader = buildSignatureHeader(payload);

        assertDoesNotThrow(() -> webhookService.handleWebhook(payload, sigHeader));

        assertThat(orderDao.findById(orderId).orElseThrow().getState()).isEqualTo(PaymentStatutEnum.PROCESSING);
    }
}
