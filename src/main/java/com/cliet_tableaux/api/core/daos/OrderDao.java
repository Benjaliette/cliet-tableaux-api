package com.cliet_tableaux.api.core.daos;

import com.cliet_tableaux.api.core.model.Order;
import com.cliet_tableaux.api.core.model.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderDao extends JpaRepository<Order, Long> {
  @Query("SELECT o from Order o WHERE o.checkoutSessionId = :sessionId")
  Optional<Order> findByStripeSessionId(@Param("sessionId") String sessionId);

  List<Order> findByUserOrderByCreatedAtDesc(User user);
}
