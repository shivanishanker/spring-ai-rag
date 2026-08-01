package com.example.springairag.repository;

import com.example.springairag.entity.Alert;

import jakarta.transaction.Transactional;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface AlertRepository extends JpaRepository<Alert, UUID> {

@Transactional
@Query(value = """
    SELECT *
    FROM cc_alerts_alert
    WHERE is_active = true
    ORDER BY embedding <=> CAST(:vector AS vector)
    LIMIT 5
    """, nativeQuery = true)
List<Alert> findSimilarAlerts(@Param("vector") String vector);

    // 🔥 for initial embedding generation
    @Query("SELECT a FROM Alert a WHERE a.embedding IS NULL")
    List<Alert> findAlertsWithoutEmbedding();

    @Query(value = """
    SELECT a.*, (a.embedding <=> CAST(:vector AS vector)) AS distance
    FROM cc_alerts_alert a
    WHERE a.is_active = true
    ORDER BY a.embedding <=> CAST(:vector AS vector)
    LIMIT 5
    """, nativeQuery = true)
List<Object[]> findSimilarAlertsWithScore(@Param("vector") String vector);
}