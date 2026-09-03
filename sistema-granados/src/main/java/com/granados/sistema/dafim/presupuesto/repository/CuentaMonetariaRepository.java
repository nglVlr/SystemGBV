package com.granados.sistema.dafim.presupuesto.repository;

import com.granados.sistema.dafim.presupuesto.entity.CuentaMonetaria;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CuentaMonetariaRepository extends JpaRepository<CuentaMonetaria, Long> {
    List<CuentaMonetaria> findByCargaId(Long cargaId);
    long deleteByCargaId(Long cargaId);
}
