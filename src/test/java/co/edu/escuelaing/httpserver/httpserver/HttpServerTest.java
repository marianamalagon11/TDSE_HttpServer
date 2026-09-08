package co.edu.escuelaing.httpserver.httpserver;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HttpServerTest {

    @Test
    void asignaElTipoDeContenidoSegunLaExtension() {
        assertTrue(HttpServer.contentType("/index.html").startsWith("text/html"));
        assertEquals("image/png", HttpServer.contentType("/img/logoU.png"));
        assertEquals("image/jpeg", HttpServer.contentType("/img/fotoU.jpeg"));
    }

    @Test
    void extensionDesconocidaCaeEnOctetStream() {
        assertEquals("application/octet-stream", HttpServer.contentType("/archivo.raro"));
    }

    @Test
    void rechazaRutasQueIntentanSalirDeLaCarpetaPublica() {
        assertNull(HttpServer.safeResourcePath("/../../etc/passwd"));
        assertNull(HttpServer.safeResourcePath("/%2e%2e/pom.xml"));
        assertNull(HttpServer.safeResourcePath("sin-slash-inicial"));
    }

    @Test
    void aceptaRutasNormales() {
        assertEquals("/index.html", HttpServer.safeResourcePath("/index.html"));
        assertEquals("/img/logoU.png", HttpServer.safeResourcePath("/img//logoU.png"));
    }

    @Test
    void unaRutaInexistenteDevuelve404() {
        assertEquals(404, HttpServer.staticFile("/no-existe.html").status());
    }

    @Test
    void unaRutaInseguraDevuelve403() {
        assertEquals(403, HttpServer.staticFile("/../pom.xml").status());
    }

    @Test
    void sirveLaPaginaDeInicioEnLaRaiz() {
        Response response = HttpServer.staticFile("/");
        assertEquals(200, response.status());
        assertTrue(response.contentType().startsWith("text/html"));
    }

    @Test
    void descomponeElQueryString() {
        Map<String, String> params = HttpServer.parseQuery("name=Mariana&n=7");
        assertEquals("Mariana", params.get("name"));
        assertEquals("7", params.get("n"));
    }

    @Test
    void decodificaLosParametrosDelQueryString() {
        assertEquals("Ana Maria", HttpServer.parseQuery("name=Ana%20Maria").get("name"));
    }
}