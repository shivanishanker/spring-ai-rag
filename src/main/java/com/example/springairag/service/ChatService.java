package com.example.springairag.service;

import com.example.springairag.dto.QueryIntent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;
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
    private final ChatClient chatClient;
    private final ObjectMapper mapper = new ObjectMapper();

    public ChatService(JdbcTemplate jdbcTemplate,
                       EmbeddingService embeddingService,
                       ChatClient.Builder builder) {
        this.jdbcTemplate = jdbcTemplate;
        this.embeddingService = embeddingService;
        this.chatClient = builder.build();
    }

    // ================= MAIN =================
    public String ask(String query) {

        try {

            System.out.println("🚀 USER QUERY: " + query);

            QueryIntent intent = parseIntent(query);
            LocalDateTime[] range = extractDate(query);

            float[] vector = embeddingService.generateEmbedding(query);
            String vec = toPgVector(vector);

            String sql = buildSQL(query, intent, range);

            List<Object> params = buildParams(query, intent, range, vec);

            System.out.println("🧠 SQL: " + sql);
            System.out.println("🧠 PARAMS: " + params);

            List<String> rows = jdbcTemplate.query(
                    sql,
                    (rs, i) -> mapRow(rs),
                    params.toArray()
            );

            if (rows.isEmpty()) {
                return "{\"status\":\"empty\",\"message\":\"No data found\"}";
            }

            String markdown = chatClient.prompt().user("""
                Generate a markdown answer.

                Data:
                """ + String.join("\n", rows) + """

                Question:
                """ + query)
            .call()
            .content();

            Map<String, Object> res = new HashMap<>();
            res.put("status", "success");
            res.put("count", rows.size());
            res.put("data", rows);
            res.put("answer_md", markdown);

            return mapper.writeValueAsString(res);

        } catch (Exception e) {
            e.printStackTrace();
            return "{\"status\":\"error\",\"message\":\"Failed\"}";
        }
    }

    // ================= SQL =================
    private String buildSQL(String query, QueryIntent intent, LocalDateTime[] range) {

        StringBuilder sql = new StringBuilder("""
            SELECT 
                a.message,
                a.start_time,
                l.incident_category,
                l.risk_assessment
            FROM cc_alerts_alert a
            LEFT JOIN cc_alerts_alertllmresponse l 
                ON a.llm_response_id = l.id
            WHERE a.is_active = true
        """);

        // ✅ DATE
        if (range != null) {
            sql.append(" AND a.start_time BETWEEN ? AND ?");
        }

        // ✅ SPECIAL CASE: smoke but no flames
        if (query.toLowerCase().contains("smoke")
                && query.toLowerCase().contains("no")
                && query.toLowerCase().contains("flame")) {

            sql.append("""
                AND a.message ILIKE '%smoke%'
                AND a.message NOT ILIKE '%flame%'
            """);

        } else if (intent.getObject() != null) {
            sql.append(" AND (a.message ILIKE ? OR l.incident_category ILIKE ?)");
        }

        sql.append(" ORDER BY a.start_time DESC LIMIT 20");

        return sql.toString();
    }

    // ================= PARAMS =================
    private List<Object> buildParams(String query,
                                    QueryIntent intent,
                                    LocalDateTime[] range,
                                    String vec) {

        List<Object> params = new ArrayList<>();

        if (range != null) {
            params.add(range[0]);
            params.add(range[1]);
        }

        // ⚠️ IMPORTANT: Only add params if placeholders exist
        if (intent.getObject() != null
                && !(query.toLowerCase().contains("smoke")
                && query.toLowerCase().contains("no"))) {

            String pattern = "%" + intent.getObject() + "%";
            params.add(pattern);
            params.add(pattern);
        }

        return params;
    }

    // ================= INTENT =================
    private QueryIntent parseIntent(String query) {
        try {
            String res = chatClient.prompt().user("""
                Extract intent JSON:
                { "type":"LIST", "object":"gun" }
                Query:
                """ + query)
            .call()
            .content();

            return mapper.readValue(res, QueryIntent.class);
        } catch (Exception e) {
            return new QueryIntent();
        }
    }

    // ================= DATE =================
    private LocalDateTime[] extractDate(String query) {
        try {
            String res = chatClient.prompt().user("""
                Extract date JSON:
                {"start":"YYYY-MM-DD HH:mm:ss","end":"YYYY-MM-DD HH:mm:ss"}
                Query:
                """ + query)
            .call()
            .content();

            Map<?, ?> map = mapper.readValue(res, Map.class);

            return new LocalDateTime[]{
                    LocalDateTime.parse(((String) map.get("start")).replace(" ", "T")),
                    LocalDateTime.parse(((String) map.get("end")).replace(" ", "T"))
            };

        } catch (Exception e) {
            return null;
        }
    }

    // ================= MAP =================
    private String mapRow(ResultSet rs) {
        try {
            return rs.getString("message") +
                    " | Category: " + rs.getString("incident_category") +
                    " | Risk: " + rs.getString("risk_assessment") +
                    " | Time: " + rs.getTimestamp("start_time");
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
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