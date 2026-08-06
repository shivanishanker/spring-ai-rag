package com.example.springairag.repository;

import com.example.springairag.entity.Alert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface AlertRepository extends JpaRepository<Alert, UUID> {

    @Query(value = """
        SELECT a.*
        FROM cc_alerts_alert a
        INNER JOIN alert_embeddings e ON e.alert_id = a.id
        WHERE a.is_active = true
        ORDER BY (
            0.5 * (e.embedding_event <=> CAST(:vector AS vector)) +
            0.3 * (e.embedding_risk <=> CAST(:vector AS vector)) +
            0.2 * (e.embedding_action <=> CAST(:vector AS vector))
        ) NULLS LAST
        LIMIT 5
        """, nativeQuery = true)
    List<Alert> findSimilarAlerts(@Param("vector") String vector);

    @Query(value = """
        SELECT a.*
        FROM cc_alerts_alert a
        LEFT JOIN alert_embeddings e ON e.alert_id = a.id
        WHERE e.alert_id IS NULL
        """, nativeQuery = true)
    List<Alert> findAlertsWithoutEmbedding();

    @Query(value = """
        SELECT a.*, (
            0.5 * (e.embedding_event <=> CAST(:vector AS vector)) +
            0.3 * (e.embedding_risk <=> CAST(:vector AS vector)) +
            0.2 * (e.embedding_action <=> CAST(:vector AS vector))
        ) AS distance
        FROM cc_alerts_alert a
        INNER JOIN alert_embeddings e ON e.alert_id = a.id
        WHERE a.is_active = true
        ORDER BY distance NULLS LAST
        LIMIT 5
        """, nativeQuery = true)
    List<Object[]> findSimilarAlertsWithScore(@Param("vector") String vector);
}
