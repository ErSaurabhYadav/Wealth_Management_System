package in.wealth.api;

import in.wealth.security.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.sql.Date;
import java.util.*;

@RestController
@RequestMapping("/api")
public class FinanceController {

    private final JdbcTemplate db;
    private final AuthService auth;

    public FinanceController(JdbcTemplate db, AuthService auth) {
        this.db = db;
        this.auth = auth;
    }

    private String uid(HttpServletRequest request) {
        return auth.userId(request.getHeader("Authorization"));
    }

    private void applyRls(String user) {
        db.execute("set local role authenticated");
        db.queryForObject(
            "select set_config('request.jwt.claim.sub', ?, true)",
            String.class,
            user
        );
    }

    private static final Map<String, String> TABLES = Map.of(
        "investments", "investments",
        "transactions", "transactions",
        "goals", "financial_goals",
        "liabilities", "liabilities",
        "assets", "assets",
        "categories", "expense_categories"
    );

    private static final Map<String, List<String>> COLUMNS = Map.of(
        "investments", List.of("name", "type", "invested_amount", "current_value", "risk_level", "investment_date", "notes"),
        "transactions", List.of("transaction_type", "category", "amount", "transaction_date", "description"),
        "goals", List.of("goal_name", "target_amount", "current_amount", "deadline", "importance", "status", "description"),
        "liabilities", List.of("name", "amount", "type"),
        "assets", List.of("name", "amount", "type"),
        "categories", List.of("category_name")
    );

    private static final Set<String> NUMERIC_COLUMNS = Set.of(
        "target_amount", "current_amount", "invested_amount", "current_value", "amount"
    );

    private static final Set<String> DATE_COLUMNS = Set.of(
        "deadline", "investment_date", "transaction_date"
    );

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok", "service", "WEALTH Java API");
    }

    @Transactional
    @GetMapping("/{resource}")
    public List<Map<String, Object>> list(
            @PathVariable String resource,
            HttpServletRequest request) {
        String table = table(resource);
        String user = uid(request);
        applyRls(user);
        return db.queryForList(
            "select * from public." + table + " where user_id = ? order by created_at desc",
            UUID.fromString(user)
        );
    }

    @Transactional
    @PostMapping("/{resource}")
    public Map<String, Object> create(
            @PathVariable String resource,
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {
        String table = table(resource);
        String user = uid(request);
        applyRls(user);

        List<String> used = COLUMNS.get(resource).stream()
            .filter(body::containsKey)
            .toList();
        if (used.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No valid fields supplied.");
        }

        List<Object> values = new ArrayList<>();
        values.add(UUID.fromString(user));
        for (String column : used) {
            values.add(convertValue(column, body.get(column)));
        }

        String columns = "user_id," + String.join(",", used);
        String placeholders = "?," + String.join(",", used.stream()
            .map(FinanceController::placeholderFor).toList());

        db.update(
            "insert into public." + table + " (" + columns + ") values (" + placeholders + ")",
            values.toArray()
        );
        return Map.of("ok", true, "message", "Record saved");
    }

    @Transactional
    @PutMapping("/{resource}/{id}")
    public Map<String, Object> update(
            @PathVariable String resource,
            @PathVariable String id,
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {
        String table = table(resource);
        String user = uid(request);
        applyRls(user);

        List<String> used = COLUMNS.get(resource).stream()
            .filter(body::containsKey)
            .toList();
        if (used.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No valid fields supplied.");
        }

        List<Object> values = new ArrayList<>();
        for (String column : used) {
            values.add(convertValue(column, body.get(column)));
        }
        values.add(UUID.fromString(id));
        values.add(UUID.fromString(user));

        String setClause = String.join(",", used.stream()
            .map(column -> column + "=" + placeholderFor(column))
            .toList());

        int count = db.update(
            "update public." + table + " set " + setClause + " where id=? and user_id=?",
            values.toArray()
        );
        if (count == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Record not found.");
        }
        return Map.of("ok", true);
    }

    @Transactional
    @DeleteMapping("/{resource}/{id}")
    public Map<String, Object> delete(
            @PathVariable String resource,
            @PathVariable String id,
            HttpServletRequest request) {
        String user = uid(request);
        applyRls(user);
        int count = db.update(
            "delete from public." + table(resource) + " where id=? and user_id=?",
            UUID.fromString(id),
            UUID.fromString(user)
        );
        if (count == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Record not found.");
        }
        return Map.of("ok", true);
    }

    @Transactional
    @GetMapping("/profile")
    public Map<String, Object> profile(HttpServletRequest request) {
        String user = uid(request);
        applyRls(user);
        List<Map<String, Object>> rows = db.queryForList(
            "select id, name, email, created_at from public.profiles where id=?",
            UUID.fromString(user)
        );
        return rows.isEmpty() ? Map.of("id", user, "name", "User") : rows.get(0);
    }

    @Transactional
    @PutMapping("/profile")
    public Map<String, Object> updateProfile(
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {
        String user = uid(request);
        applyRls(user);
        String name = Objects.toString(body.getOrDefault("name", "User")).trim();
        if (name.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Name is required.");
        }
        db.update(
            "insert into public.profiles(id, name, email) values (?, ?, ?) " +
            "on conflict(id) do update set name=excluded.name, email=excluded.email",
            UUID.fromString(user),
            name,
            Objects.toString(body.getOrDefault("email", ""), "")
        );
        return Map.of("ok", true, "name", name);
    }

    private Object convertValue(String column, Object value) {
        if (DATE_COLUMNS.contains(column)) {
            if (value == null || value.toString().isBlank()) {
                return null; // Empty optional deadline is stored as SQL NULL.
            }
            try {
                return Date.valueOf(value.toString()); // HTML date input sends yyyy-MM-dd.
            } catch (IllegalArgumentException ex) {
                throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid date for " + column + ". Use YYYY-MM-DD."
                );
            }
        }
        if (NUMERIC_COLUMNS.contains(column)) {
            if (value == null || value.toString().isBlank()) {
                throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "A numeric value is required for " + column + "."
                );
            }
            try {
                return new BigDecimal(value.toString());
            } catch (NumberFormatException ex) {
                throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid number for " + column + "."
                );
            }
        }
        return value;
    }

    private static String placeholderFor(String column) {
        if (NUMERIC_COLUMNS.contains(column)) return "CAST(? AS numeric)";
        if (DATE_COLUMNS.contains(column)) return "CAST(? AS date)";
        return "?";
    }

    private String table(String resource) {
        String table = TABLES.get(resource);
        if (table == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown resource.");
        }
        return table;
    }
}
