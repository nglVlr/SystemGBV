package com.granados.sistema.dafim.presupuesto.repository;

import com.granados.sistema.dafim.presupuesto.entity.CargaCaja;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CargaCajaRepository extends JpaRepository<CargaCaja, Long> {
    Optional<CargaCaja> findTopByEstadoOrderByFechaCargaDesc(String estado);
    List<CargaCaja> findAllByOrderByFechaCargaDesc();
}
