package com.example.springairag.service;

import com.example.springairag.dto.TimeFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.*;
import java.util.*;
import java.util.regex.*;

@Service
public class ChatService {

    private final JdbcTemplate jdbcTemplate;
    private final EmbeddingService embeddingService;
    private final ChatClient chatClient;

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    public ChatService(JdbcTemplate jdbcTemplate,
                       EmbeddingService embeddingService,
                       ChatClient.Builder builder) {
        this.jdbcTemplate = jdbcTemplate;
        this.embeddingService = embeddingService;
        this.chatClient = builder.build();
    }

    // 🔥 MAIN METHOD
    public String ask(String query) {

        System.out.println("🚀 Query: " + query);

        try {
            // ✅ STEP 1: TIME EXTRACTION
            TimeFilter timeFilter = extractTimeHybrid(query);

            System.out.println("🧠 START: " + (timeFilter != null ? timeFilter.getStart() : null));
            System.out.println("🧠 END: " + (timeFilter != null ? timeFilter.getEnd() : null));

            // 🚨 STOP if invalid
            if (timeFilter == null ||
                timeFilter.getStart() == null ||
                timeFilter.getEnd() == null) {

                return "❌ Could not understand date. Try '10 March' or 'yesterday'.";
            }

            // ✅ STEP 2: EMBEDDING
            float[] vector = embeddingService.generateEmbedding(query);
            String vectorString = toPgVector(vector);

            // ✅ STEP 3: SQL (FIXED)
            String sql = """
                SELECT message, start_time
                FROM (
                    SELECT *
                    FROM cc_alerts_alert
                    WHERE is_active = true
                    AND start_time >= ? AND start_time <= ?
                    ORDER BY start_time DESC
                    LIMIT 100
                ) sub
                ORDER BY embedding <=> ?::vector
                LIMIT 20
            """;

            List<Object> params = new ArrayList<>();
            params.add(timeFilter.getStart());
            params.add(timeFilter.getEnd());
            params.add(vectorString);

            List<String> results = jdbcTemplate.query(
                    sql,
                    (rs, rowNum) -> mapRow(rs),
                    params.toArray()
            );

            System.out.println("📊 Rows: " + results.size());

            if (results.isEmpty()) {
                return "No data found for given date.";
            }

            // ✅ STEP 4: CONTEXT
            String context = String.join("\n", results);

            // ✅ STEP 5: AI RESPONSE
            return chatClient
                    .prompt()
                    .user("""
                        You are a construction monitoring AI.

                        Answer ONLY from the data.

                        - Group similar events
                        - Remove duplicates
                        - If "when" → include time
                        - If "how many" → count

                        Data:
                        """ + context + """

                        Question:
                        """ + query)
                    .call()
                    .content();

        } catch (Exception e) {
            e.printStackTrace();
            return "Error processing request.";
        }
    }

    // 🧠 HYBRID EXTRACTION
    private TimeFilter extractTimeHybrid(String query) {

        TimeFilter regex = extractUsingRegex(query);
        if (regex != null) {
            System.out.println("⚡ Regex used");
            return regex;
        }

        // ❌ KEEP AI as LAST fallback (optional)
        try {
            String response = chatClient
                    .prompt()
                    .user("""
                        Extract time range from query.

                        Return ONLY JSON:
                        {"start":"2026-03-10T00:00:00","end":"2026-03-10T23:59:59"}

                        Query:
                        """ + query)
                    .call()
                    .content();

            System.out.println("🧠 AI RAW: " + response);

            return mapper.readValue(response, TimeFilter.class);

        } catch (Exception e) {
            System.out.println("⚠️ AI failed");
            return null;
        }
    }

    // ⚡ REGEX (PRIMARY)
    private TimeFilter extractUsingRegex(String query) {

        query = query.toLowerCase();

        try {
            // today
            if (query.contains("today")) {
                LocalDate d = LocalDate.now();
                return new TimeFilter(d.atStartOfDay(), d.atTime(23,59,59));
            }

            // yesterday
            if (query.contains("yesterday")) {
                LocalDate d = LocalDate.now().minusDays(1);
                return new TimeFilter(d.atStartOfDay(), d.atTime(23,59,59));
            }

            // 10 March
            Pattern p = Pattern.compile("(\\d{1,2})\\s+(\\w+)");
            Matcher m = p.matcher(query);

            if (m.find()) {
                int day = Integer.parseInt(m.group(1));
                String monthStr = m.group(2).substring(0,3).toUpperCase();

                Month month = Month.valueOf(monthStr);

                int year = LocalDate.now().getYear(); // ✅ FIXED (dynamic year)

                LocalDate d = LocalDate.of(year, month, day);

                return new TimeFilter(
                        d.atStartOfDay(),
                        d.atTime(23, 59, 59)
                );
            }

        } catch (Exception e) {
            System.out.println("⚠️ Regex failed");
        }

        return null;
    }

    // 🔄 MAPPER
    private String mapRow(ResultSet rs) throws SQLException {
        return rs.getString("message") +
                " at " +
                rs.getTimestamp("start_time");
    }

    // 🔧 VECTOR FORMAT
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