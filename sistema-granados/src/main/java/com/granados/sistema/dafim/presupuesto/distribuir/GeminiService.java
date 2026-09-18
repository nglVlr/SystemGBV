package com.granados.sistema.dafim.presupuesto.distribuir;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.granados.sistema.dafim.compras.util.Constantes;
import com.granados.sistema.dafim.compras.util.TextoUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

/**
 * Clasificador con Gemini (nivel gratis): manda TODAS las filas en una
 * sola llamada y espera JSON estricto [{fila, grupo, renglon, explicacion}].
 *
 * La key solo vive en el servidor (env GEMINI_API_KEY) y viaja en el
 * header x-goog-api-key, nunca en la URL: asi ningun log la puede
 * exponer. Cualquier fallo (sin key, timeout, HTTP != 200, JSON
 * invalido) devuelve lista vacia para que el caller caiga al
 * ClasificadorLocalService.
 */
@Service
public class GeminiService {

    private static final Logger log = LoggerFactory.getLogger(GeminiService.class);

    /** Timeout total de la llamada (la API gratis a veces se tarda). */
    static final Duration TIMEOUT = Duration.ofSeconds(30);

    /** Recorte de descripcion/emisor dentro del prompt, para acotar tokens. */
    private static final int MAX_TEXTO_FILA = 200;

    private static final String URL_BASE =
            "https://generativelanguage.googleapis.com/v1beta/models/";

    private final String apiKey;
    private final String model;
    private final HttpClient http;
    private final Duration timeout;
    private final ObjectMapper mapper = new ObjectMapper();

    @Autowired
    public GeminiService(@Value("${gemini.api-key:}") String apiKey,
                         @Value("${gemini.model:gemini-2.0-flash}") String model) {
        this(apiKey, model,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(),
                TIMEOUT);
    }

