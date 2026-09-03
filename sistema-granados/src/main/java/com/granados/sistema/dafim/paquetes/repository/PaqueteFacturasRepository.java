package com.granados.sistema.dafim.paquetes.repository;

import com.granados.sistema.dafim.paquetes.entity.PaqueteFacturas;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaqueteFacturasRepository extends JpaRepository<PaqueteFacturas, Long> {

    List<PaqueteFacturas> findByAnioAndMesOrderByNumeroAsc(int anio, int mes);

    List<PaqueteFacturas> findAllByOrderByAnioDescMesDesc();

    boolean existsByAnioAndMes(int anio, int mes);

    void deleteByAnioAndMes(int anio, int mes);
}
