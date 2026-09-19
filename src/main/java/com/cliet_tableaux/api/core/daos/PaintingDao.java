package com.cliet_tableaux.api.core.daos;

import com.cliet_tableaux.api.core.model.Painting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaintingDao extends JpaRepository<Painting, Long> {
}
