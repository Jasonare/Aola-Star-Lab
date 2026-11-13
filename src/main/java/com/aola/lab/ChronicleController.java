package com.aola.lab;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.Set;

@RestController
@RequestMapping("/chronicle/api")
public class ChronicleController {
    private static final Set<String> FIELDS = Set.of("year", "title", "yabi", "background", "description");
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    ChronicleController(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    record Snapshot(long version, JsonNode nodes) {}

    @GetMapping
    Snapshot get() {
        return jdbc.query("SELECT version, nodes_json FROM chronicle_snapshot WHERE id = 1", rs -> {
            if (!rs.next()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "编年史尚未初始化，请执行数据库迁移");
            try {
                return new Snapshot(rs.getLong(1), mapper.readTree(rs.getString(2)));
            } catch (java.io.IOException ex) {
                throw new IllegalStateException("编年史数据损坏", ex);
            }
        });
    }

    @PutMapping
    ResponseEntity<Snapshot> put(@RequestBody JsonNode body) throws com.fasterxml.jackson.core.JsonProcessingException {
        if (!body.isObject() || body.size() != 2 || !body.has("version") || !body.has("nodes")
                || !body.get("version").isIntegralNumber() || !body.get("version").canConvertToLong()
                || body.get("version").longValue() < 0 || body.get("version").longValue() >= 9007199254740991L
                || !body.get("nodes").isArray() || body.get("nodes").size() > 200) {
            throw badRequest();
        }
        ArrayNode nodes = (ArrayNode) body.get("nodes");
        for (JsonNode node : nodes) {
            if (!node.isObject() || node.size() != FIELDS.size()) throw badRequest();
            for (String field : FIELDS) {
                if (!node.has(field) || !node.get(field).isTextual()) throw badRequest();
            }
            String year = node.get("year").textValue();
            String title = node.get("title").textValue();
            String yabi = node.get("yabi").textValue();
            String background = node.get("background").textValue();
            String description = node.get("description").textValue();
            if (year.isBlank() || year.length() > 16 || title.isBlank() || title.length() > 40
                    || yabi.length() > 65 || background.length() > 2048 || description.length() > 240
                    || !safeBackground(background)) throw badRequest();
        }
        long version = body.get("version").longValue();
        int updated = jdbc.update("UPDATE chronicle_snapshot SET nodes_json = ?, version = version + 1 WHERE id = 1 AND version = ?",
                mapper.writeValueAsString(nodes), version);
        if (updated == 0) {
            if (jdbc.queryForObject("SELECT COUNT(*) FROM chronicle_snapshot WHERE id = 1", Integer.class) == 0)
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "编年史尚未初始化，请执行数据库迁移");
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
        return ResponseEntity.ok(new Snapshot(version + 1, nodes));
    }

    private static boolean safeBackground(String value) {
        if (value.isEmpty()) return true;
        if (value.matches("assets/[A-Za-z0-9_-]+(?:/[A-Za-z0-9_-]+)*\\.[A-Za-z0-9]+(?:#hue=-?[0-9]{1,3})?")) return true;
        try {
            URI uri = new URI(value);
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null
                    && uri.getRawUserInfo() == null && uri.getPort() <= 65535
                    && !value.contains("\\") && !value.contains("\"") && !value.contains("'")
                    && value.chars().noneMatch(Character::isISOControl);
        } catch (java.net.URISyntaxException ex) {
            return false;
        }
    }

    private static ResponseStatusException badRequest() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "编年史数据无效");
    }
}
