package com.granados.sistema.dafim.presupuesto.distribuir;

import com.granados.sistema.dafim.compras.util.Constantes;
import com.granados.sistema.dafim.compras.util.TextoUtil;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Clasificador local por palabras clave: es el fallback cuando no hay
 * GEMINI_API_KEY configurada o la llamada a Gemini falla.
 *
 * El grupo (EDUCACION, MUNICIPAL, SALUD, INFRAESTRUCTURA, SERVICIOS,
 * OTROS) sale de GRUPO_KEYWORDS y el renglon reusando Constantes.KEYWORDS
 * del motor de compras. Todo el match es sobre el texto normalizado con
 * TextoUtil.norm (mayusculas, sin tildes), probando primero la palabra
 * clave mas larga: en "UTILES ESCOLARES ... LIBRERIA" gana EDUCACION
 * porque "UTILES ESCOLARES" es mas especifica que "LIBRERIA".
 */
@Service
public class ClasificadorLocalService {

    public static final String GRUPO_OTROS = "OTROS";

    /** Grupos validos, en el orden en que se muestran en la hoja. */
    public static final List<String> GRUPOS = List.of(
            "EDUCACION", "MUNICIPAL", "SALUD", "INFRAESTRUCTURA", "SERVICIOS", GRUPO_OTROS);

    /** Palabra clave (ya normalizada) -> grupo. El orden de insercion desempata. */
    private static final Map<String, String> GRUPO_KEYWORDS;

    /** Claves de GRUPO_KEYWORDS por longitud descendente (orden estable). */
    private static final List<String> GRUPO_KEYWORDS_ORDENADAS;

    static {
        LinkedHashMap<String, String> kw = new LinkedHashMap<>();
        // Educacion: utiles y vida escolar
        kw.put("REFACCION ESCOLAR", "EDUCACION");
        kw.put("VIAJES DE ALUMNOS", "EDUCACION");
        kw.put("UTILES ESCOLARES", "EDUCACION");
        kw.put("PREESCOLAR", "EDUCACION");
        kw.put("EDUCACION", "EDUCACION");
        kw.put("INSTITUTO", "EDUCACION");
        kw.put("ESCUELA", "EDUCACION");
        kw.put("COLEGIO", "EDUCACION");
        kw.put("MAESTROS", "EDUCACION");
        kw.put("DOCENTES", "EDUCACION");
        kw.put("MOCHILAS", "EDUCACION");
        kw.put("ALUMNOS", "EDUCACION");
        kw.put("KINDER", "EDUCACION");
        kw.put("BECAS", "EDUCACION");
        // Salud: centros, medicinas y atencion
        kw.put("CENTRO DE SALUD", "SALUD");
        kw.put("PUESTO DE SALUD", "SALUD");
        kw.put("MEDICAMENTOS", "SALUD");
        kw.put("MEDICINAS", "SALUD");
        kw.put("HOSPITAL", "SALUD");
        kw.put("FARMACIA", "SALUD");
        kw.put("CLINICA", "SALUD");
        kw.put("VACUNAS", "SALUD");
        kw.put("SALUD", "SALUD");
        // Infraestructura: materiales y obra publica
        kw.put("VIAJES DE MATERIAL", "INFRAESTRUCTURA");
        kw.put("VIBROCOMPACTADOR", "INFRAESTRUCTURA");
        kw.put("RETROEXCAVADORA", "INFRAESTRUCTURA");
        kw.put("RETROEXCABADORA", "INFRAESTRUCTURA");
        kw.put("CONSTRUCCION", "INFRAESTRUCTURA");
        kw.put("AGUA POTABLE", "INFRAESTRUCTURA");
        kw.put("MAQUINARIA", "INFRAESTRUCTURA");
        kw.put("BALASTRO", "INFRAESTRUCTURA");
        kw.put("BALASTO", "INFRAESTRUCTURA");
        kw.put("CEMENTO", "INFRAESTRUCTURA");
        kw.put("PIEDRIN", "INFRAESTRUCTURA");
        kw.put("ASFALTO", "INFRAESTRUCTURA");
        kw.put("DRENAJE", "INFRAESTRUCTURA");
        kw.put("PUENTE", "INFRAESTRUCTURA");
        kw.put("CAMINO", "INFRAESTRUCTURA");
        kw.put("HIERRO", "INFRAESTRUCTURA");
        kw.put("LAMINA", "INFRAESTRUCTURA");
        kw.put("CALLE", "INFRAESTRUCTURA");
        kw.put("ACERA", "INFRAESTRUCTURA");
        kw.put("ARENA", "INFRAESTRUCTURA");
        kw.put("FLETE", "INFRAESTRUCTURA");
        // Servicios: energia, telecom y servicios publicos
        kw.put("SERVICIOS FUNERARIOS", "SERVICIOS");
        kw.put("ENERGIA ELECTRICA", "SERVICIOS");
        kw.put("CAJAS MORTUARIAS", "SERVICIOS");
        kw.put("RECOLECCION", "SERVICIOS");
        kw.put("FIBRA OPTICA", "SERVICIOS");
        kw.put("ALUMBRADO", "SERVICIOS");
        kw.put("TELEFONIA", "SERVICIOS");
        kw.put("BOMBILLAS", "SERVICIOS");
        kw.put("FOTOCELDA", "SERVICIOS");
        kw.put("FUNERARIO", "SERVICIOS");
        kw.put("TELEFONO", "SERVICIOS");
        kw.put("INTERNET", "SERVICIOS");
        kw.put("BASURA", "SERVICIOS");
        // Municipal: gasto corriente de oficina y actividades
        kw.put("SERVICIOS PROFESIONALES", "MUNICIPAL");
        kw.put("INSUMOS DE LIBRERIA", "MUNICIPAL");
        kw.put("ALQUILER DE SILLAS", "MUNICIPAL");
        kw.put("UTILES DE OFICINA", "MUNICIPAL");
        kw.put("HOJAS MEMBRETADAS", "MUNICIPAL");
        kw.put("CANASTA BASICA", "MUNICIPAL");
        kw.put("AUDIO Y SONIDO", "MUNICIPAL");
        kw.put("ARRENDAMIENTO", "MUNICIPAL");
        kw.put("MUNICIPALIDAD", "MUNICIPAL");
        kw.put("PAPEL BOND", "MUNICIPAL");
        kw.put("MUNICIPAL", "MUNICIPAL");
        kw.put("LIBRERIA", "MUNICIPAL");
        kw.put("UNIFORME", "MUNICIPAL");
        kw.put("VIVERES", "MUNICIPAL");
        kw.put("MARIMBA", "MUNICIPAL");
        kw.put("TINTAS", "MUNICIPAL");
        kw.put("TOLDOS", "MUNICIPAL");
        kw.put("FERIA", "MUNICIPAL");
        GRUPO_KEYWORDS = Collections.unmodifiableMap(kw);

        List<String> orden = new ArrayList<>(kw.keySet());
        // sort estable: empates conservan orden de insercion (igual que Constantes)
        orden.sort((a, b) -> Integer.compare(b.length(), a.length()));
        GRUPO_KEYWORDS_ORDENADAS = Collections.unmodifiableList(orden);
    }

