package com.granados.sistema.dafim.compras.repository;

import com.granados.sistema.dafim.compras.entity.HistorialCompra;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HistorialCompraRepository extends JpaRepository<HistorialCompra, Long> {
    List<HistorialCompra> findByAnioAndMes(Integer anio, Integer mes);
    long deleteByAnioAndMes(Integer anio, Integer mes);
    List<HistorialCompra> findByNitOrderByAnioDescMesDesc(String nit);
}
