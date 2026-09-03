package com.granados.sistema.rrhh.repository;

import com.granados.sistema.rrhh.entity.Permiso;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PermisoRepository extends JpaRepository<Permiso, Long> {

    List<Permiso> findAllByOrderBySolicitadoEnDesc();

    List<Permiso> findByEstadoOrderBySolicitadoEnDesc(String estado);

    long countByEstado(String estado);

    List<Permiso> findByEmpleadoIdAndEstadoAndFechaInicioBetween(
            Long empleadoId, String estado, LocalDate desde, LocalDate hasta);

    long countByEstadoAndFechaInicioBetween(
            String estado, LocalDate desde, LocalDate hasta);

    @Query("""
            SELECT p FROM Permiso p JOIN FETCH p.empleado e
            WHERE (:estado IS NULL OR p.estado = :estado)
              AND (:empleadoId IS NULL OR e.id = :empleadoId)
              AND (:tipo IS NULL OR p.tipo = :tipo)
              AND (:desde IS NULL OR p.fechaInicio >= :desde)
              AND (:hasta IS NULL OR p.fechaInicio <= :hasta)
            ORDER BY p.solicitadoEn DESC
            """)
    List<Permiso> filtrar(@Param("estado") String estado,
                          @Param("empleadoId") Long empleadoId,
                          @Param("tipo") String tipo,
                          @Param("desde") LocalDate desde,
                          @Param("hasta") LocalDate hasta);

    @Query("""
            SELECT p FROM Permiso p
            WHERE p.empleado.id = :empleadoId
              AND p.estado IN ('SOLICITADO', 'APROBADO')
              AND p.fechaInicio <= :fin
              AND p.fechaFin >= :ini
            """)
    List<Permiso> solapes(@Param("empleadoId") Long empleadoId,
                          @Param("ini") LocalDate ini,
                          @Param("fin") LocalDate fin);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Permiso p WHERE p.id = :id")
    Optional<Permiso> lockById(@Param("id") Long id);
}
