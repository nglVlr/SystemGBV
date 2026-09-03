package com.granados.sistema.dafim.paquetes.repository;

import com.granados.sistema.dafim.paquetes.entity.LineaPaquete;
import com.granados.sistema.dafim.paquetes.entity.PaqueteFacturas;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LineaPaqueteRepository extends JpaRepository<LineaPaquete, Long> {

    List<LineaPaquete> findByPaqueteIdOrderByOrdenAsc(Long paqueteId);

    List<LineaPaquete> findByPaqueteInOrderByPaqueteNumeroAscOrdenAsc(
            List<PaqueteFacturas> paquetes);

    void deleteByPaqueteIn(List<PaqueteFacturas> paquetes);

    /** La linea (y por tanto el paquete) donde quedo asignada una factura, si alguna. */
    java.util.Optional<LineaPaquete> findByFactura_Id(Long facturaId);
}
