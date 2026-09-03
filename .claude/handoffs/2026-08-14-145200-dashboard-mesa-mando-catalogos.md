# Handoff: Dashboard mesa de mando + logo 3D + catálogos

## Session Metadata
- Created: 2026-08-14 14:52:00
- Project: C:\Users\ngl\Documents\PROYECTOS\sistema-granados v2\sistema-granados (el código vive en la subcarpeta `sistema-granados/`)
- Branch: [no es repo git — no hay control de versiones]
- Session duration: continuación de la misma conversación (después del handoff de las 12:00)

### Recent Commits (for context)
  - No hay git.

## Handoff Chain

- **Continues from**: [2026-08-14-120000-carga-mes-compras-ui.md](2026-08-14-120000-carga-mes-compras-ui.md)
- **Also related**: [2026-08-14-094000-pestanias-y-apartados.md](2026-08-14-094000-pestanias-y-apartados.md) (presupuesto pestañas/apartados)
- **Also related**: [2026-08-11-160358-rediseno-front-modulo-presupuesto.md](2026-08-11-160358-rediseno-front-modulo-presupuesto.md) (tema "Noche en Verapaz", `fondo3d.js`)
- **Does not supersede**: LAN temporal, NPG 100 MB, parsers de compras. Esos siguen vigentes en el handoff de las 12:00.

## Current State Summary

El usuario pidió tres cosas y se implementaron:

1. **Bugs visuales en catálogos** — fuentes (formulario de nombre que rompía la fila), paquetes (triángulo de `<details>`, asignador con `min-width:320px` que abría scroll horizontal, nombres largos), renglones/personal/usuarios (overflow y tablas altas sin scroll).
2. **Dashboard distinto, no la grilla de siempre** — se eliminó el hero con escudo grande + 4 KPI + tarjetas de acción. Ahora es una **mesa de mando**: saludo tipográfico sobre el paisaje 3D, cinta de cifras (no cards) y módulos en lista numerada (01…05).
3. **Mismo fondo 3D, con el logo municipal sin molestar** — `#fondo-escudo` (`logo.png`, sello circular amarillo con toro) en el cielo, opacidad ~0.14, `mix-blend-mode: screen`, parallax suave con la cámara de `fondo3d.js`. En login se recorre a la columna izquierda.

Hay que **reiniciar la app y recargar con Ctrl+F5** para ver estáticos. El firewall LAN del handoff anterior sigue pendiente si no se corrió el .bat como admin.

## Codebase Understanding

### Architecture Overview

- Spring Boot 3.2.5 / Java 17 / Thymeleaf / PostgreSQL 17 Docker. Puerto **8085**, bind `0.0.0.0`. Código en `sistema-granados/`.
- Layout global: `templates/layout/base.html` → canvas `#fondo3d` + img `#fondo-escudo` + `.veladura` + `.app-shell` (z-index 0 / 1 / 1 / 2). Login replica el mismo trío de fondo.
- Dashboard: `GET /` → `DashboardController.inicio` → `dashboard.html`. Datos opcionales (`pctEjecucion`, `vigentePresupuesto`, `ultimoMesCompras`, `totalPagosHistorial`) solo se pintan si existen.
- Tema visual: oro/jade sobre noche. **No** volver a meter `.tarjeta-stat` + `.tarjeta-accion` en el inicio: el usuario lo rechazó explícitamente (“no como siempre”).
- Catálogos = listas maestras: Fuentes, Renglones, Paquetes del mes, Personal RRHH, Usuarios.

### Critical Files

| File | Purpose | Relevance |
|------|---------|-----------|
| `templates/dashboard.html` | Mesa de mando | Inicio nuevo |
| `templates/layout/base.html` | Shell + `#fondo-escudo` | Logo en todas las pantallas autenticadas |
| `templates/login.html` | Login + mismo fondo | Sello a la izquierda (`body:has(.pagina-login)`) |
| `static/css/app.css` | Mesa, sello, catálogos | `.mesa-mando`, `#fondo-escudo`, `.catalogo-nombre`, `.asignador` |
| `static/js/fondo3d.js` | WebGL colinas + parallax del sello | Mueve `#fondo-escudo` con `camara.x/y` |
| `static/js/app.js` | Saludo por hora + contadores de cinta | `#saludoMomento`, `.cinta-dato .valor` |
| `static/img/logo.png` | Sello real (toro, piña, fondo amarillo) | Usar este, no solo `escudo.svg` |
| `templates/dafim/presupuesto/fuentes.html` | Catálogo de fuentes | Form `.catalogo-nombre` |
| `templates/dafim/paquetes/mes.html` | Catálogo de paquetes | `<details>`, asignador, overflow |
| `templates/dafim/presupuesto/renglones.html` | Catálogo renglones | `.scroll-alto` |
| `templates/rrhh/empleados.html` | Catálogo personal | Truncado + scroll |
| `templates/admin/usuarios.html` | Catálogo usuarios | Truncado + scroll |

