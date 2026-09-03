package com.granados.sistema.dafim.presupuesto.repository;

import com.granados.sistema.dafim.presupuesto.entity.LineaPresupuesto;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LineaPresupuestoRepository extends JpaRepository<LineaPresupuesto, Long> {
    List<LineaPresupuesto> findByCargaId(Long cargaId);
    List<LineaPresupuesto> findByCargaIdAndRenglon(Long cargaId, String renglon);
    long deleteByCargaId(Long cargaId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT l FROM LineaPresupuesto l WHERE l.id = :id")
    Optional<LineaPresupuesto> lockById(@Param("id") Long id);
}
