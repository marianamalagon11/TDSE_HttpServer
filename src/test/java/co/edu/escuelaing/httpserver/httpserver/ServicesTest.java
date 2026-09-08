package co.edu.escuelaing.httpserver.httpserver;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ServicesTest {

    @Test
    void saludaConElNombreRecibido() {
        Response response = Services.hello("Mariana");
        assertEquals(200, response.status());
        assertTrue(response.contentType().startsWith("application/json"));
    }

    @Test
    void saludoSinNombreEsErrorDelCliente() {
        assertEquals(400, Services.hello(null).status());
        assertEquals(400, Services.hello("   ").status());
    }

    @Test
    void escapaLasComillasParaNoRomperElJson() {
        assertEquals("Ma\\\"ria", Services.escapeJson("Ma\"ria"));
    }

    @Test
    void calculaElCuadradoDeUnNumero() {
        Response response = Services.square("7");
        assertEquals(200, response.status());
        assertTrue(new String(response.body()).contains("49"));
    }

    @Test
    void elCuadradoRechazaValoresNoNumericos() {
        assertEquals(400, Services.square("abc").status());
        assertEquals(400, Services.square(null).status());
    }

    @Test
    void healthRespondeOk() {
        assertEquals(200, Services.health().status());
    }
}