package com.granados.sistema.dafim.presupuesto.repository;

import com.granados.sistema.dafim.presupuesto.entity.FuenteFinanciamiento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FuenteFinanciamientoRepository extends JpaRepository<FuenteFinanciamiento, String> {
    List<FuenteFinanciamiento> findAllByOrderByCodigo();
}
