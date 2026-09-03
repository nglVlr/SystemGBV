package com.granados.sistema.dafim.presupuesto.parser;

import com.granados.sistema.dafim.presupuesto.dto.EjecucionParseada;
import com.granados.sistema.dafim.presupuesto.dto.LineaEjecucion;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser del reporte SICOIN GL "Ejecucion de Egresos del Ejercicio"
 * (R00814981.rpt), pieza de entrada del modulo de presupuesto: la
 * municipalidad necesita la ejecucion oficial por renglon y fuente para
 * compararla contra lo registrado en el sistema, asi que los importes se
 * leen tal cual vienen impresos (BigDecimal, al centavo).
 *
 * Estructura del documento (PDFBox con setSortByPosition(true)):
 * - Encabezados de pagina repetidos (SIAF, MUNICIPALIDAD, periodo, titulos
 *   de columnas) que se descartan.
 * - Filas de jerarquia (programa/subprograma/proyecto/actividad/obra) con
 *   7 importes acumulados; NO son lineas de datos pero fijan el contexto.
 *   El primer nivel de 3 digitos tras el subprograma es el proyecto; el
 *   siguiente con nombre (si no es obra) es la actividad, campo propio.
 * - Filas de datos con ancla "renglon fuente" (p.ej. "011 21-0101-0001")
 *   seguidas de descripcion y 11 importes en este orden: Asignado,
 *   Modificado, Vigente, PreCompromiso, Compromiso, Devengado, Pagado,
 *   ExtraPresupuestario, SaldoDisponible, SaldoPorDevengar, SaldoPorPagar.
 * - El codigo "Act O" (obra) llega como linea suelta de 3 digitos antes
 *   del bloque de renglones al que aplica (quirk del sortByPosition).
 *   "000 SIN OBRA" o un nombre seguido de ese codigo suelto es obra, no
 *   una actividad nueva.
 * - La descripcion puede continuar en lineas siguientes en mayusculas
 *   ("OTRAS REMUNERACIONES DE PERSONAL" + "TEMPORAL").
 *
 * Una fila de datos siempre completa sus 11 importes en su propia linea
 * con este extractor; aun asi se acumulan lineas siguientes por defensa
 * (otros SICOIN o cambios de fuente podrian partirlas).
 */
public final class ParserEjecucionEgresos {

    private static final int NUM_MONTOS = 11;

    private static final Pattern RE_ANCLA = Pattern.compile(
            "^\\s*(\\d{3})\\s+(\\d{2}-\\d{4}-\\d{4})\\s*(.*)$");
    private static final Pattern RE_MONTO = Pattern.compile("-?\\d[\\d,]*\\.\\d{2}");
    private static final Pattern RE_PERIODO = Pattern.compile(
            "Periodo del:\\s*(\\d{2}/\\d{2}/\\d{4})\\s*al:\\s*(\\d{2}/\\d{2}/\\d{4})");
    private static final Pattern RE_PROGRAMA = Pattern.compile("^\\d{2}\\p{L}.*");
    private static final Pattern RE_SUBPROGRAMA = Pattern.compile("^\\d{2}\\s+\\S.*");
    private static final Pattern RE_JERARQUIA3 = Pattern.compile("^\\d{3}\\s+\\S.*");
    private static final Pattern RE_OBRA_SUELTA = Pattern.compile("^\\d{3}$");
    private static final Pattern RE_OBRA_EN_DESC = Pattern.compile("^(\\d{3})(?:\\s+(.*))?$");
    private static final Pattern RE_TOTAL = Pattern.compile("^TOTAL\\b");
    private static final Pattern RE_ENCABEZADO = Pattern.compile(
            "^(SIAF:|MUNICIPALIDAD|DEPARTAMENTO|CLASIFICACI|Usuario:|Ejecuci|"
                    + "Todos los programas|EN EL EJERCICIO|Prog Subp|Pre PRESUPUESTARIO|"
                    + "Asignado Modificado|Renglon\\s)", Pattern.CASE_INSENSITIVE);
    private static final DateTimeFormatter FMT_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private ParserEjecucionEgresos() {}

