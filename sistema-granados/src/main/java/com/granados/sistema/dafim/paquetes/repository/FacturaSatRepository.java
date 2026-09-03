package com.granados.sistema.dafim.paquetes.repository;

import com.granados.sistema.dafim.paquetes.entity.FacturaSat;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface FacturaSatRepository extends JpaRepository<FacturaSat, Long> {

    boolean existsByAutorizacion(String autorizacion);

    List<FacturaSat> findByAnioAndMesOrderByIdAsc(int anio, int mes);

    /** Todas las autorizaciones ya guardadas (para no reusar facturas). */
    @Query("select f.autorizacion from FacturaSat f")
    List<String> todasLasAutorizaciones();

    /** Facturas del mes que ninguna linea tiene asignada (libres). */
    @Query("select f from FacturaSat f where f.anio = :anio and f.mes = :mes "
            + "and f.id not in (select l.factura.id from LineaPaquete l "
            + "where l.factura is not null)")
    List<FacturaSat> libresDelMes(int anio, int mes);

    long countByAnioAndMes(int anio, int mes);

    void deleteByAnioAndMes(int anio, int mes);

    /** Busqueda global por numero de DTE (contiene, sin importar mayusculas), mas recientes primero. */
    List<FacturaSat> findByNumeroDteContainingIgnoreCaseOrderByAnioDescMesDescIdDesc(String dte);

    @Query("select f from FacturaSat f where lower(f.numeroDte) like lower(concat('%', :q, '%')) "
            + "or lower(f.nitEmisor) like lower(concat('%', :q, '%')) "
            + "or lower(f.nombreEmisor) like lower(concat('%', :q, '%')) "
            + "or lower(f.descripcion) like lower(concat('%', :q, '%')) "
            + "or lower(f.autorizacion) like lower(concat('%', :q, '%')) "
            + "order by f.anio desc, f.mes desc, f.id desc")
    List<FacturaSat> buscarGeneral(@Param("q") String q, Pageable pageable);
}
