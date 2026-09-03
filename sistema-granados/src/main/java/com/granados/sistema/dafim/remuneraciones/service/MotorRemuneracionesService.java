package com.granados.sistema.dafim.remuneraciones.service;

import com.granados.sistema.dafim.compras.dto.RegistroSicoin;
import com.granados.sistema.dafim.compras.service.ValidadorComprasService;
import com.granados.sistema.dafim.compras.util.Constantes;
import com.granados.sistema.dafim.compras.util.TextoUtil;
import com.granados.sistema.dafim.remuneraciones.dto.FilaPlanilla;
import com.granados.sistema.dafim.remuneraciones.dto.FilaRemuneracion;
import com.granados.sistema.dafim.remuneraciones.dto.PersonaRrhh;
import com.granados.sistema.dafim.remuneraciones.dto.ResultadoRemuneraciones;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Arma el oficio LAIP de remuneraciones: planillas 011/022, SICOIN 029/035/064/188/183
 * y huecos del maestro RRHH.
 */
public final class MotorRemuneracionesService {

    static final List<String> ORDEN = List.of(
            "064", "011", "022", "029", "188", "183", "035");

    private static final Pattern RE_CARGO_COMO = Pattern.compile(
            "COMO:?\\s+(.+?)(?=\\s*,?\\s*SEG[UÚ]N\\b|\\s+CORRESPONDIENTE\\b|\\s+CONTRATO\\b|$)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern RE_ANTES_CONTRATO = Pattern.compile(
            "(.+?)\\s*,?\\s*SEG[UÚ]N\\s+CONTRATO", Pattern.CASE_INSENSITIVE);
    private static final Pattern RE_CONCEJAL = Pattern.compile(
            "CONCEJAL\\s+(III|II|I|3|2|1)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern RE_SINDICO = Pattern.compile(
            "S[IÍ]NDICO\\s+(II|I|2|1)?", Pattern.CASE_INSENSITIVE);

    private MotorRemuneracionesService() {}

    public static ResultadoRemuneraciones construir(
            int mes, int anio,
            List<FilaPlanilla> planillas,
            List<RegistroSicoin> sicoin,
            List<PersonaRrhh> rrhh) {
        if (planillas == null) planillas = List.of();
        if (sicoin == null) sicoin = List.of();
        if (rrhh == null) rrhh = List.of();

        Map<String, List<FilaRemuneracion>> por = new LinkedHashMap<>();
        for (String c : ORDEN) por.put(c, new ArrayList<>());
        List<String> alertas = new ArrayList<>();

        boolean hay011 = false;
        boolean hay022 = false;
        for (FilaPlanilla p : planillas) {
            String rg = normRenglon(p.getRenglon());
            if ("011".equals(rg)) hay011 = true;
            if ("022".equals(rg)) hay022 = true;
            if (!por.containsKey(rg)) continue;
            if (yaEsta(por.get(rg), p.getNombre())) continue;
            por.get(rg).add(desdePlanilla(p, rg));
        }
        if (!hay011) alertas.add("Falta la planilla 011 (cheque y/o deposito). Los presupuestados 011 pueden quedar incompletos.");
        if (!hay022) alertas.add("Falta la planilla 022.");

        for (RegistroSicoin s : sicoin) {
            if (s == null || !"IMPRESO".equals(s.getStatus())) continue;
            String rg = normRenglon(s.getRenglon());
            String desc = s.getDesc() == null ? "" : s.getDesc().toUpperCase(Locale.ROOT);
            if ("011".equals(rg) || "022".equals(rg)) {
                aplicarExtraSicoin(por.get(rg), s, desc);
                continue;
            }
            if (!por.containsKey(rg)) continue;
            if (yaEsta(por.get(rg), s.getNombre())) continue;
            por.get(rg).add(desdeSicoin(s, rg, rrhh));
        }

        for (PersonaRrhh e : rrhh) {
            if (e == null) continue;
            String rg = normRenglon(e.getRenglon());
            if (!por.containsKey(rg)) continue;
            if (yaEsta(por.get(rg), e.getNombre())) {
                enriquecer(por.get(rg), e);
                continue;
            }
            FilaRemuneracion f = nueva(rg, e.getNombre(), e.getCargo(), e.getDependencia());
            f.setIncompleta(true);
            if ("064".equals(rg)) {
                f.setDescuentos(0);
            }
            por.get(rg).add(f);
            alertas.add("Sin monto (completar a mano): " + e.getNombre()
                    + " · R" + f.renglonExcel());
        }

        for (String c : ORDEN) {
            for (FilaRemuneracion f : por.get(c)) {
                if (f.getDependencia() == null || f.getDependencia().isBlank()) {
                    f.setDependencia("MUNICIPALIDAD DE GRANADOS");
                }
            }
        }

        List<FilaRemuneracion> filas = new ArrayList<>();
        double totP = 0, totQ = 0, totR = 0;
        for (String c : ORDEN) {
            int n = 1;
            for (FilaRemuneracion f : por.get(c)) {
                f.setNumero(n++);
                filas.add(f);
                totP += f.getTotalIngresos();
                totQ += f.getDescuentos();
                totR += f.getLiquido();
            }
        }

        ResultadoRemuneraciones out = new ResultadoRemuneraciones();
        out.setMes(mes);
        out.setAnio(anio);
        out.setFilas(filas);
        out.setAlertas(alertas);
        out.setTotalIngresos(r2(totP));
        out.setTotalDescuentos(r2(totQ));
        out.setTotalLiquido(r2(totR));
        return out;
    }

