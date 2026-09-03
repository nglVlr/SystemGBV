package com.granados.sistema.dafim.compras.dto;

import java.io.Serializable;
import java.util.LinkedHashMap;

/** Estado en memoria de la BD (equivale a BD_CONTRATOS, BD_PROVEEDORES, BD_NPGS_029). */
public class DatosBd implements Serializable {
    private LinkedHashMap<String, ContratoInfo> contratos = new LinkedHashMap<>();
    private LinkedHashMap<String, ProveedorInfo> proveedores = new LinkedHashMap<>();
    private LinkedHashMap<String, String> npgs029 = new LinkedHashMap<>();

    public LinkedHashMap<String, ContratoInfo> getContratos() { return contratos; }
    public LinkedHashMap<String, ProveedorInfo> getProveedores() { return proveedores; }
    public LinkedHashMap<String, String> getNpgs029() { return npgs029; }
}
