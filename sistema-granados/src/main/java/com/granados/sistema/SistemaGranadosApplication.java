package com.granados.sistema;

import com.granados.sistema.config.StorageProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Sistema integral de la Municipalidad de Granados, Baja Verapaz.
 *
 * Modulos:
 *  - DAFIM  : compras directas (informe mensual Art. 10 Num. 11 LAIP)
 *  - RRHH   : recursos humanos (en construccion)
 *  - ADMIN  : gestion de usuarios (solo SUPERADMIN)
 */
@SpringBootApplication
@EnableConfigurationProperties(StorageProperties.class)
public class SistemaGranadosApplication {

    public static void main(String[] args) {
        SpringApplication.run(SistemaGranadosApplication.class, args);
    }

}

