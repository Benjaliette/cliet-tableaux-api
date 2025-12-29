package com.cliet_tableaux.api.core.daos;

import com.cliet_tableaux.api.core.model.Painting;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaintingDao extends JpaRepository<Painting, Long> {

  @Override
  @Query("SELECT p FROM Painting p WHERE p.id = :id")
  Optional<Painting> findById(@Param("id") Long id);
}