    public static EjecucionParseada parsear(InputStream in) throws IOException {
        List<String> lineas = new ArrayList<>();
        try (PDDocument doc = PDDocument.load(in)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            for (int p = 1; p <= doc.getNumberOfPages(); p++) {
                stripper.setStartPage(p);
                stripper.setEndPage(p);
                for (String l : stripper.getText(doc).split("\n")) lineas.add(l);
            }
        }
        return parsearLineas(lineas);
    }

    /** Separado para poder probarse con texto plano sin un PDF real. */
    public static EjecucionParseada parsearLineas(List<String> lineas) {
        EjecucionParseada ejecucion = new EjecucionParseada();
        List<LineaEjecucion> datos = new ArrayList<>();
        String programa = "", subprograma = "", proyecto = "", actividad = "", actividadObra = "";
        LineaEjecucion abierta = null;

        for (int i = 0; i < lineas.size(); i++) {
            String ln = lineas.get(i).strip();
            if (ln.isEmpty()) continue;

            Matcher mper = RE_PERIODO.matcher(ln);
            if (mper.find()) {
                ejecucion.setPeriodoDesde(LocalDate.parse(mper.group(1), FMT_FECHA));
                ejecucion.setPeriodoHasta(LocalDate.parse(mper.group(2), FMT_FECHA));
                ejecucion.setAnio(ejecucion.getPeriodoDesde().getYear());
                continue;
            }
            if (RE_ENCABEZADO.matcher(ln).lookingAt()) continue; // no cierra la linea abierta (salto de pagina)
            if (RE_TOTAL.matcher(ln).lookingAt()) { abierta = null; continue; }

            Matcher man = RE_ANCLA.matcher(ln);
            if (man.matches()) {
                StringBuilder resto = new StringBuilder(man.group(3));
                while (contarMontos(resto) < NUM_MONTOS && i + 1 < lineas.size()) {
                    i++;
                    resto.append(' ').append(lineas.get(i).strip());
                }
                LineaEjecucion lin = construirLinea(man.group(1), man.group(2), resto.toString(),
                        programa, subprograma, proyecto, actividad, actividadObra);
                datos.add(lin);
                abierta = lin;
                continue;
            }

            int montos = contarMontos(ln);
            if (montos == 7) {
                // fila de jerarquia con acumulados: fija contexto, no es dato
                abierta = null;
                if (RE_PROGRAMA.matcher(ln).matches()) {
                    programa = ln.substring(0, 2) + " " + nombreSinMontos(ln.substring(2));
                    subprograma = ""; proyecto = ""; actividad = ""; actividadObra = "";
                } else if (RE_SUBPROGRAMA.matcher(ln).matches()) {
                    subprograma = ln.substring(0, 2) + " " + nombreSinMontos(ln.substring(2).strip());
                    proyecto = ""; actividad = ""; actividadObra = "";
                } else if (RE_JERARQUIA3.matcher(ln).matches()) {
                    String etiqueta = ln.substring(0, 3) + " " + nombreSinMontos(ln.substring(3).strip());
                    if (proyecto.isEmpty()) {
                        proyecto = etiqueta;
                        actividad = "";
                    } else if (esFilaObra(etiqueta, lineas, i)) {
                        // obra nombrada o "SIN OBRA"; Act O llega como linea suelta
                    } else {
                        actividad = etiqueta;
                    }
                    actividadObra = "";
                }
                continue;
            }
            if (RE_OBRA_SUELTA.matcher(ln).matches()) {
                actividadObra = ln; // codigo "Act O" del bloque de renglones que sigue
                abierta = null;
                continue;
            }
            if (montos == 0 && abierta != null) {
                // continuacion de la descripcion de la linea de datos abierta
                abierta.setDescripcion((abierta.getDescripcion() + " " + ln).strip());
            }
        }

        ejecucion.setLineas(datos);
        BigDecimal totalVigente = BigDecimal.ZERO, totalDevengado = BigDecimal.ZERO, totalPagado = BigDecimal.ZERO;
        for (LineaEjecucion l : datos) {
            totalVigente = totalVigente.add(l.getVigente());
            totalDevengado = totalDevengado.add(l.getDevengado());
            totalPagado = totalPagado.add(l.getPagado());
        }
        ejecucion.setTotalVigente(totalVigente);
        ejecucion.setTotalDevengado(totalDevengado);
        ejecucion.setTotalPagado(totalPagado);
        return ejecucion;
    }

