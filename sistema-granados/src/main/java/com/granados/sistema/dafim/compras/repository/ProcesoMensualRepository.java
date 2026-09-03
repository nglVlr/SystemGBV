package com.granados.sistema.dafim.compras.repository;

import com.granados.sistema.dafim.compras.entity.ProcesoMensual;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProcesoMensualRepository extends JpaRepository<ProcesoMensual, Long> {
    Optional<ProcesoMensual> findTopByOrderByAnioDescMesDesc();
    List<ProcesoMensual> findByAnioAndMesOrderByFechaProcesoDesc(Integer anio, Integer mes);
}
