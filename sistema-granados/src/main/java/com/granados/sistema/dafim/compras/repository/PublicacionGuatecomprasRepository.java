package com.granados.sistema.dafim.compras.repository;

import com.granados.sistema.dafim.compras.entity.PublicacionGuatecompras;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PublicacionGuatecomprasRepository
        extends JpaRepository<PublicacionGuatecompras, Long> {
    Optional<PublicacionGuatecompras> findByNpg(String npg);
    long countByNpgIn(java.util.Collection<String> npgs);
}