    /** Constructor para pruebas: permite inyectar el HTTP mockeado. */
    GeminiService(String apiKey, String model, HttpClient http, Duration timeout) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = (model == null || model.isBlank()) ? "gemini-2.0-flash" : model.trim();
        this.http = http;
        this.timeout = timeout;
    }

    /** true si hay key configurada (sin key ni se intenta la llamada). */
    public boolean estaDisponible() {
        return !apiKey.isEmpty();
    }

    /**
     * Clasifica todas las filas en UNA llamada batch. Devuelve las mismas
     * filas con grupo/renglon/explicacion llenos (las filas que Gemini no
     * devolvio quedan en blanco); lista vacia si algo falla, para que el
     * caller use el clasificador local.
     */
    public List<FilaDistribucion> clasificar(List<FilaDistribucion> filas) {
        if (!estaDisponible() || filas == null || filas.isEmpty()) {
            return List.of();
        }
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(URL_BASE + model + ":generateContent"))
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .header("x-goog-api-key", apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(cuerpoPeticion(filas)))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                log.warn("Gemini respondio HTTP {} clasificando {} filas; se usa el clasificador local",
                        resp.statusCode(), filas.size());
                return List.of();
            }
            return aplicarRespuesta(resp.body(), filas);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Gemini interrumpido; se usa el clasificador local");
            return List.of();
        } catch (Exception e) {
            // La key va en el header, pero por cautela no se loguea la peticion
            log.warn("Gemini fallo ({}: {}); se usa el clasificador local",
                    e.getClass().getSimpleName(), e.getMessage());
            return List.of();
        }
    }

    // ------------------------------ internos ------------------------------

    /** Cuerpo JSON de generateContent: un solo prompt con todas las filas. */
    private String cuerpoPeticion(List<FilaDistribucion> filas) throws Exception {
        var root = mapper.createObjectNode();
        var contents = root.putArray("contents");
        contents.addObject().putArray("parts").addObject().put("text", prompt(filas));
        var gen = root.putObject("generationConfig");
        gen.put("responseMimeType", "application/json");
        gen.put("temperature", 0);
        return mapper.writeValueAsString(root);
    }

    /** Prompt en espanol: una linea numerada por factura, respuesta JSON estricta. */
    private static String prompt(List<FilaDistribucion> filas) {
        StringBuilder sb = new StringBuilder();
        sb.append("Clasifica facturas de una municipalidad de Guatemala (presupuesto SICOIN).\n");
        sb.append("Para cada fila devuelve:\n");
        sb.append("- \"grupo\": uno de ").append(String.join(", ", ClasificadorLocalService.GRUPOS)).append('\n');
        sb.append("- \"renglon\": codigo SICOIN de 3 digitos (ej. 029, 111, 142, 274) o \"\" si no hay certeza\n");
        sb.append("- \"explicacion\": una frase corta en espanol\n");
        sb.append("Responde SOLO con un array JSON, sin texto extra ni markdown: ");
        sb.append("[{\"fila\":1,\"grupo\":\"...\",\"renglon\":\"...\",\"explicacion\":\"...\"}]\n\n");
        sb.append("Filas:\n");
        for (int i = 0; i < filas.size(); i++) {
            FilaDistribucion f = filas.get(i);
            sb.append(i + 1).append(". ")
                    .append(TextoUtil.corta(f.getDescripcion(), MAX_TEXTO_FILA))
                    .append(" — ")
                    .append(TextoUtil.corta(f.getEmisor(), MAX_TEXTO_FILA))
                    .append('\n');
        }
        return sb.toString();
    }

    /**
     * Saca el texto de candidates[0].content.parts[0].text, lo parsea como
     * el array pedido y vuelca grupo/renglon/explicacion por numero de fila.
     */
    private List<FilaDistribucion> aplicarRespuesta(String cuerpo, List<FilaDistribucion> filas)
            throws Exception {
        String texto = extraerTexto(cuerpo);
        if (texto == null) {
            log.warn("Gemini respondio sin texto usable; se usa el clasificador local");
            return List.of();
        }
        JsonNode arr = mapper.readTree(limpiarJson(texto));
        if (!arr.isArray()) {
            log.warn("Gemini no devolvio un array JSON; se usa el clasificador local");
            return List.of();
        }
        for (JsonNode item : arr) {
            int idx = item.path("fila").asInt(-1) - 1;
            if (idx < 0 || idx >= filas.size()) continue;
            FilaDistribucion f = filas.get(idx);
            f.setGrupo(normalizarGrupo(item.path("grupo").asText("")));
            f.setRenglon(normalizarRenglon(item.path("renglon").asText("")));
            f.setExplicacion(item.path("explicacion").asText(""));
        }
        return filas;
    }

    /** Texto de la primera parte del primer candidato; null si no viene. */
    private String extraerTexto(String cuerpo) throws Exception {
        JsonNode root = mapper.readTree(cuerpo);
        JsonNode text = root.path("candidates").path(0)
                .path("content").path("parts").path(0).path("text");
        return text.isTextual() ? text.asText() : null;
    }

    /** Quita la envoltura ```json ... ``` que a veces agrega el modelo. */
    private static String limpiarJson(String texto) {
        String t = texto.strip();
        if (t.startsWith("```")) {
            int salto = t.indexOf('\n');
            t = salto >= 0 ? t.substring(salto + 1) : t.substring(3);
            if (t.endsWith("```")) t = t.substring(0, t.length() - 3);
        }
        return t.strip();
    }

    /** Grupo normalizado (mayusculas, sin tildes); invalido -> OTROS. */
    private static String normalizarGrupo(String grupo) {
        String g = TextoUtil.norm(grupo);
        return ClasificadorLocalService.GRUPOS.contains(g) ? g : ClasificadorLocalService.GRUPO_OTROS;
    }

    /** Renglon: solo digitos, rellenado a 3 ("" si no es un codigo claro). */
    private static String normalizarRenglon(String renglon) {
        String r = renglon == null ? "" : renglon.replaceAll("[^0-9]", "");
        if (r.isEmpty() || r.length() > 3) return "";
        return Constantes.zfill3(r);
    }
}
