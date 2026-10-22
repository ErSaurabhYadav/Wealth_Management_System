package in.wealth.security;

import java.net.URI;
import java.net.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
public class AuthService {
    @Value("${supabase.url}") private String supabaseUrl;
    @Value("${supabase.anon-key}") private String anonKey;

    public String userId(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer "))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Please sign in.");
        try {
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(supabaseUrl + "/auth/v1/user"))
                .header("apikey", anonKey)
                .header("Authorization", authorization)
                .GET().build();
            HttpResponse<String> res = HttpClient.newHttpClient().send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Session expired. Please sign in again.");
            String body = res.body();
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"id\"\\s*:\\s*\"([0-9a-fA-F-]{36})\"").matcher(body);
            if (!m.find()) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid session.");
            return m.group(1);
        } catch (ResponseStatusException ex) { throw ex; }
        catch (Exception ex) { throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Unable to verify session."); }
    }
}
