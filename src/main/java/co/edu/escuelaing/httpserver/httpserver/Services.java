package co.edu.escuelaing.httpserver.httpserver;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

// Los cuatro servicios hardcodeados del laboratorio. Cada metodo recibe el
// valor que vino en el query string (puede ser null si no lo mandaron) y
// devuelve una Response con JSON. Ninguno guarda estado entre peticiones.
public class Services {

    // GET /app/hello?name=Mariana
    public static Response hello(String name) {
        if (name == null || name.isBlank()) {
            return Response.json(400, "{\"error\":\"El parametro 'name' es obligatorio\"}");
        }
        return Response.json(200,
                "{\"greeting\":\"Hola, " + escapeJson(name.trim()) + "!\"}");
    }

    // GET /app/square?n=7
    public static Response square(String n) {
        if (n == null || n.isBlank()) {
            return Response.json(400, "{\"error\":\"El parametro 'n' es obligatorio\"}");
        }
        try {
            double value = Double.parseDouble(n.trim());
            return Response.json(200,
                    "{\"input\":" + value + ",\"square\":" + (value * value) + "}");
        } catch (NumberFormatException e) {
            return Response.json(400, "{\"error\":\"'n' debe ser un numero\"}");
        }
    }

    // GET /app/time
    public static Response time() {
        String now = ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        return Response.json(200, "{\"serverTime\":\"" + now + "\"}");
    }

    // GET /app/health
    public static Response health() {
        return Response.json(200, "{\"status\":\"ok\"}");
    }

    // Nunca meto texto del usuario en un JSON sin escapar: unas comillas
    // dentro del nombre romperian la respuesta
    public static String escapeJson(String raw) {
        StringBuilder sb = new StringBuilder();
        for (char c : raw.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '<' -> sb.append("\\u003C");
                case '>' -> sb.append("\\u003E");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }
}