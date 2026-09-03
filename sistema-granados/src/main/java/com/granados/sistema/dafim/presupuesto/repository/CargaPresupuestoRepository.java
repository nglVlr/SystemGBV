package com.granados.sistema.dafim.presupuesto.repository;

import com.granados.sistema.dafim.presupuesto.entity.CargaPresupuesto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CargaPresupuestoRepository extends JpaRepository<CargaPresupuesto, Long> {
    Optional<CargaPresupuesto> findTopByEstadoOrderByFechaCargaDesc(String estado);
    List<CargaPresupuesto> findAllByOrderByFechaCargaDesc();
}