    private static FilaRemuneracion desdePlanilla(FilaPlanilla p, String rg) {
        FilaRemuneracion f = nueva(rg, p.getNombre(), p.getCargo(), p.getDependencia());
        double pBruto = r2(p.totalIngresos());
        f.setBonifIncentivo(r2(p.getBoniLey()));
        f.setBonoEspecifico(r2(p.getBonifMunicipal()));
        f.setOtrasRemuneraciones(r2(p.getOtrosIngresos()));
        f.setSueldoBase(r2(Math.max(0, pBruto - f.getBonifIncentivo()
                - f.getBonoEspecifico() - f.getOtrasRemuneraciones())));
        f.setTotalIngresos(pBruto);
        f.setDescuentos(r2(p.descuentos()));
        double liq = p.getTotalRecibir() > 0 ? p.getTotalRecibir() : pBruto - f.getDescuentos();
        f.setLiquido(r2(Math.max(0, liq)));
        f.setIncompleta(pBruto <= 0);
        return f;
    }

    private static FilaRemuneracion desdeSicoin(RegistroSicoin s, String rg,
                                                List<PersonaRrhh> rrhh) {
        PersonaRrhh extra = buscarRrhh(rrhh, s.getNombre(), rg);
        String cargo = extraerCargo(s.getDesc());
        String dep = extraerDependencia(s.getDesc(), cargo);
        if (cargo.isEmpty() && extra != null) cargo = nn(extra.getCargo());
        if (dep.isEmpty() && extra != null) dep = nn(extra.getDependencia());
        FilaRemuneracion f = nueva(rg, s.getNombre(), cargo, dep);
        double monto = r2(s.getMonto());
        if ("064".equals(rg)) {
            f.setDietas(monto);
            f.setTotalIngresos(monto);
            if (monto > 0) {
                f.setDescuentos(Constantes.DESCUENTO_DIETA_CONCEJAL);
                f.setLiquido(r2(Math.max(0, monto - f.getDescuentos())));
            }
        } else {
            f.setSueldoBase(monto);
            f.setTotalIngresos(monto);
            f.setLiquido(monto);
        }
        f.setIncompleta(monto <= 0);
        return f;
    }

    private static void aplicarExtraSicoin(List<FilaRemuneracion> dest,
                                           RegistroSicoin s, String descU) {
        if (dest == null || dest.isEmpty()) return;
        FilaRemuneracion f = null;
        for (FilaRemuneracion x : dest) {
            if (mismoNombre(x.getNombre(), s.getNombre())) {
                f = x;
                break;
            }
        }
        if (f == null) return;
        if (f.getCargo() == null || f.getCargo().isBlank()) {
            String cargo = extraerCargo(s.getDesc());
            if (!cargo.isEmpty()) f.setCargo(cargo);
        }
        if (f.getDependencia() == null || f.getDependencia().isBlank()) {
            String dep = extraerDependencia(s.getDesc(), f.getCargo());
            if (!dep.isEmpty()) f.setDependencia(dep);
        }
        double monto = r2(s.getMonto());
        if (descU.contains("REPRESENTACION") || descU.contains("REPRESENTACIÓN")) {
            f.setGastosRepresentacion(r2(f.getGastosRepresentacion() + monto));
            f.setTotalIngresos(r2(f.getTotalIngresos() + monto));
            f.setLiquido(r2(Math.max(0, f.getTotalIngresos() - f.getDescuentos())));
        } else if (descU.contains("DIETA")) {
            f.setDietas(r2(f.getDietas() + monto));
            f.setTotalIngresos(r2(f.getTotalIngresos() + monto));
            f.setLiquido(r2(Math.max(0, f.getTotalIngresos() - f.getDescuentos())));
        }
    }

