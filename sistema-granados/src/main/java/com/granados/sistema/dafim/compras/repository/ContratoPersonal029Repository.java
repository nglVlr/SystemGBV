package com.granados.sistema.dafim.compras.repository;

import com.granados.sistema.dafim.compras.entity.ContratoPersonal029;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ContratoPersonal029Repository
        extends JpaRepository<ContratoPersonal029, Long> {
    Optional<ContratoPersonal029> findByNit(String nit);
}
