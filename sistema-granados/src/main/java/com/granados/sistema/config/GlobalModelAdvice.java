package com.granados.sistema.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Expone la URI actual a todas las vistas (Thymeleaf 3.1 ya no permite
 * acceder al request directamente) para marcar el menu activo.
 */
@ControllerAdvice
public class GlobalModelAdvice {

    @ModelAttribute("uri")
    public String uriActual(HttpServletRequest request) {
        return request.getRequestURI();
    }
}
