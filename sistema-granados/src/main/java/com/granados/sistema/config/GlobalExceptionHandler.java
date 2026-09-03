package com.granados.sistema.config;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Manejo global de errores frecuentes con mensajes claros para el usuario.
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log =
            LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Archivo mas grande que el limite configurado (100 MB por archivo). */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String archivoMuyGrande(MaxUploadSizeExceededException ex,
                                   HttpServletRequest req,
                                   RedirectAttributes flash) {
        String uri = req == null ? "?" : req.getRequestURI();
        log.warn("Archivo demasiado grande en {}: {}", uri, ex.getMessage());
        flash.addFlashAttribute("error",
                "El archivo es demasiado grande. El limite es 100 MB por archivo.");
        return RedirectInterno.desde(req);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public String faltaDato(MissingServletRequestParameterException ex,
                            HttpServletRequest req,
                            RedirectAttributes flash) {
        String uri = req == null ? "?" : req.getRequestURI();
        log.warn("Falta el dato {} en {}", ex.getParameterName(), uri);
        flash.addFlashAttribute("error",
                "Falta un dato obligatorio. Revisa el formulario e intentalo de nuevo.");
        return RedirectInterno.desde(req);
    }
}
