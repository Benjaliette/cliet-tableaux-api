package com.cliet_tableaux.api.core.daos;

import com.cliet_tableaux.api.core.model.Order;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderDao extends JpaRepository<Order, Long> {
  @Query("SELECT o from Order o WHERE o.checkoutSessionId = :sessionId")
  Optional<Order> findByStripeSessionId(@Param("sessionId") String sessionId);

  @Query("SELECT o from Order o WHERE o.paymentIntentId = :paymentId")
  Optional<Order> findByStripePaymentIntentId(@Param("paymentId") String paymentId);
}
