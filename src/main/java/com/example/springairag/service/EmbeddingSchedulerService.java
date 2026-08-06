package com.example.springairag.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class EmbeddingSchedulerService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingSchedulerService.class);

    private final JdbcTemplate jdbcTemplate;
    private final EmbeddingService embeddingService;

    public EmbeddingSchedulerService(JdbcTemplate jdbcTemplate, EmbeddingService embeddingService) {
        this.jdbcTemplate = jdbcTemplate;
        this.embeddingService = embeddingService;
    }

    /** Embeds active alerts that do not yet have a row in alert_embeddings. */
    @Scheduled(fixedDelayString = "${embedding.scheduler.fixed-delay:PT1H}")
    public void runEmbeddingJob() {
        log.info("Starting alert embedding job");

        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
            SELECT a.id,
                   a.message,
                   a.inference_analysis,
                   l.incident_category,
                   l.risk_assessment,
                   l.recommended_action::text AS recommended_action,
                   l.next_action::text AS next_action,
                   COALESCE(asset.device_names, '') AS device_names,
                   COALESCE(asset.camera_names, '') AS camera_names
            FROM cc_alerts_alert a
            LEFT JOIN cc_alerts_alertllmresponse l ON a.llm_response_id = l.id
            LEFT JOIN LATERAL (
                SELECT string_agg(DISTINCT d.name, ', ') AS device_names,
                       string_agg(DISTINCT c.name, ', ') AS camera_names
                FROM cc_alerts_alertdevice ad
                LEFT JOIN devices d ON d.id = ad.device_id
                LEFT JOIN cc_alerts_sensoralert sa ON sa.id = ad.sensor_alert_id
                LEFT JOIN field_units_camera c ON c.id = sa.camera_id
                WHERE ad.alert_id = a.id
            ) asset ON true
            LEFT JOIN alert_embeddings e ON a.id = e.alert_id
            WHERE a.is_active = true
              AND e.alert_id IS NULL
            ORDER BY a.start_time ASC
            LIMIT 20
            """);

        int count = 0;
        for (Map<String, Object> row : rows) {
            try {
                String eventText = safe(row.get("message")) + " "
                        + safe(row.get("inference_analysis")) + " "
                        + safe(row.get("incident_category"));
                String riskText = "Risk: " + safe(row.get("risk_assessment"))
                        + " Category: " + safe(row.get("incident_category"));
                String actionText = "Action: " + safe(row.get("recommended_action"))
                        + " Next: " + safe(row.get("next_action"))
                        + " Device: " + safe(row.get("device_names"))
                        + " Camera: " + safe(row.get("camera_names"));

                int inserted = jdbcTemplate.update("""
                    INSERT INTO alert_embeddings (alert_id, embedding_event, embedding_risk, embedding_action)
                    VALUES (?, CAST(? AS vector), CAST(? AS vector), CAST(? AS vector))
                    ON CONFLICT (alert_id) DO NOTHING
                    """,
                        row.get("id"),
                        toPgVector(embeddingService.generateEmbedding(eventText)),
                        toPgVector(embeddingService.generateEmbedding(riskText)),
                        toPgVector(embeddingService.generateEmbedding(actionText)));
                count += inserted;
            } catch (Exception exception) {
                log.error("Failed to create embeddings for alert {}", row.get("id"), exception);
            }
        }

        log.info("Completed alert embedding job; embedded {} record(s)", count);
    }

    private String safe(Object value) {
        return value == null ? "" : value.toString();
    }

    private String toPgVector(float[] vector) {
        StringBuilder value = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                value.append(',');
            }
            value.append(vector[i]);
        }
        return value.append(']').toString();
    }
}