    /** Arma la linea de datos: descripcion antes del primer importe y los 11 montos en orden. */
    private static LineaEjecucion construirLinea(String renglon, String fuente, String resto,
                                                 String programa, String subprograma,
                                                 String proyecto, String actividad,
                                                 String actividadObra) {
        List<BigDecimal> montos = new ArrayList<>();
        Matcher m = RE_MONTO.matcher(resto);
        int primerMonto = -1;
        while (m.find()) {
            if (primerMonto < 0) primerMonto = m.start();
            montos.add(new BigDecimal(m.group().replace(",", "")));
        }
        if (montos.size() != NUM_MONTOS) {
            throw new IllegalStateException("Fila de ejecucion con " + montos.size()
                    + " importes (se esperaban " + NUM_MONTOS + "): " + renglon + " " + fuente + " " + resto);
        }
        String desc = primerMonto >= 0 ? resto.substring(0, primerMonto).strip() : resto.strip();
        // el codigo de obra puede venir pegado a la fuente dentro de la propia linea
        String obra = actividadObra;
        Matcher mobra = RE_OBRA_EN_DESC.matcher(desc);
        if (mobra.matches()) {
            obra = mobra.group(1);
            desc = mobra.group(2) != null ? mobra.group(2).strip() : "";
        }

        LineaEjecucion l = new LineaEjecucion();
        l.setRenglon(renglon);
        l.setFuente(fuente);
        l.setDescripcion(desc);
        l.setPrograma(programa);
        l.setSubprograma(subprograma);
        l.setProyecto(proyecto);
        l.setActividad(actividad == null ? "" : actividad);
        l.setActividadObra(obra);
        l.setAsignado(montos.get(0));
        l.setModificado(montos.get(1));
        l.setVigente(montos.get(2));
        l.setPreCompromiso(montos.get(3));
        l.setCompromiso(montos.get(4));
        l.setDevengado(montos.get(5));
        l.setPagado(montos.get(6));
        l.setExtraPresupuestario(montos.get(7));
        l.setSaldoDisponible(montos.get(8));
        l.setSaldoPorDevengar(montos.get(9));
        l.setSaldoPorPagar(montos.get(10));
        return l;
    }

    private static int contarMontos(CharSequence texto) {
        int n = 0;
        Matcher m = RE_MONTO.matcher(texto);
        while (m.find()) n++;
        return n;
    }

    /** Recorta el nombre de una fila de jerarquia justo antes de sus importes acumulados. */
    private static String nombreSinMontos(String texto) {
        Matcher m = RE_MONTO.matcher(texto);
        int corte = m.find() ? m.start() : texto.length();
        return texto.substring(0, corte).strip();
    }

    /**
     * Obra (no actividad): el nombre dice "SIN OBRA", o despues del
     * posible corte de nombre viene el codigo Act O suelto de 3 digitos.
     */
    private static boolean esFilaObra(String etiqueta, List<String> lineas, int indice) {
        String nombre = etiqueta == null ? "" : etiqueta.toUpperCase();
        if (nombre.contains("SIN OBRA")) return true;
        for (int j = indice + 1; j < lineas.size(); j++) {
            String n = lineas.get(j).strip();
            if (n.isEmpty()) continue;
            if (RE_ENCABEZADO.matcher(n).lookingAt()) continue;
            if (RE_OBRA_SUELTA.matcher(n).matches()) return true;
            if (contarMontos(n) == 0 && !RE_ANCLA.matcher(n).matches()
                    && !RE_TOTAL.matcher(n).lookingAt()) {
                continue;
            }
            return false;
        }
        return false;
    }
}
