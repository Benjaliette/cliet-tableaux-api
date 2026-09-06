package com.cliet_tableaux.api.core.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;

import com.cliet_tableaux.api.core.daos.OrderDao;
import com.cliet_tableaux.api.core.daos.PaintingDao;
import com.cliet_tableaux.api.core.daos.UserDao;
import com.cliet_tableaux.api.core.dtos.CheckoutSessionRequest;
import com.cliet_tableaux.api.core.dtos.CheckoutSessionResponse;
import com.cliet_tableaux.api.core.enums.PaymentStatutEnum;
import com.cliet_tableaux.api.core.exceptions.PaintingAlreadySoldException;
import com.cliet_tableaux.api.core.model.Order;
import com.cliet_tableaux.api.core.model.Painting;
import com.cliet_tableaux.api.core.model.User;
import com.stripe.exception.ApiConnectionException;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
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
class OrderServiceIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderDao orderDao;

    @Autowired
    private UserDao userDao;

    @Autowired
    private PaintingDao paintingDao;

    // Reproduit le scénario du §3 (Phase 3) : la session Stripe échoue APRÈS que
    // l'Order ait été sauvegardé une première fois. Sans @Transactional(rollbackFor = StripeException.class),
    // StripeException étant une exception checked, Spring ne fait pas rollback par défaut
    // et l'Order resterait en base, orpheline (PENDING, sans checkoutSessionId).
    @Test
    void createCheckoutSession_rollsBackOrder_whenStripeSessionCreationFails() {
        User user = new User();
        user.setEmail("buyer@example.com");
        user.setPassword("hashed-password");
        user = userDao.save(user);

        Painting painting = new Painting();
        painting.setTitle("Tableau de test");
        painting.setDescription("Description de test");
        painting.setSell(false);
        painting.setPriceCents(1000L);
        painting = paintingDao.save(painting);
        Long paintingId = painting.getId();

        CheckoutSessionRequest request = new CheckoutSessionRequest(1000L, "1 rue de Paris", "EUR", paintingId);
        User finalUser = user;

        try (MockedStatic<Session> mockedSession = Mockito.mockStatic(Session.class)) {
            mockedSession.when(() -> Session.create(any(SessionCreateParams.class)))
                    .thenThrow(new ApiConnectionException("Simulated Stripe network failure"));

            assertThrows(StripeException.class,
                    () -> orderService.createCheckoutSession(finalUser, request));
        }

        // Comparaison ciblée sur ce Painting plutôt que orderDao.findAll() global :
        // la base Testcontainers est partagée entre les méthodes de test de cette classe.
        boolean orphanOrderExists = orderDao.findAll().stream()
                .anyMatch(order -> order.getPainting().getId().equals(paintingId));
        assertThat(orphanOrderExists).isFalse();
    }

    // Chemin nominal : vérifie que l'Order persisté est bien relisible depuis la base
    // (couvre au passage le constructeur sans argument requis par Hibernate) et que
    // @Transactional ne casse pas le flux existant.
    @Test
    void createCheckoutSession_persistsOrder_whenStripeSessionCreationSucceeds() throws StripeException {
        User user = new User();
        user.setEmail("buyer2@example.com");
        user.setPassword("hashed-password");
        user = userDao.save(user);

        Painting painting = new Painting();
        painting.setTitle("Tableau de test 2");
        painting.setDescription("Description de test 2");
        painting.setSell(false);
        painting.setPriceCents(2000L);
        painting = paintingDao.save(painting);

        CheckoutSessionRequest request = new CheckoutSessionRequest(2000L, "2 rue de Paris", "EUR", painting.getId());

        Session fakeSession = Mockito.mock(Session.class);
        Mockito.when(fakeSession.getId()).thenReturn("cs_test_123");
        Mockito.when(fakeSession.getUrl()).thenReturn("https://checkout.stripe.com/cs_test_123");

        CheckoutSessionResponse response;
        try (MockedStatic<Session> mockedSession = Mockito.mockStatic(Session.class)) {
            mockedSession.when(() -> Session.create(any(SessionCreateParams.class))).thenReturn(fakeSession);
            response = orderService.createCheckoutSession(user, request);
        }

        assertThat(response.sessionId()).isEqualTo("cs_test_123");

        Optional<Order> persisted = orderDao.findById(response.orderId());
        assertThat(persisted).isPresent();
        assertThat(persisted.get().getCheckoutSessionId()).isEqualTo("cs_test_123");
    }

    // Anti-survente (Phase 4) : un Painting déjà marqué sell=true doit être rejeté
    // avant tout appel Stripe, sans créer de commande.
    @Test
    void createCheckoutSession_rejectsAlreadySoldPainting() {
        User user = new User();
        user.setEmail("buyer3@example.com");
        user.setPassword("hashed-password");
        user = userDao.save(user);

        Painting painting = new Painting();
        painting.setTitle("Tableau déjà vendu");
        painting.setDescription("Description de test 3");
        painting.setSell(true);
        painting.setPriceCents(3000L);
        painting = paintingDao.save(painting);
        Long paintingId = painting.getId();

        CheckoutSessionRequest request = new CheckoutSessionRequest(3000L, "3 rue de Paris", "EUR", paintingId);
        User finalUser = user;

        assertThrows(PaintingAlreadySoldException.class,
                () -> orderService.createCheckoutSession(finalUser, request));

        boolean orderCreated = orderDao.findAll().stream()
                .anyMatch(order -> order.getPainting().getId().equals(paintingId));
        assertThat(orderCreated).isFalse();
    }

    // Automatisation du stock : payment_intent.succeeded doit marquer le Painting comme vendu
    // (sell=true), passer l'Order à SUCCEEDED, et être idempotent si Stripe rejoue le même
    // événement webhook (cas fréquent en cas de retry) — sans planter ni re-agir sur le Painting.
    //
    // Couvre aussi la régression diagnostiquée le 2026-09-06 : Stripe ne garantit pas que
    // checkout.session.completed arrive avant payment_intent.succeeded. Ce test appelle
    // handlePaymentIntentSucceeded sur un Order dont paymentIntentId n'a JAMAIS été renseigné
    // (checkout.session.completed n'est pas simulé ici) : la commande doit quand même être
    // retrouvée via la metadata orderId posée sur le PaymentIntent à la création de la session.
    @Test
    void handlePaymentIntentSucceeded_marksPaintingSold_andIsIdempotentOnWebhookRetry() {
        User user = new User();
        user.setEmail("buyer4@example.com");
        user.setPassword("hashed-password");
        user = userDao.save(user);

        Painting painting = new Painting();
        painting.setTitle("Tableau succès webhook");
        painting.setDescription("Description de test 4");
        painting.setSell(false);
        painting.setPriceCents(4000L);
        painting = paintingDao.save(painting);

        Order order = Order.create(4000L, "4 rue de Paris", "EUR", user, painting);
        order.setCheckoutSessionId("cs_test_webhook");
        order = orderDao.save(order);
        Long orderId = order.getId();
        Long paintingId = painting.getId();

        // La metadata orderId (posée sur le PaymentIntent dès createCheckoutSession) est le seul
        // lien utilisé par handlePaymentIntentSucceeded : pas besoin que checkout.session.completed
        // soit passé avant pour renseigner paymentIntentId (cf. bug de race diagnostiqué le 2026-09-06).
        PaymentIntent paymentIntent = Mockito.mock(PaymentIntent.class);
        Mockito.when(paymentIntent.getId()).thenReturn("pi_test_webhook");
        Mockito.when(paymentIntent.getMetadata()).thenReturn(Map.of("orderId", String.valueOf(orderId)));

        orderService.handlePaymentIntentSucceeded(paymentIntent);

        assertThat(orderDao.findById(orderId).orElseThrow().getState()).isEqualTo(PaymentStatutEnum.SUCCEEDED);
        assertThat(paintingDao.findById(paintingId).orElseThrow().isSell()).isTrue();

        // Rejeu du même événement (Stripe retry) : ne doit pas planter ni changer l'état.
        orderService.handlePaymentIntentSucceeded(paymentIntent);

        assertThat(orderDao.findById(orderId).orElseThrow().getState()).isEqualTo(PaymentStatutEnum.SUCCEEDED);
        assertThat(paintingDao.findById(paintingId).orElseThrow().isSell()).isTrue();
    }

    // Sécurité supplémentaire : si le Painting a déjà été vendu par une AUTRE commande avant que
    // le webhook de celle-ci n'arrive (double vente due à une race condition à la création), le
    // traitement ne doit pas planter et ne doit pas "re-vendre" — le paiement reste enregistré
    // (SUCCEEDED) mais le stock n'est pas retouché une seconde fois.
    @Test
    void handlePaymentIntentSucceeded_doesNotFailWhenPaintingAlreadySoldByAnotherOrder() {
        User user = new User();
        user.setEmail("buyer5@example.com");
        user.setPassword("hashed-password");
        user = userDao.save(user);

        Painting painting = new Painting();
        painting.setTitle("Tableau déjà vendu ailleurs");
        painting.setDescription("Description de test 5");
        painting.setSell(true);
        painting.setPriceCents(5000L);
        painting = paintingDao.save(painting);

        Order order = Order.create(5000L, "5 rue de Paris", "EUR", user, painting);
        order.setCheckoutSessionId("cs_test_conflict");
        order = orderDao.save(order);
        Long orderId = order.getId();

        PaymentIntent paymentIntent = Mockito.mock(PaymentIntent.class);
        Mockito.when(paymentIntent.getId()).thenReturn("pi_test_conflict");
        Mockito.when(paymentIntent.getMetadata()).thenReturn(Map.of("orderId", String.valueOf(orderId)));

        orderService.handlePaymentIntentSucceeded(paymentIntent);

        assertThat(orderDao.findById(orderId).orElseThrow().getState()).isEqualTo(PaymentStatutEnum.SUCCEEDED);
    }
}
