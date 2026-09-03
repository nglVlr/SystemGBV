package com.granados.sistema.dafim.presupuesto.repository;

import com.granados.sistema.dafim.presupuesto.entity.Apartado;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ApartadoRepository extends JpaRepository<Apartado, Long> {
    List<Apartado> findByEstadoOrderByFechaDesc(String estado);
    List<Apartado> findAllByOrderByFechaDesc();
    List<Apartado> findByAnioAndEstadoOrderByFechaDesc(Integer anio, String estado);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Apartado a WHERE a.id = :id")
    Optional<Apartado> lockById(@Param("id") Long id);
}