### Key Patterns Discovered

- `.tarjeta` ya anima con `aparecer`. No mezclar `.revelar` en la misma tarjeta (pelea `opacity`).
- `logo.png` es un sello **amarillo circular** (toro blanco, piña, caña). `mix-blend-mode: screen` lo vuelve luna dorada sobre el cielo oscuro. Un PNG con fondo blanco + `multiply` también funcionaría; screen quedó mejor.
- `escudo.svg` es un dibujo lineal (ceiba/colinas) para UI; el de la municipalidad de verdad es `logo.png`.
- Sidebar y favicon siguen usando `/img/logo.png` con `border-radius:50%` y fondo blanco — eso está bien en el menú; **no** repetir ese escudo grande en el dashboard.
- Hooks JS: `data-procesando` (velo pantalla), `data-nombre`, `data-confirmar` (se registra primero; el velo respeta `defaultPrevented`).
- Clases de overflow: `.scroll-alto`, `.celda-desc`, `.celda-proveedor`, `.celda-archivo`.

## Work Completed

### Tasks Finished

- [x] Dashboard rediseñado como mesa de mando (saludo + cinta + lista 01–05)
- [x] Logo municipal en el fondo 3D, tenue, con parallax
- [x] Login: mismo sello, posición distinta para no tapar el formulario
- [x] Catálogo Fuentes: input de nombre ya no rompe la fila
- [x] Catálogo Paquetes: marker de details, asignador sin min-width 320px, textos largos, scroll
- [x] Renglones, empleados, usuarios: scroll interno + ellipsis en nombres
- [x] Buscador anidado en tarjeta (dónde pagar) ya no duplica el marco (`.tarjeta > .buscador-caja`)

### Files Modified

| File | Changes | Rationale |
|------|---------|-----------|
| `dashboard.html` | Reescritura: `.mesa-mando`, sin hero ni cards | Pedido de “uno diferente” |
| `base.html` | `<img id="fondo-escudo">` entre canvas y veladura | Sello global |
| `login.html` | Mismo `#fondo-escudo` | Continuidad del paisaje |
| `app.css` | Mesa de mando; sello cielo; `.catalogo-nombre`; `.asignador`; marker details; print hide escudo | Visual + catálogos |
| `fondo3d.js` | Parallax de `#fondo-escudo` con la cámara | Que el sello “viva” en el 3D |
| `app.js` | Buenos días/tardes/noches; contadores en `.cinta-dato` | Saludo y cifras |
| `fuentes.html` | `.catalogo-nombre` + `.scroll-alto` | Bug de formulario en tabla |
| `renglones.html` | `.scroll-alto` | Tabla larga |
| `paquetes/mes.html` | asignador CSS, `form-select`, ellipsis, scroll | Scroll horizontal y overflow |
| `empleados.html` | ellipsis + scroll + buscador flex | Nombres largos tipo MAYEN,CARDONA,,… |
| `usuarios.html` | ellipsis + scroll | Igual |

### Decisions Made

| Decision | Options Considered | Rationale |
|----------|-------------------|-----------|
| Lista numerada en vez de tarjetas de acción | Cards 3D, tiles, bento | El usuario pidió explícitamente no “como siempre” |
| Cinta de cifras, no `.tarjeta-stat` | KPI cards vs hairline | Deja ver el paisaje 3D |
| Sello CSS overlay + parallax JS, no textura WebGL | Quad texturizado vs `<img>` | Más fiable; el PNG circular + `screen` ya se siente luna |
| Opacidad 0.14 y tamaño `min(22vw, 220px)` arriba-derecha | Grande al centro vs esquina | “Que no sea molesto”; el dashboard es max-width 760px y el sello queda en el cielo vacío |
| En login, `left: 22%` vía `body:has(.pagina-login)` | Misma posición 78% | 78% caía encima del formulario |
| Botón guardar de fuentes solo icono | Texto “Guardar” | El texto + input 300px reventaba la columna |

## Pending Work

### Immediate Next Steps