    /**
     * Clasifica la fila en sitio: llena grupo, renglon y explicacion a
     * partir de descripcion + emisor. Devuelve la misma fila.
     */
    public FilaDistribucion clasificar(FilaDistribucion fila) {
        String texto = TextoUtil.norm(fila.getDescripcion() + " " + fila.getEmisor());
        String kwGrupo = buscarKeywordGrupo(texto);
        String kwRenglon = buscarKeywordRenglon(texto);

        String grupo = kwGrupo == null ? GRUPO_OTROS : GRUPO_KEYWORDS.get(kwGrupo);
        String renglon = kwRenglon == null ? "" : Constantes.KEYWORDS.get(kwRenglon);

        fila.setGrupo(grupo);
        fila.setRenglon(renglon);
        fila.setExplicacion(explicacion(kwGrupo, grupo, kwRenglon, renglon));
        return fila;
    }

    /** Solo el grupo (EDUCACION, MUNICIPAL, ...; OTROS si nada pega). */
    public String clasificarGrupo(String descripcion, String emisor) {
        String kw = buscarKeywordGrupo(TextoUtil.norm(descripcion + " " + emisor));
        return kw == null ? GRUPO_OTROS : GRUPO_KEYWORDS.get(kw);
    }

    /** Solo el renglon SICOIN por keywords de compras ("" si nada pega). */
    public String clasificarRenglon(String descripcion, String emisor) {
        String kw = buscarKeywordRenglon(TextoUtil.norm(descripcion + " " + emisor));
        return kw == null ? "" : Constantes.KEYWORDS.get(kw);
    }

    /** Primera keyword de grupo que pega (la mas larga); null si ninguna. */
    private static String buscarKeywordGrupo(String textoNorm) {
        for (String kw : GRUPO_KEYWORDS_ORDENADAS) {
            if (textoNorm.contains(kw)) return kw;
        }
        return null;
    }

    /** Primera keyword de renglon que pega (la mas larga); null si ninguna. */
    private static String buscarKeywordRenglon(String textoNorm) {
        for (String kw : Constantes.KEYWORDS_ORDENADAS) {
            if (textoNorm.contains(kw)) return kw;
        }
        return null;
    }

    /** Frase corta que explica al usuario por que se propuso esa clasificacion. */
    private static String explicacion(String kwGrupo, String grupo, String kwRenglon, String renglon) {
        StringBuilder sb = new StringBuilder("Regla local: ");
        if (kwGrupo == null) {
            sb.append("sin palabra clave de grupo, queda en OTROS");
        } else {
            sb.append('\"').append(kwGrupo).append("\" -> ").append(grupo);
        }
        if (kwRenglon != null) {
            sb.append("; \"").append(kwRenglon).append("\" -> renglon ").append(renglon);
        }
        return sb.toString();
    }
}