    private static void enriquecer(List<FilaRemuneracion> dest, PersonaRrhh e) {
        for (FilaRemuneracion f : dest) {
            if (!mismoNombre(f.getNombre(), e.getNombre())) continue;
            if (f.getCargo() == null || f.getCargo().isBlank()) f.setCargo(nn(e.getCargo()));
            if (f.getDependencia() == null || f.getDependencia().isBlank()) {
                f.setDependencia(nn(e.getDependencia()));
            }
            return;
        }
    }

    private static PersonaRrhh buscarRrhh(List<PersonaRrhh> rrhh, String nombre, String rg) {
        for (PersonaRrhh e : rrhh) {
            if (rg.equals(normRenglon(e.getRenglon())) && mismoNombre(nombre, e.getNombre())) {
                return e;
            }
        }
        for (PersonaRrhh e : rrhh) {
            if (mismoNombre(nombre, e.getNombre())) return e;
        }
        return null;
    }

    static String extraerCargo(String desc) {
        if (desc == null || desc.isBlank()) return "";
        String d = desc.replaceAll("\\s+", " ").strip();
        Matcher mComo = RE_CARGO_COMO.matcher(d);
        if (mComo.find()) {
            String cargo = limpiarCargo(mComo.group(1));
            cargo = cargo.replaceAll("(?i)\\s+DEL CONSEJO MUNICIPAL\\s*$", "").strip();
            if (!esRuidoCargo(cargo)) return cargo;
        }
        Matcher mAntes = RE_ANTES_CONTRATO.matcher(d);
        if (mAntes.find()) {
            String cargo = limpiarCargo(quitarBoilerplate(mAntes.group(1)));
            if (!esRuidoCargo(cargo)) return cargo;
        }
        String u = TextoUtil.norm(d);
        Matcher mc = RE_CONCEJAL.matcher(d);
        if (mc.find()) {
            String n = mc.group(1).toUpperCase(Locale.ROOT)
                    .replace("3", "III").replace("2", "II").replace("1", "I");
            return "CONCEJAL " + n;
        }
        if (u.contains("VICE ALCALDE") || u.contains("VICEALCALDE")) return "VICE ALCALDE";
        Matcher ms = RE_SINDICO.matcher(d);
        if (ms.find()) {
            String n = ms.group(1) == null ? "" : ms.group(1).toUpperCase(Locale.ROOT)
                    .replace("2", "II").replace("1", "I");
            return ("SINDICO " + n).strip();
        }
        if (u.contains("ASESOR LEGAL")) return "ASESOR LEGAL";
        if (u.contains("SUPERVISOR")) return "SUPERVISOR DE OBRAS";
        if (u.contains("LIMPIEZA") || u.contains("CALLES")) {
            if (u.contains("COORDINADOR")) return "COORDINADOR DE LIMPIEZA";
            if (u.contains("ENCARGADA")) return "ENCARGADA DE LIMPIEZA";
            if (u.contains("ENCARGADO")) return "ENCARGADO DE LIMPIEZA";
            return "TRABAJADOR DE LIMPIEZA";
        }
        if (u.contains("SERVICIOS PROFESIONALES")) return "SERVICIOS PROFESIONALES";
        return "";
    }

    static String extraerDependencia(String desc, String cargo) {
        String blob = TextoUtil.norm((desc == null ? "" : desc) + " " + (cargo == null ? "" : cargo));
        if (blob.contains("CONSEJO MUNICIPAL") || blob.contains("CONCEJO MUNICIPAL")) {
            return "CONSEJO MUNICIPAL";
        }
        if (blob.contains("UGAM")) return "UGAM";
        if (blob.contains("DAFIM") || blob.contains("IUSI")) return "DAFIM";
        if (blob.contains("RELACIONES PUBLIC")) return "RELACIONES PUBLICAS";
        if (blob.contains("SERVICIOS PUBLIC")) return "SERVICIOS PUBLICOS";
        if (blob.contains("RECURSOS HUMAN")) return "RECURSOS HUMANOS";
        if (blob.contains("CATASTRO")) return "CATASTRO";
        if (blob.contains("DIRECCION MUNICIPAL DE PLANIFICACION")
                || blob.contains(" DMP") || blob.endsWith(" DMP") || blob.contains("DMP ")) {
            return "DMP";
        }
        if (blob.contains("PLANIFICACION")) return "DMP";
        if (blob.contains("OFICINA MUNICIPAL DE LA MUJER")
                || blob.contains("MUNICIPAL DE LA MUJER") || blob.contains(" OMM")) {
            return "OMM";
        }
        if (blob.contains("OMNA") || blob.contains("NINEZ")) return "OMNA";
        if (blob.contains("DISCAPACIDAD")) return "OFICINA DE DISCAPACIDAD";
        if (blob.contains("SECRETARIA MUNICIPAL")) return "SECRETARIA MUNICIPAL";
        if (blob.contains("ADULTO MAYOR")) return "ADULTO MAYOR";
        if (blob.contains("BODEGA")) return "BODEGA MUNICIPAL";
        if (blob.contains("LAIP") || blob.contains("INFORMACION PUB")) return "LAIP";
        if (blob.contains("CULTURA")) return "CULTURA Y DEPORTES";
        return "";
    }

