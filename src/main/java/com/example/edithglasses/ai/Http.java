package com.example.edithglasses.ai;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

final class Http {
    private static final HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    record Resp(int status, byte[] body) {
        String text() {
            return new String(body, StandardCharsets.UTF_8);
        }

        boolean ok() {
            return status >= 200 && status < 300;
        }
    }

    private Http() {
    }

    static Resp get(String url, String bearer) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15)).GET();
        if (bearer != null) b.header("Authorization", "Bearer " + bearer);
        return send(b.build());
    }

    static Resp postJson(String url, String json, String bearer) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(45))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8));
        if (bearer != null) b.header("Authorization", "Bearer " + bearer);
        return send(b.build());
    }

    static java.util.Map<String, String> auth(Ais.Ai ai, String key) {
        java.util.Map<String, String> m = new java.util.LinkedHashMap<>();
        if (ai.anthropic()) {
            m.put("x-api-key", key);
            m.put("anthropic-version", "2023-06-01");
        } else {
            m.put("Authorization", "Bearer " + key);
        }
        return m;
    }

    static Resp getH(String url, java.util.Map<String, String> headers) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15)).GET();
        headers.forEach(b::header);
        return send(b.build());
    }

    static Resp postJsonH(String url, String json, java.util.Map<String, String> headers) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(45))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8));
        headers.forEach(b::header);
        return send(b.build());
    }

    /** Minimal multipart upload for Groq's transcription endpoint. */
    static Resp postAudio(String url, String bearer, String model, byte[] wav) throws Exception {
        String boundary = "----edith" + UUID.randomUUID();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        field(out, boundary, "model", model);
        field(out, boundary, "response_format", "json");
        field(out, boundary, "temperature", "0");
        out.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"speech.wav\"\r\nContent-Type: audio/wav\r\n\r\n")
            .getBytes(StandardCharsets.UTF_8));
        out.write(wav);
        out.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(45))
            .header("Authorization", "Bearer " + bearer)
            .header("Content-Type", "multipart/form-data; boundary=" + boundary)
            .POST(HttpRequest.BodyPublishers.ofByteArray(out.toByteArray())).build();
        return send(req);
    }

    private static void field(ByteArrayOutputStream out, String boundary, String name, String value) throws Exception {
        out.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + name + "\"\r\n\r\n" + value + "\r\n")
            .getBytes(StandardCharsets.UTF_8));
    }

    private static Resp send(HttpRequest r) throws Exception {
        HttpResponse<byte[]> resp = CLIENT.send(r, HttpResponse.BodyHandlers.ofByteArray());
        return new Resp(resp.statusCode(), resp.body());
    }
}
