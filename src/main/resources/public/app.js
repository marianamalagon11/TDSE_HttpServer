// Cliente asincronico. Ninguna accion recarga la pagina: pido el servicio con
// fetch y solo actualizo el area de resultado o la de error.

const resultadoTexto = document.getElementById("resultado-texto");
const cajaError = document.getElementById("error");
const errorTexto = document.getElementById("error-texto");
const botones = document.querySelectorAll("button");

function mostrarResultado(texto) {
    resultadoTexto.textContent = texto;
    cajaError.hidden = true;
}

function mostrarError(texto) {
    errorTexto.textContent = texto;
    cajaError.hidden = false;
}

// Bloqueo los botones mientras espero para que se note el estado de carga
function cargando(activo) {
    botones.forEach((boton) => {
        boton.disabled = activo;
    });
    if (activo) {
        resultadoTexto.textContent = "Esperando la respuesta del servidor...";
    }
}

// Pide un servicio y devuelve el JSON ya parseado, o null si algo fallo.
// Separo tres casos: falla de red, respuesta HTTP de error, y respuesta buena.
async function pedirServicio(url) {
    cargando(true);
    try {
        let respuesta;
        try {
            respuesta = await fetch(url);
        } catch (fallaDeRed) {
            // Aca no hubo respuesta HTTP: el servidor esta caido o no hay red
            mostrarError("No se pudo contactar al servidor. Verifica que este corriendo.");
            return null;
        }

        // Aca si hubo respuesta, pero puede ser 400, 404, 405...
        if (!respuesta.ok) {
            let mensaje = "El servidor respondio con estado " + respuesta.status + ".";
            try {
                const cuerpo = await respuesta.json();
                if (cuerpo && cuerpo.error) {
                    mensaje = cuerpo.error;
                }
            } catch (noEsJson) {
                // El cuerpo no era JSON, me quedo con el mensaje generico
            }
            mostrarError(mensaje);
            return null;
        }

        return await respuesta.json();
    } finally {
        // Pase lo que pase, vuelvo a habilitar los botones
        cargando(false);
    }
}

document.getElementById("btn-saludo").addEventListener("click", async (evento) => {
    evento.preventDefault();
    const nombre = document.getElementById("nombre").value.trim();
    if (nombre === "") {
        mostrarError("Escribe un nombre antes de pedir el saludo.");
        return;
    }
    const datos = await pedirServicio("/app/hello?name=" + encodeURIComponent(nombre));
    if (datos) {
        mostrarResultado(datos.greeting);
    }
});

document.getElementById("btn-cuadrado").addEventListener("click", async (evento) => {
    evento.preventDefault();
    const numero = document.getElementById("numero").value.trim();
    if (numero === "") {
        mostrarError("Escribe un numero antes de calcular el cuadrado.");
        return;
    }
    const datos = await pedirServicio("/app/square?n=" + encodeURIComponent(numero));
    if (datos) {
        mostrarResultado("El cuadrado de " + datos.input + " es " + datos.square + ".");
    }
});

document.getElementById("btn-hora").addEventListener("click", async (evento) => {
    evento.preventDefault();
    const datos = await pedirServicio("/app/time");
    if (datos) {
        mostrarResultado("Hora del servidor: " + datos.serverTime);
    }
});

document.getElementById("btn-lento").addEventListener("click", async (evento) => {
    evento.preventDefault();
    const datos = await pedirServicio("/app/slow?seconds=5");
    if (datos) {
        mostrarResultado("La peticion lenta termino a las " + datos.finishedAt + ".");
    }
});