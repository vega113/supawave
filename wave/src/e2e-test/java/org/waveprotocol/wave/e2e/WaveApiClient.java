package org.waveprotocol.wave.e2e;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * HTTP client for the Wave server REST/servlet API.
 *
 * Uses Redirect.NEVER so the 302/303 from /auth/signin can be intercepted
 * to capture Set-Cookie before any redirect is followed.
 */
class WaveApiClient {

    private static final Gson GSON = new Gson();
    private final String baseUrl;
    private final HttpClient http;

    WaveApiClient(String baseUrl) {
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.http = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /** GET /healthz — returns true when server responds 200. */
    boolean healthCheck() {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/healthz"))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<Void> resp = http.send(req, HttpResponse.BodyHandlers.discarding());
            return resp.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * POST /auth/register — returns HTTP status code.
     * Normalizes the Jakarta PRG success redirect to 200.
     * 200 = success, 403 = duplicate/disabled.
     */
    int register(String username, String password) throws Exception {
        String body = "address=" + enc(username) + "&password=" + enc(password);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/auth/register"))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<Void> resp = http.send(req, HttpResponse.BodyHandlers.discarding());
        int status = resp.statusCode();
        if (status == 302 || status == 303) {
            return 200;
        }
        return status;
    }

    /**
     * POST /auth/signin — returns a LoginResult with JSESSIONID and wave-session-jwt.
     * The server returns 302/303 on success; we read Set-Cookie before the redirect.
     * Address is sent as "username@local.net".
     */
    LoginResult login(String username, String password) throws Exception {
        String address = username + "@local.net";
        String body = "address=" + enc(address) + "&password=" + enc(password);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/auth/signin"))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<Void> resp = http.send(req, HttpResponse.BodyHandlers.discarding());
        int status = resp.statusCode();
        if (status != 302 && status != 303) {
            return new LoginResult(status, null, null);
        }
        String jsessionid = null;
        String jwt = null;
        for (String header : resp.headers().allValues("set-cookie")) {
            for (String part : header.split(";")) {
                String trimmed = part.trim();
                if (trimmed.startsWith("JSESSIONID=")) {
                    jsessionid = trimmed.substring("JSESSIONID=".length());
                } else if (trimmed.startsWith("wave-session-jwt=")) {
                    jwt = trimmed.substring("wave-session-jwt=".length());
                }
            }
        }
        return new LoginResult(status, jsessionid, jwt);
    }

    /**
     * GET /fetch/<waveId> with JSESSIONID cookie.
     * Wave ID "domain!id" is converted to "domain/id" URL path per JavaWaverefEncoder.
     */
    JsonObject fetch(String jsessionid, String waveId) throws Exception {
        String path = waveId.replace("!", "/");
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/fetch/" + path))
                .timeout(Duration.ofSeconds(10))
                .header("Cookie", "JSESSIONID=" + jsessionid)
                .GET()
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new RuntimeException("fetch(" + waveId + ") returned HTTP " + resp.statusCode()
                    + ": " + resp.body().substring(0, Math.min(200, resp.body().length())));
        }
        return GSON.fromJson(resp.body(), JsonObject.class);
    }

    /** GET /search/?query=...&index=0&numResults=10 with JSESSIONID cookie. */
    JsonObject search(String jsessionid, String query) throws Exception {
        String url = baseUrl + "/search/?query=" + enc(query) + "&index=0&numResults=10";
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Cookie", "JSESSIONID=" + jsessionid)
                .GET()
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new RuntimeException("search(\"" + query + "\") returned HTTP " + resp.statusCode()
                    + ": " + resp.body().substring(0, Math.min(200, resp.body().length())));
        }
        return GSON.fromJson(resp.body(), JsonObject.class);
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    /** Result from login(). */
    static class LoginResult {
        final int status;
        final String jsessionid;
        final String jwt;

        LoginResult(int status, String jsessionid, String jwt) {
            this.status = status;
            this.jsessionid = jsessionid;
            this.jwt = jwt;
        }

        boolean success() {
            return status == 302 || status == 303;
        }
    }
}
