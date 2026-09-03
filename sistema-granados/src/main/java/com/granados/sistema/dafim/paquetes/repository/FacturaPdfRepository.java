package com.granados.sistema.dafim.paquetes.repository;

import com.granados.sistema.dafim.paquetes.entity.FacturaPdf;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FacturaPdfRepository extends JpaRepository<FacturaPdf, Long> {

    Optional<FacturaPdf> findFirstByFacturaId(Long facturaId);

    void deleteByFacturaIdIn(java.util.List<Long> facturaIds);
}
