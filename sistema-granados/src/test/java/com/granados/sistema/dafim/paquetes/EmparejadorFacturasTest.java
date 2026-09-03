package com.granados.sistema.dafim.paquetes;

import com.granados.sistema.dafim.paquetes.dto.FacturaSatDatos;
import com.granados.sistema.dafim.paquetes.dto.PaqueteDatos;
import com.granados.sistema.dafim.paquetes.service.EmparejadorFacturas;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmparejadorFacturasTest {

    private static FacturaSatDatos factura(String aut, String desc,
                                           double monto, LocalDate fecha) {
        FacturaSatDatos f = new FacturaSatDatos();
        f.setAutorizacion(aut);
        f.setDescripcion(desc);
        f.setMonto(monto);
        f.setFechaEmision(fecha);
        f.setArchivo(aut + ".pdf");
        return f;
    }

    @Test
    void mismaDescripcionYMontoSeUsanUnaSolaVez() {
        // dos lineas identicas y dos facturas identicas (distinto DTE)
        PaqueteDatos p = new PaqueteDatos();
        p.setNombreHoja("PAQUETE 1");
        p.getLineas().add(new PaqueteDatos.Linea(1,
                "pago de 18 horas de renta de maquinaria tipo patrol", 9000));
        p.getLineas().add(new PaqueteDatos.Linea(2,
                "pago de 18 horas de renta de maquinaria tipo patrol", 9000));

        List<FacturaSatDatos> fs = new ArrayList<>();
        fs.add(factura("AAAA0001-0000-0000-0000-000000000000",
                "18 Horas de renta de maquinaria tipo patrol", 9000,
                LocalDate.of(2026, 7, 3)));
        fs.add(factura("AAAA0002-0000-0000-0000-000000000000",
                "18 Horas de renta de maquinaria tipo patrol", 9000,
                LocalDate.of(2026, 7, 5)));

        EmparejadorFacturas.Resultado r =
                EmparejadorFacturas.emparejar(List.of(p), fs, Set.of());
        assertEquals(2, r.asignaciones.get(0).size());
        // cada factura una sola vez
        assertEquals(2, Set.copyOf(r.asignaciones.get(0).values()).size());
        // la mas antigua va primero
        assertEquals(0, r.asignaciones.get(0).get(1));
        assertEquals(1, r.asignaciones.get(0).get(2));
        assertTrue(r.sinPaquete.isEmpty());
    }

    @Test
    void facturaYaGuardadaNoSeReusa() {
        PaqueteDatos p = new PaqueteDatos();
        p.getLineas().add(new PaqueteDatos.Linea(1, "flete de arena", 800));
        List<FacturaSatDatos> fs = List.of(
                factura("BBBB0001-0000-0000-0000-000000000000",
                        "flete de arena", 800, LocalDate.of(2026, 7, 1)));
        EmparejadorFacturas.Resultado r = EmparejadorFacturas.emparejar(
                List.of(p), fs,
                Set.of("BBBB0001-0000-0000-0000-000000000000"));
        assertEquals(1, r.yaEnBd.size());
        assertTrue(r.asignaciones.get(0).isEmpty());
    }

    @Test
    void repetidaEnElLoteSeDescarta() {
        PaqueteDatos p = new PaqueteDatos();
        p.getLineas().add(new PaqueteDatos.Linea(1, "flete de piedrin", 700));
        List<FacturaSatDatos> fs = List.of(
                factura("CCCC0001-0000-0000-0000-000000000000",
                        "flete de piedrin", 700, LocalDate.of(2026, 7, 1)),
                factura("CCCC0001-0000-0000-0000-000000000000",
                        "flete de piedrin", 700, LocalDate.of(2026, 7, 1)));
        EmparejadorFacturas.Resultado r =
                EmparejadorFacturas.emparejar(List.of(p), fs, Set.of());
        assertEquals(1, r.repetidasEnLote.size());
        assertEquals(1, r.asignaciones.get(0).size());
    }

    @Test
    void lineaViudaNoRobaFacturaDeOtroCaserio() {
        // La factura correcta de la linea NO vino (corrupta/faltante).
        // Existen facturas del mismo monto pero de OTROS caserios:
        // la linea debe quedar PENDIENTE, jamas asignarse a otro caserio.
        PaqueteDatos p = new PaqueteDatos();
        p.getLineas().add(new PaqueteDatos.Linea(1,
                "pago de 18 horas de renta de maquinaria tipo patrol para "
                        + "mejoramiento de carretera caserio Cabrera Granados", 9000));
        List<FacturaSatDatos> fs = List.of(
                factura("AAAA1111-0000-0000-0000-000000000000",
                        "pago de 18 horas de renta de maquinaria tipo patrol para "
                                + "mejoramiento de carretera caserio Pastor Granados",
                        9000, LocalDate.of(2026, 7, 2)),
                factura("AAAA2222-0000-0000-0000-000000000000",
                        "pago de 18 horas de renta de maquinaria tipo patrol para "
                                + "mejoramiento de carretera caserio Aviadero Granados",
                        9000, LocalDate.of(2026, 7, 3)));
        EmparejadorFacturas.Resultado r =
                EmparejadorFacturas.emparejar(List.of(p), fs, Set.of());
        assertTrue(r.asignaciones.get(0).isEmpty(),
                "no debe asignar una factura de otro caserio");
        assertEquals(2, r.sinPaquete.size());
    }

    @Test
    void typoDelExcelSeAsignaSiNoHayCompetencia() {
        // "maqunaria" (typo) vs "maquinaria": debe reconocerse como la misma
        PaqueteDatos p = new PaqueteDatos();
        p.getLineas().add(new PaqueteDatos.Linea(1,
                "pago de 16 horas de renta de maqunaria tipo vibrocompactador "
                        + "para mejoramiento de carretera caserio Cabrera", 8000));
        List<FacturaSatDatos> fs = List.of(
                factura("BBBB1111-0000-0000-0000-000000000000",
                        "pago de 16 horas de renta de maquinaria tipo vibrocompactador "
                                + "para mejoramiento de carretera caserio Cabrera",
                        8000, LocalDate.of(2026, 7, 4)));
        EmparejadorFacturas.Resultado r =
                EmparejadorFacturas.emparejar(List.of(p), fs, Set.of());
        assertEquals(1, r.asignaciones.get(0).size(), "el typo debe tolerar el match");
        assertEquals(0, r.asignaciones.get(0).get(1));
    }

    @Test
    void ambiguaEntreDosParecidasQuedaPendiente() {
        // La linea tiene un typo y hay DOS facturas parecidas de caserios
        // distintos con el mismo monto: es ambiguo, debe quedar pendiente.
        PaqueteDatos p = new PaqueteDatos();
        p.getLineas().add(new PaqueteDatos.Linea(1,
                "pago de 8 camionadas de material balasto para mejoramiento "
                        + "de carretera caserio San Antonio", 8000));
        List<FacturaSatDatos> fs = List.of(
                factura("CCCC1111-0000-0000-0000-000000000000",
                        "pago de 8 camionadas de material balasto para mejoramiento "
                                + "de carretera caserio San Jose",
                        8000, LocalDate.of(2026, 7, 2)),
                factura("CCCC2222-0000-0000-0000-000000000000",
                        "pago de 8 camionadas de material balasto para mejoramiento "
                                + "de carretera caserio San Miguel",
                        8000, LocalDate.of(2026, 7, 3)));
        EmparejadorFacturas.Resultado r =
                EmparejadorFacturas.emparejar(List.of(p), fs, Set.of());
        assertTrue(r.asignaciones.get(0).isEmpty(),
                "con dos candidatas parecidas no se adivina");
    }

    @Test
    void montoDistintoNuncaSeAsigna() {
        PaqueteDatos p = new PaqueteDatos();
        p.getLineas().add(new PaqueteDatos.Linea(1, "renta de maquinaria", 9000));
        List<FacturaSatDatos> fs = List.of(
                factura("DDDD0001-0000-0000-0000-000000000000",
                        "renta de maquinaria", 8000, LocalDate.of(2026, 7, 1)));
        EmparejadorFacturas.Resultado r =
                EmparejadorFacturas.emparejar(List.of(p), fs, Set.of());
        assertTrue(r.asignaciones.get(0).isEmpty());
        assertEquals(1, r.sinPaquete.size());
    }

    @Test
    void similitudToleraTildesYTypos() {
        double s = EmparejadorFacturas.similitud(
                "Pago de 16 horas de renta de maqunaria tipo vibrocompactador  para mejoramiento de carretera  Aldea Ixchel",
                "16 horas de renta de maquinaria tipo vibrocompactador para mejoramiento de carretera aldea Ixchel");
        assertTrue(s >= 0.45, "similitud=" + s);
    }
}
