package com.granados.sistema.config;

import jakarta.servlet.http.HttpServletRequest;

import java.net.URI;

/**
 * Convierte un Referer en una redireccion interna. Evita open-redirect
 * si el encabezado apunta a otro host o a un esquema no http(s).
 */
final class RedirectInterno {

    private RedirectInterno() {
    }

    static String desde(HttpServletRequest req) {
        String host = req == null ? null : req.getServerName();
        String referer = req == null ? null : req.getHeader("Referer");
        return "redirect:" + ruta(referer, host);
    }

    static String ruta(String referer, String hostServidor) {
        if (referer == null || referer.isBlank()) {
            return "/";
        }
        String crudo = referer.trim();
        if (crudo.startsWith("//") || crudo.contains("\\")) {
            return "/";
        }
        try {
            URI uri = URI.create(crudo);
            if (uri.isOpaque()) {
                return "/";
            }
            String path = uri.getPath();
            if (path == null || path.isEmpty()) {
                path = "/";
            }
            if (!path.startsWith("/") || path.startsWith("//")) {
                return "/";
            }
            String host = uri.getHost();
            if (host != null) {
                if (hostServidor == null || !host.equalsIgnoreCase(hostServidor)) {
                    return "/";
                }
            } else if (uri.getScheme() != null) {
                return "/";
            }
            String query = uri.getRawQuery();
            if (query != null && !query.isEmpty()) {
                return path + "?" + query;
            }
            return path;
        } catch (IllegalArgumentException e) {
            return "/";
        }
    }
}
