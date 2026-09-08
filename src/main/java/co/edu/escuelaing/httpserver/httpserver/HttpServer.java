package co.edu.escuelaing.httpserver.httpserver;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

// Servidor HTTP secuencial: atiende una conexion completa antes de aceptar la
// siguiente. No uso hilos, es a proposito para el laboratorio.
public class HttpServer {

    // Carpeta donde guardo el HTML, el JS, el CSS y las imagenes
    private static final String PUBLIC_ROOT = "/public";

    // Relaciono cada extension con el tipo que le anuncio al navegador
    private static final Map<String, String> CONTENT_TYPES = new HashMap<>();

    static {
        CONTENT_TYPES.put("html", "text/html; charset=utf-8");
        CONTENT_TYPES.put("css", "text/css; charset=utf-8");
        CONTENT_TYPES.put("js", "application/javascript; charset=utf-8");
        CONTENT_TYPES.put("json", "application/json; charset=utf-8");
        CONTENT_TYPES.put("txt", "text/plain; charset=utf-8");
        CONTENT_TYPES.put("png", "image/png");
        CONTENT_TYPES.put("jpg", "image/jpeg");
        CONTENT_TYPES.put("jpeg", "image/jpeg");
        CONTENT_TYPES.put("ico", "image/x-icon");
    }

    public static void main(String[] args) throws IOException {
        ServerSocket serverSocket = new ServerSocket(35000);
        System.out.println("Ready to receive...");

        while (true) {
            // Con try-with-resources el socket del cliente se cierra siempre,
            // aunque falle algo. El de escucha sigue abierto para la siguiente.
            try (Socket clientSocket = serverSocket.accept()) {
                handleClient(clientSocket);
            } catch (IOException | RuntimeException e) {
                // Atrapo el error aca adentro para que una peticion mala no
                // tumbe el servidor entero
                System.err.println("Peticion fallida: " + e);
            }
        }
    }

    // Lee una peticion completa y escribe la respuesta
    static void handleClient(Socket clientSocket) throws IOException {
        BufferedReader in = new BufferedReader(
                new InputStreamReader(clientSocket.getInputStream(), StandardCharsets.UTF_8));
        OutputStream out = clientSocket.getOutputStream();

        // Primera linea: "GET /index.html HTTP/1.1"
        String requestLine = in.readLine();
        if (requestLine == null || requestLine.isBlank()) {
            return;
        }
        System.out.println("Request line: " + requestLine);

        // Consumo las cabeceras hasta la linea en blanco, que es la que marca
        // el final segun el protocolo. Ya no las imprimo para no llenar el log.
        String header;
        while ((header = in.readLine()) != null && !header.isEmpty()) {
        }

        String[] parts = requestLine.split(" ");
        if (parts.length < 3) {
            send(out, Response.text(400, "400 Bad Request"));
            return;
        }
        String method = parts[0];
        String target = parts[1];

        // Este laboratorio solo acepta GET
        if (!"GET".equals(method)) {
            send(out, Response.text(405, "405 Method Not Allowed"));
            return;
        }

        URI reqURI;
        try {
            reqURI = new URI(target);
        } catch (URISyntaxException e) {
            send(out, Response.text(400, "400 Bad Request"));
            return;
        }

        Response response = route(reqURI);
        System.out.println("  -> " + response.status() + " " + response.contentType()
                + " (" + response.body().length + " bytes)");
        send(out, response);
    }

    // Ruteo hardcodeado: condiciones explicitas, sin framework ni anotaciones.
    // Lo que no sea ruta de servicio se busca como archivo estatico.
    static Response route(URI reqURI) {
        String path = reqURI.getPath();
        if (path == null) {
            return Response.text(400, "400 Bad Request");
        }

        Map<String, String> query = parseQuery(reqURI.getRawQuery());

        switch (path) {
            case "/app/hello":
                return Services.hello(query.get("name"));
            case "/app/square":
                return Services.square(query.get("n"));
            case "/app/time":
                return Services.time();
            case "/app/health":
                return Services.health();
            default:
                return staticFile(path);
        }
    }

