package com.cliet_tableaux.api.core.daos;

import com.cliet_tableaux.api.core.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserDao extends JpaRepository<User, Long> {
    @Query("SELECT user from User user WHERE user.email = :username")
    Optional<User> findByEmail(@Param("username") String username);

    @Query("SELECT user from User user WHERE user.refreshToken = :refreshToken")
    Optional<User> findByRefreshToken(@Param("refreshToken") String refreshToken);

    @Query("SELECT CASE WHEN COUNT(user) > 0 THEN true ELSE false END FROM User user WHERE user.email = :email")
    boolean existsByEmail(@Param("email") String email);
}
