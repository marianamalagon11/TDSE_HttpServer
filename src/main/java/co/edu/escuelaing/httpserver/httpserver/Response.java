package co.edu.escuelaing.httpserver.httpserver;

import java.nio.charset.StandardCharsets;

public record Response(int status, String contentType, byte[] body) {

    public static Response html(int status, String html) {
        return new Response(status, "text/html; charset=utf-8",
                html.getBytes(StandardCharsets.UTF_8));
    }

    public static Response text(int status, String text) {
        return new Response(status, "text/plain; charset=utf-8",
                text.getBytes(StandardCharsets.UTF_8));
    }

    public static Response json(int status, String json) {
        return new Response(status, "application/json; charset=utf-8",
                json.getBytes(StandardCharsets.UTF_8));
    }
}