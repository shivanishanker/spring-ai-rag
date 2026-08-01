package com.example.springairag.service;

import com.example.springairag.dto.QueryIntent;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.*;
import java.util.*;

@Service
public class ChatService {

    private final JdbcTemplate jdbcTemplate;
    private final EmbeddingService embeddingService;
    private final ChatClient chatClient;

    public ChatService(JdbcTemplate jdbcTemplate,
                       EmbeddingService embeddingService,
                       ChatClient.Builder builder) {
        this.jdbcTemplate = jdbcTemplate;
        this.embeddingService = embeddingService;
        this.chatClient = builder.build();
    }

    // ================= MAIN =================
    public String ask(String query) {

        System.out.println("🚀 QUERY: " + query);

        try {
            // 🔥 STEP 1: Extract intent (AI)
            QueryIntent intent = extractIntent(query);

            // 🔥 STEP 2: Extract date (HYBRID: AI + RULES)
            LocalDateTime[] range = extractDateRange(query, intent);

            LocalDateTime start = range[0];
            LocalDateTime end = range[1];

            System.out.println("📅 START: " + start);
            System.out.println("📅 END: " + end);

            // 🔥 STEP 3: Build SQL (SAFE + FAST)
            StringBuilder sql = new StringBuilder("""
                SELECT message, start_time
                FROM cc_alerts_alert
                WHERE is_active = true
            """);

            List<Object> params = new ArrayList<>();

            if (start != null && end != null) {
                sql.append(" AND start_time >= ? AND start_time <= ?");
                params.add(start);
                params.add(end);
            }

            // 🔥 OBJECT FILTER
            if (intent.getObject() != null) {
                sql.append(" AND message ILIKE ?");
                params.add("%" + intent.getObject() + "%");
            }

            // 🔥 VECTOR SEARCH (only for similarity queries)
            if (intent.isUseVector()) {
                float[] vec = embeddingService.generateEmbedding(query);
                sql.append(" ORDER BY embedding <=> CAST(? AS vector)");
                params.add(toPgVector(vec));
            } else {
                sql.append(" ORDER BY start_time DESC");
            }

            sql.append(" LIMIT 20");

            List<String> rows = jdbcTemplate.query(
                    sql.toString(),
                    (rs, i) -> mapRow(rs),
                    params.toArray()
            );

            if (rows.isEmpty()) {
                return "No relevant data found.";
            }

            // 🔥 STEP 4: Response by type
            switch (intent.getType()) {
                case "COUNT":
                    return "Total incidents: " + rows.size();

                case "LIST":
                    return String.join("\n", rows);

                case "COMPARE":
                case "ANALYZE":
                case "SUMMARY":
                default:
                    return summarize(rows, query);
            }

        } catch (Exception e) {
            e.printStackTrace();
            return "Error processing query.";
        }
    }

    // ================= INTENT =================
    private QueryIntent extractIntent(String query) {

        try {
            return chatClient.prompt().user("""
                Extract structured intent.

                Return JSON ONLY:
                {
                  "type": "COUNT | LIST | SUMMARY | COMPARE | ANALYZE",
                  "object": "gun | fire | knife | smoke | person | null",
                  "useVector": true/false
                }

                RULES:
                - "how many" → COUNT
                - "show/list" → LIST
                - "highest/most" → COMPARE
                - "why/explain" → ANALYZE
                - else → SUMMARY

                Query:
                """ + query)
                    .call()
                    .entity(QueryIntent.class);

        } catch (Exception e) {
            QueryIntent fallback = new QueryIntent();
            fallback.setType("SUMMARY");
            fallback.setUseVector(true);
            return fallback;
        }
    }

    // ================= DATE ENGINE =================
    private LocalDateTime[] extractDateRange(String query, QueryIntent intent) {

        query = query.toLowerCase();

        LocalDate today = LocalDate.now();

        try {
            // ✅ TODAY
            if (query.contains("today")) {
                return new LocalDateTime[]{
                        today.atStartOfDay(),
                        today.atTime(23, 59, 59)
                };
            }

            // ✅ YESTERDAY
            if (query.contains("yesterday")) {
                LocalDate d = today.minusDays(1);
                return new LocalDateTime[]{
                        d.atStartOfDay(),
                        d.atTime(23, 59, 59)
                };
            }

            // ✅ THIS MONTH
            if (query.contains("this month")) {
                LocalDate start = today.withDayOfMonth(1);
                LocalDate end = today;
                return new LocalDateTime[]{
                        start.atStartOfDay(),
                        end.atTime(23, 59, 59)
                };
            }

            // ✅ LAST MONTH
            if (query.contains("last month")) {
                LocalDate lastMonth = today.minusMonths(1);
                LocalDate start = lastMonth.withDayOfMonth(1);
                LocalDate end = lastMonth.withDayOfMonth(lastMonth.lengthOfMonth());

                return new LocalDateTime[]{
                        start.atStartOfDay(),
                        end.atTime(23, 59, 59)
                };
            }

            // ✅ THIS WEEK
            if (query.contains("this week")) {
                LocalDate start = today.minusDays(today.getDayOfWeek().getValue() - 1);
                return new LocalDateTime[]{
                        start.atStartOfDay(),
                        today.atTime(23, 59, 59)
                };
            }

            // ✅ 10 March
            String[] months = {"jan","feb","mar","apr","may","jun","jul","aug","sep","oct","nov","dec"};

            for (int i = 0; i < months.length; i++) {
                if (query.contains(months[i])) {

                    int month = i + 1;
                    int day = Integer.parseInt(query.replaceAll("\\D+", ""));

                    LocalDate d = LocalDate.of(today.getYear(), month, day);

                    return new LocalDateTime[]{
                            d.atStartOfDay(),
                            d.atTime(23, 59, 59)
                    };
                }
            }

        } catch (Exception ignored) {}

        // 🔥 FALLBACK (never fail)
        LocalDateTime end = LocalDateTime.now();
        LocalDateTime start = end.minusDays(7);

        return new LocalDateTime[]{start, end};
    }

    // ================= SUMMARY =================
    private String summarize(List<String> rows, String query) {

        String context = String.join("\n", rows);

        return chatClient.prompt().user("""
            Answer using ONLY the data.

            - Group similar events
            - Remove duplicates
            - Be precise

            Data:
            """ + context + """

            Question:
            """ + query)
                .call()
                .content();
    }

    // ================= MAPPER =================
    private String mapRow(ResultSet rs) throws SQLException {
        return rs.getString("message") + " at " + rs.getTimestamp("start_time");
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