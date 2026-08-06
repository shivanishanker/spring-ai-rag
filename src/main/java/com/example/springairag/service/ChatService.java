package com.example.springairag.service;

import com.example.springairag.dto.ChatResponse;
import com.example.springairag.dto.QueryIntent;
import com.example.springairag.provider.AiProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class ChatService {

    private final JdbcTemplate jdbcTemplate;
    private final EmbeddingService embeddingService;
    private final AiProvider aiProvider;
    private final ObjectMapper mapper = new ObjectMapper();

    public ChatService(JdbcTemplate jdbcTemplate,
                       EmbeddingService embeddingService,
                       AiProvider aiProvider) {
        this.jdbcTemplate = jdbcTemplate;
        this.embeddingService = embeddingService;
        this.aiProvider = aiProvider;
    }

    // ================= MAIN =================
    public ChatResponse ask(String query) {

        ChatResponse response = new ChatResponse();

        try {
            System.out.println("🚀 USER QUERY: " + query);

            QueryIntent intent = parseIntent(query);
            LocalDateTime[] range = extractDate(query);

            float[] vector = embeddingService.generateEmbedding(query);
            String vec = toPgVector(vector);

            String sql = buildSQL(intent, range);

            List<Object> params = buildParams(intent, range, vec);

            System.out.println("🧠 SQL: " + sql);
            System.out.println("🧠 PARAMS: " + params);

            List<String> rows = jdbcTemplate.query(
                    sql,
                    (rs, i) -> mapRow(rs),
                    params.toArray()
            );

            if (rows.isEmpty()) {
                response.setAnswer("No relevant data found.");
                response.setSource("DB");
                return response;
            }

            String context = String.join("\n", rows);

            String markdown = aiProvider.generateAnswer(
                    "Generate a concise markdown answer using ONLY the given data.",
                    context,
                    query
            );

            response.setAnswer(markdown);
            response.setSource("Hybrid Multi-Embedding AI");

            return response;

        } catch (Exception e) {
            e.printStackTrace();
            response.setAnswer("Error processing request");
            response.setSource("System Error");
            return response;
        }
    }

    // ================= SQL =================
    private String buildSQL(QueryIntent intent, LocalDateTime[] range) {

        StringBuilder sql = new StringBuilder("""
            SELECT 
                a.message,
                a.start_time,
                a.inference_analysis,
                l.incident_category,
                l.risk_assessment,
                l.recommended_action::text AS recommended_action,
                l.next_action::text AS next_action,
                COALESCE(asset.device_names, '') AS device_names,
                COALESCE(asset.camera_names, '') AS camera_names
            FROM cc_alerts_alert a
            LEFT JOIN cc_alerts_alertllmresponse l 
                ON a.llm_response_id = l.id
            LEFT JOIN LATERAL (
                SELECT string_agg(DISTINCT d.name, ', ') AS device_names,
                       string_agg(DISTINCT c.name, ', ') AS camera_names
                FROM cc_alerts_alertdevice ad
                LEFT JOIN devices d ON d.id = ad.device_id
                LEFT JOIN cc_alerts_sensoralert sa ON sa.id = ad.sensor_alert_id
                LEFT JOIN field_units_camera c ON c.id = sa.camera_id
                WHERE ad.alert_id = a.id
            ) asset ON true
            INNER JOIN alert_embeddings e
                ON a.id = e.alert_id
            WHERE a.is_active = true
        """);

        // DATE FILTER
        if (range != null) {
            sql.append(" AND a.start_time BETWEEN ? AND ?");
        }

        // TEXT FILTER
        if (intent.getObject() != null) {
            sql.append(" AND (a.message ILIKE ? OR l.incident_category ILIKE ?)");
        }

        // 🔥 MULTI-EMBEDDING SCORING
        sql.append("""
            ORDER BY (
                0.5 * (e.embedding_event <=> CAST(? AS vector)) +
                0.3 * (e.embedding_risk <=> CAST(? AS vector)) +
                0.2 * (e.embedding_action <=> CAST(? AS vector))
            )
        """);

        sql.append(" NULLS LAST LIMIT 20");

        return sql.toString();
    }

    // ================= PARAMS =================
    private List<Object> buildParams(QueryIntent intent,
                                    LocalDateTime[] range,
                                    String vec) {

        List<Object> params = new ArrayList<>();

        // DATE
        if (range != null) {
            params.add(range[0]);
            params.add(range[1]);
        }

        // TEXT
        if (intent.getObject() != null) {
            String pattern = "%" + intent.getObject() + "%";
            params.add(pattern);
            params.add(pattern);
        }

        // 🔥 VECTOR (3 TIMES)
        params.add(vec); // event
        params.add(vec); // risk
        params.add(vec); // action

        return params;
    }

    // ================= INTENT =================
    private QueryIntent parseIntent(String query) {
        try {
            String res = aiProvider.generateAnswer(
                    "Return only JSON: {\"type\":\"LIST\",\"object\":\"gun\"}",
                    "",
                    query
            );

            return mapper.readValue(res, QueryIntent.class);
        } catch (Exception e) {
            return new QueryIntent();
        }
    }

    // ================= DATE =================
    private LocalDateTime[] extractDate(String query) {
        try {
            String res = aiProvider.generateAnswer(
                    "Return JSON only: {\"start\":\"YYYY-MM-DD HH:mm:ss\",\"end\":\"YYYY-MM-DD HH:mm:ss\"}",
                    "",
                    query
            );

            Map<?, ?> map = mapper.readValue(res, Map.class);

            if (map.get("start") == null || map.get("end") == null) {
                return null;
            }

            return new LocalDateTime[]{
                    LocalDateTime.parse(((String) map.get("start")).replace(" ", "T")),
                    LocalDateTime.parse(((String) map.get("end")).replace(" ", "T"))
            };

        } catch (Exception e) {
            return null;
        }
    }

    // ================= MAP =================
    private String mapRow(ResultSet rs) throws SQLException {
        return rs.getString("message") +
                " | Analysis: " + rs.getString("inference_analysis") +
                " | Category: " + rs.getString("incident_category") +
                " | Risk: " + rs.getString("risk_assessment") +
                " | Recommended action: " + rs.getString("recommended_action") +
                " | Next action: " + rs.getString("next_action") +
                " | Device: " + rs.getString("device_names") +
                " | Camera: " + rs.getString("camera_names") +
                " | Time: " + rs.getTimestamp("start_time");
    }

    // ================= VECTOR =================
    private String toPgVector(float[] vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            sb.append(vector[i]);
            if (i < vector.length - 1) sb.append(",");
        }
        sb.append("]");
        return sb.toString();
    }
}
