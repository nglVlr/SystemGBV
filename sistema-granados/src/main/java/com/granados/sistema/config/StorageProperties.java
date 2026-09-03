package com.granados.sistema.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Carpetas de trabajo del sistema (configurables en application.properties):
 *   app.storage.uploads   -> archivos que sube el usuario cada mes
 *   app.storage.generados -> Excel generados por el sistema
 */
@ConfigurationProperties(prefix = "app.storage")
public class StorageProperties {

    private String uploads = "./storage/uploads";
    private String generados = "./storage/generados";

    public String getUploads() { return uploads; }
    public void setUploads(String uploads) { this.uploads = uploads; }
    public String getGenerados() { return generados; }
    public void setGenerados(String generados) { this.generados = generados; }
}
