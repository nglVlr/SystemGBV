package com.granados.sistema.dafim.compras.dto;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/** Retorno de validar(): ok + reporte linea por linea. */
public class ResultadoValidacion implements Serializable {
    private boolean ok;
    private List<String> reporte = new ArrayList<>();

    public boolean isOk() { return ok; }
    public void setOk(boolean ok) { this.ok = ok; }
    public List<String> getReporte() { return reporte; }
    public void setReporte(List<String> reporte) { this.reporte = reporte; }
}
