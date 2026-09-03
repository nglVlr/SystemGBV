package com.granados.sistema.dafim.compras.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Constantes del dominio, copiadas EXACTAS del motor Python.
 * El orden de insercion de KEYWORDS importa: en caso de empate de longitud,
 * gana la que se declaro primero (sorted estable, igual que Python).
 */
public final class Constantes {

    private Constantes() {}

    public static final Set<String> RENGLONES_CD = Set.of(
            "029", "111", "113", "142", "154", "155", "162", "165", "182", "184", "186",
            "187", "188", "191", "196", "199", "211", "223", "232", "241", "262", "263",
            "274", "291", "292", "294", "297", "299", "322", "328", "329", "411");

    public static final Set<String> RENGLONES_EXCLUIDOS = Set.of(
            "011", "015", "022", "035", "051", "055", "062", "063",
            "331", "332", "413", "422", "435", "769");

    public static final Map<String, String> KEYWORDS;
    /** Claves de KEYWORDS ordenadas por longitud descendente (orden estable). */
    public static final List<String> KEYWORDS_ORDENADAS;

    /** Retencion fija de dietas de concejal (renglon 64) en el oficio de julio. */
    public static final double DESCUENTO_DIETA_CONCEJAL = 1760.00;

    public static final Set<String> PALABRAS_COMUNES = Set.of(
            "GARCIA", "GONZALEZ", "MARTINEZ", "REYES", "LOPEZ", "HERRERA",
            "PEREZ", "HERNANDEZ", "RUIZ", "MELGAR", "ALVARADO", "ROSALES",
            "DE", "LA", "LOS", "EL", "Y", "MAYEN", "ORTIZ", "GARC\u00CDA",
            "SOCIEDAD", "ANONIMA", "ANONIMAS", "COMPANIA", "COMPA\u00D1IA",
            "LIMITADA", "RESPONSABILIDAD", "COLECTIVA");

    public static final Map<Integer, String> MESES_NOMBRE;

    public static final Map<String, String> MES_MAP;

    static {
        LinkedHashMap<String, String> kw = new LinkedHashMap<>();
        kw.put("BASURA", "142"); kw.put("BALASTO", "142"); kw.put("BALASTRO", "142");
        kw.put("VIAJES DE MATERIAL", "142"); kw.put("FLETE", "142");
        kw.put("CAMIONADAS DE 10", "142"); kw.put("VIAJES DE ALUMNOS", "142");
        kw.put("PIEDRIN", "142");
        kw.put("MAQUINARIA", "154"); kw.put("HORAS DE RENTA", "154"); kw.put("PATROL", "154");
        kw.put("VIBROCOMPACTADOR", "154"); kw.put("RETROEXCABADORA", "154");
        kw.put("RETROEXCAVADORA", "154"); kw.put("TIPO PIPA", "154");
        kw.put("SACOS DE CEMENTO", "274"); kw.put("QUINTALES DE CEMENTO", "274");
        kw.put("CEMENTO", "274");
        kw.put("CAMIONADAS DE ARENA", "223");
        kw.put("INSUMOS DE LIMPIEZA", "292"); kw.put("UTILES DE LIMPIEZA", "292");
        kw.put("INSUMOS DE LIBRERIA", "291"); kw.put("UTILES DE OFICINA", "291");
        kw.put("LIBRERIA", "291"); kw.put("PAPEL BOND", "291");
        kw.put("HOJAS MEMBRETADAS", "291"); kw.put("TINTAS", "291");
        kw.put("VIVERES", "211"); kw.put("CANASTA BASICA", "211");
        kw.put("AUDIO Y SONIDO", "196"); kw.put("ACTIVIDADES DE FERIA", "196");
        kw.put("FERIA", "196"); kw.put("ALQUILER DE SILLAS", "196");
        kw.put("SERVICIO DE SONIDO", "196"); kw.put("ALIMENTACION", "196");
        kw.put("PRESENTACION ARTISTICA", "187"); kw.put("MARIMBA", "187");
        kw.put("INTERNET", "186"); kw.put("FIBRA OPTICA", "186");
        kw.put("ENERGIA ELECTRICA", "111");
        kw.put("CAJAS MORTUARIAS", "411"); kw.put("SERVICIOS FUNERARIOS", "411");
        kw.put("FUNERARIO", "411");
        kw.put("ARRENDAMIENTO DE BIEN MUEBLE", "155");
        kw.put("RESTAURACION Y PINTURA", "165"); kw.put("MANTENIMIENTO Y REPARACION", "165");
        kw.put("BOMBILLAS", "297"); kw.put("FOTOCELDA", "297");
        kw.put("TOLDOS", "199"); kw.put("ESTUDIOS DE PROYECTOS", "199");
        kw.put("CIRCUITO CERRADO", "199");
        kw.put("ALFOMBRA", "299"); kw.put("SEMILLAS", "299");
        kw.put("MATERIALES VARIOS", "299"); kw.put("MATERIALES UTILIZADOS", "299");
        kw.put("CONOS", "299"); kw.put("IMPLEMENTOS", "299");
        kw.put("UNIFORME", "294"); kw.put("SERVICIOS PROFESIONALES", "188");
        KEYWORDS = Collections.unmodifiableMap(kw);

        List<String> orden = new ArrayList<>(kw.keySet());
        // sort estable: empates conservan orden de insercion (igual que sorted de Python)
        orden.sort((a, b) -> Integer.compare(b.length(), a.length()));
        KEYWORDS_ORDENADAS = Collections.unmodifiableList(orden);

        LinkedHashMap<Integer, String> meses = new LinkedHashMap<>();
        meses.put(1, "ENERO"); meses.put(2, "FEBRERO"); meses.put(3, "MARZO");
        meses.put(4, "ABRIL"); meses.put(5, "MAYO"); meses.put(6, "JUNIO");
        meses.put(7, "JULIO"); meses.put(8, "AGOSTO"); meses.put(9, "SEPTIEMBRE");
        meses.put(10, "OCTUBRE"); meses.put(11, "NOVIEMBRE"); meses.put(12, "DICIEMBRE");
        MESES_NOMBRE = Collections.unmodifiableMap(meses);

        LinkedHashMap<String, String> mm = new LinkedHashMap<>();
        mm.put("ene", "01"); mm.put("feb", "02"); mm.put("mar", "03"); mm.put("abr", "04");
        mm.put("may", "05"); mm.put("jun", "06"); mm.put("jul", "07"); mm.put("ago", "08");
        mm.put("sep", "09"); mm.put("oct", "10"); mm.put("nov", "11"); mm.put("dic", "12");
        MES_MAP = Collections.unmodifiableMap(mm);
    }

    /**
     * Porteo de reng_kw(): busca palabras clave en el texto (en mayusculas),
     * probando primero las mas largas. Devuelve el renglon o "".
     */
    public static String rengKw(Object texto) {
        String t = String.valueOf(texto).toUpperCase();
        for (String kw : KEYWORDS_ORDENADAS) {
            if (t.contains(kw)) return KEYWORDS.get(kw);
        }
        return "";
    }

    /** Rellena con ceros a la izquierda hasta 3 (zfill(3) de Python). */
    public static String zfill3(String s) {
        StringBuilder sb = new StringBuilder(s);
        while (sb.length() < 3) sb.insert(0, '0');
        return sb.toString();
    }
}
