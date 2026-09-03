package com.granados.sistema.dafim.web;

import com.granados.sistema.dafim.compras.service.GestionBaseDatosComprasService;
import com.granados.sistema.dafim.paquetes.service.PaquetesService;
import com.granados.sistema.dafim.presupuesto.service.PresupuestoService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

/**
 * Bitacora general DAFIM: una sola busqueda sobre compras, presupuesto
 * (carga activa) y facturas FEL. No altera datos.
 */
@Controller
@RequestMapping("/dafim/bitacora")
public class BitacoraController {

    private final GestionBaseDatosComprasService gestionBd;
    private final PresupuestoService presupuesto;
    private final PaquetesService paquetes;

    public BitacoraController(GestionBaseDatosComprasService gestionBd,
                              PresupuestoService presupuesto,
                              PaquetesService paquetes) {
        this.gestionBd = gestionBd;
        this.presupuesto = presupuesto;
        this.paquetes = paquetes;
    }

    @GetMapping
    public String ver(@RequestParam(required = false) String q,
                      @RequestParam(required = false) String tipo,
                      @RequestParam(required = false) String renglon,
                      @RequestParam(required = false) Integer anio,
                      @RequestParam(required = false) Integer mes,
                      @RequestParam(required = false) Boolean sinPago,
                      Model model) {
        model.addAttribute("q", q == null ? "" : q);
        model.addAttribute("tipo", tipo == null || tipo.isBlank() ? "TODOS" : tipo);
        model.addAttribute("renglon", renglon == null ? "" : renglon);
        model.addAttribute("anio", anio);
        model.addAttribute("mes", mes);
        boolean soloSinPago = Boolean.TRUE.equals(sinPago);
        model.addAttribute("sinPago", soloSinPago);

        boolean hay = (q != null && !q.isBlank())
                || (renglon != null && !renglon.isBlank())
                || anio != null || mes != null || soloSinPago
                || "PUBLICACIONES".equalsIgnoreCase(tipo);
        model.addAttribute("buscado", hay);
        if (!hay) {
            model.addAttribute("contratos", List.of());
            model.addAttribute("proveedores", List.of());
            model.addAttribute("pagos", List.of());
            model.addAttribute("publicaciones", List.of());
            model.addAttribute("lineasPresupuesto", List.of());
            model.addAttribute("fuentes", List.of());
            model.addAttribute("apartados", List.of());
            model.addAttribute("facturas", List.of());
            return "dafim/bitacora";
        }

        Map<String, Object> res = gestionBd.buscar(q, tipo, renglon, anio, mes, soloSinPago);
        model.addAttribute("contratos", res.get("contratos"));
        model.addAttribute("proveedores", res.get("proveedores"));
        model.addAttribute("pagos", res.get("pagos"));
        model.addAttribute("publicaciones", res.get("publicaciones"));

        String texto = q == null ? "" : q;
        model.addAttribute("lineasPresupuesto", presupuesto.buscarLineas(texto));
        model.addAttribute("fuentes", presupuesto.buscarFuentes(texto));
        model.addAttribute("apartados", presupuesto.buscarApartados(texto));
        model.addAttribute("facturas", texto.isBlank() ? List.of() : paquetes.buscarGeneral(texto));
        return "dafim/bitacora";
    }
}
