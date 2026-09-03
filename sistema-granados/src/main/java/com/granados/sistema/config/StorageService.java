package com.granados.sistema.config;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Guarda los archivos subidos y los Excel generados en disco, con nombres
 * con marca de tiempo para no pisar los de meses anteriores.
 */
@Service
public class StorageService {

    private static final DateTimeFormatter SELLO =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final StorageProperties props;

    public StorageService(StorageProperties props) {
        this.props = props;
    }

    @PostConstruct
    void crearCarpetas() throws IOException {
        Files.createDirectories(dirUploads());
        Files.createDirectories(dirGenerados());
    }

    public Path dirUploads() {
        return Paths.get(props.getUploads()).toAbsolutePath().normalize();
    }

    public Path dirGenerados() {
        return Paths.get(props.getGenerados()).toAbsolutePath().normalize();
    }

    /** Guarda un archivo subido y retorna la ruta final. */
    public Path guardarSubida(MultipartFile archivo, String prefijo) throws IOException {
        String original = archivo.getOriginalFilename() == null
                ? "archivo" : Paths.get(archivo.getOriginalFilename())
                        .getFileName().toString();
        String nombre = prefijo + "_" + LocalDateTime.now().format(SELLO)
                + "_" + original.replaceAll("[^A-Za-z0-9._-]", "_");
        Path destino = dirUploads().resolve(nombre);
        Files.copy(archivo.getInputStream(), destino, StandardCopyOption.REPLACE_EXISTING);
        return destino;
    }

    /** Ruta para un Excel generado (el nombre ya viene armado). */
    public Path rutaGenerado(String nombreArchivo) {
        return dirGenerados().resolve(nombreArchivo);
    }
}