1. Reiniciar IntelliJ / `iniciar-lan.bat` y Ctrl+F5 en `/`.
2. Si el sello se siente fuerte o flojo, ajustar `#fondo-escudo` opacity (hoy `.14`) y `width`.
3. Firewall LAN: si otras PCs aún no entran, `servidor/abrir-firewall.bat` como administrador (handoff 12:00).
4. Probar catálogos reales: Fuentes (guardar un nombre), Paquetes del mes (abrir un paquete e intentar asignar), Personal.

### Blockers/Open Questions

- [ ] Firewall 8085: puede seguir sin regla si no se elevó UAC.
- [ ] IP LAN `192.168.101.45` (Ethernet 2, DHCP). Si cambió, `ipconfig`.
- [ ] Python no está en PATH: el scaffold/validador de handoffs no corre en esta PC. Los md se escriben a mano en `.claude/handoffs/`.
- [ ] El usuario no confirmó visualmente el dashboard nuevo en esta sesión (se pidió guardar contexto al rato).

### Deferred Items

- Servicio Windows NSSM 24/7 (setup era temporal hasta vender/subir a web).
- `servidor/LEEME_SERVIDOR.md` sigue hablando de MySQL.
- README aún menciona límite 20 MB en “problemas frecuentes”.
- Tests MockMvc de guards TXT/PDF vacíos.
- ZIP en la carga mensual (sigue siendo 3 archivos sueltos).

## Context for Resuming Agent

### Important Context

- Código en `sistema-granados/`, no en la raíz del workspace.
- Hibernate `ddl-auto=update` — nunca `create`.
- Parser SICOIN y `MotorComprasService` están calibrados; no “limpiar” regex sin PDF real (ver handoff 12:00).
- **No restaurar** el dashboard de tarjetas `.tarjeta-accion` / `.hero-muni` salvo que el usuario lo pida. `.hero-muni` se eliminó del CSS y de `dashboard.html`.
- Credenciales iniciales: las del README (`superadmin`, `admin_dafim`, `admin_rrhh`). No copiar passwords ni la de Postgres aquí.
- `data-procesando` pinta `#veloProcesando` a pantalla completa.
- Cascarón presupuesto (`_nav :: cascaron`) y apartados siguen como en el handoff de las 09:40.

### Assumptions Made

- “Catálogos” = Fuentes, Renglones, Paquetes, Personal, Usuarios (listas maestras con tablas).
- El sello debe sentirse parte del cielo, no un watermark sobre el texto.
- Otras PCs en `192.168.101.0/24` si se usa el modo LAN.

### Potential Gotchas

- `target/classes/` no se edita a mano; DevTools/reinicio copia resources. Un grep en `target/` puede mostrar el dashboard viejo hasta recompilar.
- `#fondo3d.listo ~ #fondo-escudo` solo aplica si el canvas existe. Si WebGL falla, el canvas se borra y entra `body.sin-webgl #fondo-escudo` (animación CSS `escudoCielo`).
- Parallax JS pisa `transform` del sello; por eso la animación CSS de flotar **solo** corre en `sin-webgl`.
- `mix-blend-mode: screen` + sello amarillo = brillo. Si alguien pone `multiply`, el círculo amarillo se apaga sobre noche.
- Asignador de paquetes: `max-width: 280px`. No volver a poner `style="min-width:320px"`.
- Form de nombre de fuente: clase `.catalogo-nombre`, no `.acciones-fila`.
- `:has(.pagina-login)` mueve el sello; browsers viejos lo ignorarían (Edge/Chrome actuales OK).

## Environment State

### Tools/Services Used

- Docker: `sistema-granados-db` (Postgres 17, `127.0.0.1:5432`)
- JDK 17
- Maven wrapper `mvnw.cmd`

### Active Processes

- Postgres Docker: probablemente up
- App Spring: puede estar con estáticos viejos si no se reinició después de este bloque

### Environment Variables

- Ninguna extra. JDBC en `application.properties` / `docker-compose.yml` (no copiar secretos).

## Related Resources

- Handoff compras/LAN/parsers: `.claude/handoffs/2026-08-14-120000-carga-mes-compras-ui.md`
- Handoff presupuesto: `.claude/handoffs/2026-08-14-094000-pestanias-y-apartados.md`
- Handoff front original: `.claude/handoffs/2026-08-11-160358-rediseno-front-modulo-presupuesto.md`
- Logo: `sistema-granados/src/main/resources/static/img/logo.png`
- Fondo 3D: `sistema-granados/src/main/resources/static/js/fondo3d.js`