    // Convierto "name=Ana&n=5" en un mapa. Primero parto por & y por =, y
    // solo despues decodifico: si decodificara antes, un %26 dentro de un
    // valor se volveria un & de verdad y partiria mal el parametro.
    static Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> params = new HashMap<>();
        if (rawQuery == null || rawQuery.isBlank()) {
            return params;
        }
        for (String pair : rawQuery.split("&")) {
            int eq = pair.indexOf('=');
            if (eq < 0) {
                params.put(URLDecoder.decode(pair, StandardCharsets.UTF_8), "");
            } else {
                String key = URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8);
                String value = URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
                params.put(key, value);
            }
        }
        return params;
    }

    // Busca el archivo dentro de /public y lo lee como bytes
    static Response staticFile(String path) {
        // La raiz es la pagina de inicio
        if (path.equals("/") || path.isEmpty()) {
            path = "/index.html";
        }
        String safePath = safeResourcePath(path);
        if (safePath == null) {
            return Response.text(403, "403 Forbidden");
        }
        try (InputStream is = HttpServer.class.getResourceAsStream(PUBLIC_ROOT + safePath)) {
            // Si no existe el archivo, getResourceAsStream devuelve null
            if (is == null) {
                return Response.html(404,
                        "<!doctype html><html><head><meta charset=\"utf-8\">"
                        + "<title>404</title></head><body>"
                        + "<h1>404 Not Found</h1>"
                        + "<p><a href=\"/\">Volver al inicio</a></p>"
                        + "</body></html>");
            }
            byte[] body = is.readAllBytes();
            return new Response(200, contentType(safePath), body);
        } catch (IOException e) {
            return Response.text(500, "500 Internal Server Error");
        }
    }

    // Rechazo cualquier ruta que intente salirse de la carpeta public.
    // Devuelve null si la ruta es insegura.
    static String safeResourcePath(String rawPath) {
        // Decodifico primero, si no alguien esconde el ataque como %2e%2e
        String decoded = URLDecoder.decode(rawPath, StandardCharsets.UTF_8);
        if (!decoded.startsWith("/")) {
            return null;
        }
        // ".." sube un nivel, "\" es separador en Windows, y el byte nulo
        // sirve para cortar cadenas en capas de mas abajo
        if (decoded.contains("..") || decoded.contains("\\") || decoded.indexOf('\0') >= 0) {
            return null;
        }
        // Junto las barras repetidas: /img//logo.png queda /img/logo.png
        return decoded.replaceAll("/+", "/");
    }

    // Saco la extension del archivo y busco su tipo en el mapa
    static String contentType(String path) {
        int dot = path.lastIndexOf('.');
        if (dot < 0 || dot == path.length() - 1) {
            return "application/octet-stream";
        }
        String ext = path.substring(dot + 1).toLowerCase();
        return CONTENT_TYPES.getOrDefault(ext, "application/octet-stream");
    }

    // Escribo la respuesta: las cabeceras como texto y el cuerpo como bytes.
    // El Content-Length lo saco del arreglo, no de contar caracteres.
    static void send(OutputStream out, Response response) throws IOException {
        StringBuilder head = new StringBuilder();
        head.append("HTTP/1.1 ").append(response.status()).append(' ')
                .append(reasonPhrase(response.status())).append("\r\n");
        head.append("Content-Type: ").append(response.contentType()).append("\r\n");
        head.append("Content-Length: ").append(response.body().length).append("\r\n");
        // Si rechazo el metodo, el protocolo pide decir cuales si acepto
        if (response.status() == 405) {
            head.append("Allow: GET\r\n");
        }
        head.append("Connection: close\r\n");
        head.append("\r\n"); // esta linea vacia separa cabeceras del cuerpo

        out.write(head.toString().getBytes(StandardCharsets.US_ASCII));
        out.write(response.body());
        out.flush();
    }

    // Traduzco el numero de estado a su texto
    static String reasonPhrase(int status) {
        return switch (status) {
            case 200 -> "OK";
            case 400 -> "Bad Request";
            case 403 -> "Forbidden";
            case 404 -> "Not Found";
            case 405 -> "Method Not Allowed";
            case 500 -> "Internal Server Error";
            default -> "Unknown";
        };
    }
}