    private static String quitarBoilerplate(String s) {
        String t = s == null ? "" : s.strip();
        for (int n = 0; n < 8; n++) {
            String prev = t;
            t = t.replaceFirst("(?i)^POR\\s+", "");
            t = t.replaceFirst("(?i)^PAGO DE (LA )?PLANILLA\\b.*$", "");
            t = t.replaceFirst("(?i)^SERVICIOS TECNICOS\\s+", "");
            t = t.replaceFirst("(?i)^SERVICIOS PROFESIONALES\\s+", "");
            t = t.replaceFirst("(?i)^SERVICIOS PRESTADOS\\s+", "");
            t = t.replaceFirst("(?i)^PRESTADOS\\s+", "");
            t = t.replaceFirst("(?i)^A LA\\s+", "");
            t = t.replaceFirst("(?i)^MUNICIPALIDAD DE GRNADOS BAJA VERPAZ\\s*", "");
            t = t.replaceFirst("(?i)^MUNICIPALIDAD DE GRANADOS( BAJA VERAPAZ)?\\s*,?\\s*", "");
            t = t.replaceFirst("(?i)^B\\.V\\.\\s*,?\\s*", "");
            t = t.replaceFirst("(?i)^COMO:?\\s+", "");
            t = t.strip();
            if (t.equals(prev)) break;
        }
        return t;
    }

    private static String limpiarCargo(String s) {
        if (s == null) return "";
        String t = s.replaceAll("\\s+", " ").strip();
        t = t.replaceAll("[,.;]+$", "").strip();
        if (t.length() > 90) t = t.substring(0, 90).strip();
        return t;
    }

    private static boolean esRuidoCargo(String cargo) {
        if (cargo == null || cargo.length() < 4) return true;
        String u = TextoUtil.norm(cargo);
        if (u.startsWith("POR SERVICIOS") || u.startsWith("PAGO DE")) return true;
        if (u.contains("DURANTE AL MES") || u.contains("CORRESPONDIENTE AL")) return true;
        if (u.equals("MUNICIPALIDAD DE GRANADOS") || u.equals("MUNICIPALIDAD DE GRANADOS BAJA VERAPAZ")) {
            return true;
        }
        return u.split(" ").length > 14;
    }

    static boolean mismoNombre(String a, String b) {
        String na = TextoUtil.norm(a);
        String nb = TextoUtil.norm(b);
        if (na.isEmpty() || nb.isEmpty()) return false;
        if (na.equals(nb)) return true;
        Set<String> pa = TextoUtil.palabras(a);
        Set<String> pb = TextoUtil.palabras(b);
        int inter = TextoUtil.interseccion(pa, pb);
        if (inter >= 3) return true;
        return inter >= 2 && pa.size() <= 4 && pb.size() <= 4;
    }

    static String normRenglon(String s) {
        if (s == null) return "";
        String d = s.trim().replaceAll("[^0-9]", "");
        if (d.isEmpty()) return "";
        if (d.length() > 3) d = d.substring(d.length() - 3);
        return Constantes.zfill3(d);
    }

    private static boolean yaEsta(List<FilaRemuneracion> lista, String nombre) {
        for (FilaRemuneracion f : lista) {
            if (mismoNombre(f.getNombre(), nombre)) return true;
        }
        return false;
    }

    private static FilaRemuneracion nueva(String rg, String nombre, String cargo, String dep) {
        FilaRemuneracion f = new FilaRemuneracion();
        f.setRenglon(rg);
        f.setNombre(nombre == null ? "" : nombre);
        f.setCargo(cargo == null ? "" : cargo);
        f.setDependencia(dep == null ? "" : dep);
        return f;
    }

    private static String nn(String s) {
        return s == null ? "" : s;
    }

    private static double r2(double v) {
        return ValidadorComprasService.round2(v);
    }
}
