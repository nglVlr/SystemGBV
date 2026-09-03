package com.granados.sistema.rrhh.repository;

import com.granados.sistema.rrhh.entity.PermisoAdjunto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface PermisoAdjuntoRepository extends JpaRepository<PermisoAdjunto, Long> {

    Optional<PermisoAdjunto> findFirstByPermisoId(Long permisoId);

    /** Ids de permisos que tienen escaneado, para marcar la bandeja. */
    @Query("select a.permisoId from PermisoAdjunto a")
    List<Long> idsDePermisosConAdjunto();
}
