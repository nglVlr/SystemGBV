package com.granados.sistema.rrhh.repository;

import com.granados.sistema.rrhh.entity.Empleado;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EmpleadoRepository extends JpaRepository<Empleado, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM Empleado e WHERE e.id = :id")
    Optional<Empleado> lockById(@Param("id") Long id);

    List<Empleado> findByActivoTrueOrderByNombreAsc();

    List<Empleado> findAllByOrderByActivoDescNombreAsc();

    long countByActivoTrue();
}
