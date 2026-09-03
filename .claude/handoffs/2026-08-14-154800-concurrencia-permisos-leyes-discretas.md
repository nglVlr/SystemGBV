# Handoff: Concurrencia LAN + permisos RRHH + leyes discretas

## Session Metadata
- Created: 2026-08-14 15:48:00
- Project: C:\Users\ngl\Documents\PROYECTOS\sistema-granados v2\sistema-granados (codigo en `sistema-granados/`)
- Branch: [no es repo git]
- Session duration: optimizacion + RRHH permisos + leyes a la derecha + tests + este handoff

### Recent Commits (for context)
  - No hay git. No se hizo commit (pedido explicito).

## Handoff Chain

- **Continues from**: [2026-08-14-153000-responsive-normativa-dafim-rrhh.md](2026-08-14-153000-responsive-normativa-dafim-rrhh.md)
- **Supersedes (UI normativa)**: el grupo grande de Normativa en el menu izquierdo y el item 06 del dashboard. Las rutas `/normativa/**` siguen vivas; ahora se entra por el texto chico a la derecha de la barra.
- **Does not supersede**: responsive, parsers PDF/Excel como fuente (no API SICOIN), mesa de mando, `ddl-auto=update`.

## Current State Summary

Tres entregables pedidos, en este orden, implementados y con `.\mvnw.cmd test` en verde:

1. **Varias PCs a la vez, mas rapido** — Tomcat 50 hilos, Hikari 20, gzip, cache de estaticos, Thymeleaf cache, sesiones concurrentes ilimitadas, candados JVM por trabajo pesado (`ExclusiveJobs`), parseo de PDFs de presupuesto/paquetes fuera de TX, locks pesimistas al apartar presupuesto y al registrar/resolver permisos. `ddl-auto` sigue en `update`.
2. **Permisos RRHH** — bandeja/formulario mas claros, estados PENDIENTE/APROBADO/RECHAZADO, validaciones (activo, tipo, solape, horas, adjunto 8 MB, rechazo con motivo, constancia solo aprobado). HTML de listado ya no cierra `#pagina` antes de la tabla.
3. **Leyes casi invisibles** — menú y dashboard sin Normativa; links 10.5px muted a la derecha de la barra (en movil un solo “Leyes”).

## Codebase Understanding

### Architecture Overview

- Un solo proceso Spring Boot 3.2.5 en puerto **8085** (`server.address=0.0.0.0`) + PostgreSQL Docker. Varias PCs de la LAN = varias sesiones HTTP contra ese proceso. No hay Redis ni cluster (no hace falta).
- Trabajos que no deben pisarse (mismo mes de compras, importar egresos, importar caja, paquetes del mes) usan `ExclusiveJobs`: si alguien ya lo esta haciendo, el segundo recibe error inmediato (“Otra persona esta haciendo este mismo trabajo…”), no se encola.
- Escrituras de presupuesto/apartados/permisos usan transaccion + `PESSIMISTIC_WRITE` en la fila. El parseo de PDF/Excel es CPU y no debe ocupar conexion Hikari.
- Contrato de datos: PDF/Excel subido es la fuente. No hay cliente API SICOIN.

### Critical Files

| File | Purpose | Relevance |
|------|---------|-----------|
| `application.properties` | Tomcat, Hikari, gzip, thymeleaf.cache, logs | Concurrencia y velocidad |
| `config/ExclusiveJobs.java` | Candado por clave, sin espera | Evita pisarse en el mismo trabajo |
| `config/WebConfig.java` | Cache-Control de /vendor /css /js /img | Estaticos cacheables en LAN |
| `config/SecurityConfig.java` | maximumSessions(-1) + SessionEventPublisher | Mismo usuario en varias PCs |
| `dafim/compras/web/ComprasDirectasController.java` | hold `compras-mes-{anio}-{mes}` | Procesar/confirmar sin carrera |
| `dafim/presupuesto/service/PresupuestoService.java` | parse fuera TX; lock egresos/caja; lockById apartados | Import + apartados concurrentes |
| `dafim/paquetes/service/PaquetesService.java` | parse fuera; persistir bajo lock del mes | Igual |
| `rrhh/service/RrhhService.java` | lock empleado/permiso, solapes, resolver | Permisos sin carrera |
| `templates/layout/base.html` | `.leyes-discreto` a la derecha | Leyes visibles si buscas |
| `templates/rrhh/permisos.html` | Bandeja | UX listado |
| `test/.../ExclusiveJobsTest.java` | Segunda clave igual falla | Concurrencia JVM |
| `test/.../RrhhServiceTest.java` | Baja, solape, rechazo, doble resolver | Permisos |

### Key Patterns Discovered

- `ddl-auto=update` — nunca `create`.
- Un servidor, N clientes: dimensionar Tomcat > Hikari (estaticos no usan BD). Hikari 20 cubre oficina municipal; no inflar a 100.
- `ExclusiveJobs.tryLock(0)`: fallar rapido. No usar wait infinito (deja a la secretaria mirando un spinner).
- `spring.thymeleaf.cache=true` y `spring.devtools.add-properties=false`: HTML/CSS de plantilla exige **reinicio** para verse. Ctrl+F5 no basta para Thymeleaf cacheado.
- Clases UI: no reescribir design system. `.leyes-discreto` es muted 10.5px.

## Work Completed

### Tasks Finished

- [x] Pool Tomcat/Hikari, gzip, batch Hibernate, logs SQL en WARN, sesion 8h
- [x] ExclusiveJobs en compras, presupuesto (egresos+caja), paquetes
- [x] Parseo PDF presupuesto/paquetes fuera de transaccion
- [x] Locks pesimistas: linea al apartar, apartado al cambiar estado, empleado al solicitar permiso, permiso al resolver
- [x] Indices Hibernate en lineas, permisos, empleados, apartados, caja
- [x] Cache estaticos (vendor/img 7d, css/js 1h must-revalidate)
- [x] Sesiones concurrentes ilimitadas (varias PCs, mismo login)
- [x] Permisos RRHH: formulario, bandeja, validaciones, bug `</div>` extra
- [x] Normativa fuera del menu y del dashboard; links chicos a la derecha
- [x] `.\mvnw.cmd test` BUILD SUCCESS (incluye ExclusiveJobs, RrhhService, NormativaView)

### Files Modified (principales)

| File | Changes |
|------|---------|
| `application.properties` | threads, hikari, gzip, thymeleaf.cache, batch |
| `config/ExclusiveJobs.java` | nuevo |
| `config/WebConfig.java` | cache estaticos |
| `config/SecurityConfig.java` | sessions + HttpSessionEventPublisher |
| `ComprasDirectasController.java` | hold al procesar/confirmar |
| `PresupuestoService.java` | ExclusiveJobs + TransactionTemplate + lockById |
| `PaquetesService.java` | igual |
| `LineaPresupuesto` / `Apartado` / `Permiso` / `Empleado` repos | indexes + lockById |
| `RrhhService.java` + `RrhhController.java` | validaciones y locks |
| `templates/rrhh/permisos.html` + `permiso-form.html` | UX |
| `templates/layout/base.html` + `app.css` | leyes-discreto |
| `templates/dashboard.html` | sin item 06 normativa |
| tests ExclusiveJobs, RrhhService, NormativaView | |

### Decisions Made

| Decision | Options Considered | Rationale |
|----------|-------------------|-----------|
| Un proceso + pool, no Redis | Redis/cluster vs pool/indices/locks | Oficina LAN, un servidor; cluster no encaja |
| tryLock(0) no cola | Esperar vs rechazar ya | El segundo usuario ve mensaje y reintenta |
| thymeleaf.cache=true | cache off (dev) vs on (LAN) | Pedido de velocidad; costo: reiniciar para HTML |
| Leyes en barra derecha, no pie | pie / columna / menu | “al lado derecho” y casi no se ve |
| No planilla/ISR | expandir RRHH vs quedarse en permisos | Pedido explicito |

## Pending Work

### Immediate Next Steps
- Reiniciar la app en 8085 y recargar con Ctrl+F5 (CSS cacheado 1h; plantillas cacheadas hasta reinicio).
- Probar 2–3 navegadores/PCs como se describe abajo.

### Blockers/Open Questions
- Ninguno para este pedido.

### Deferred Items
- Redis/sesiones distribuidas si algun dia hay dos JVMs.
- Planilla / ISR (fuera de alcance).
- Paginar tablas enormes de historial de compras (payloads; no se toco el Excel legal).

## Context for Resuming Agent

### Important Context

- Municipio: Granados, Baja Verapaz, **Guatemala**. LAIP = Decreto **57-2008**, Art. 10 numeral 11. No mezclar Decreto 534 (El Salvador) ni meter otras leyes en la ficha LAIP.
- PDF/Excel subido es la fuente de verdad. No inventar API SICOIN.
- `ddl-auto=update` nunca `create`.
- Password de Postgres esta en `application.properties`; no copiarla a handoffs ni commits.

### Assumptions Made

- Un solo jar/proceso en la PC que ya sirve la LAN. Varias personas = varios browsers, no varios deploys.
- ~5–15 usuarios simultaneos reales (DAFIM + RRHH + admin). Pool 20 es holgado.

### Potential Gotchas

- **Thymeleaf cache**: cambiar un `.html` no se ve hasta reiniciar Spring.
- **CSS/JS**: Cache-Control 1h; Ctrl+F5 o esperar. Vendor 7 dias.
- **ExclusiveJobs es por JVM**: protege este proceso, no dos instancias.
- **WebConfig** registra handlers de `/css/**` `/js/**`: si un estatico da 404, revisar que el path sea `classpath:/static/...`.
- `maximumSessions(-1)` permite el mismo usuario en N PCs a proposito (secretaria + encargado).

## Environment State

- Tests: `.\mvnw.cmd test` OK (2026-08-14 ~15:45, Java 17.0.12).
- App no se dejo corriendo desde esta sesion; hay que arrancarla para la prueba LAN.
- Puerto 8085, bind 0.0.0.0. Docker Postgres en localhost:5432.

## Como probar (para humanos y agentes)

### Varias PCs / navegadores (concurrencia)

1. Arrancar Postgres (`docker compose up -d` desde el proyecto) y la app (`.\mvnw.cmd spring-boot:run` o el jar) en la PC servidor.
2. En esa PC: `http://localhost:8085`. En otra PC de la LAN: `http://<IP-del-servidor>:8085`.
3. Login distinto o el mismo (admin DAFIM en una, RRHH en otra, superadmin en tercera). Deben convivir.
4. En dos PCs, entrar a Presupuesto y navegar: no debe “echar” al otro.
5. En dos PCs, **el mismo mes** de compras → procesar a la vez: el segundo debe ver el aviso de que otra persona ya lo esta haciendo. Meses distintos: ambos siguen.
6. Dos apartados sobre la **misma linea** a la vez: uno debe fallar o esperar el lock de fila; no duplicar saldo negativo.
7. Dos resoluciones del **mismo permiso** (aprobar vs rechazar): el segundo debe decir que ya esta resuelto.

### Permisos RRHH

1. Login ADMIN_RRHH → Permisos.
2. Sin personal activo: el form avisa y manda a Personal.
3. Registrar: fechas, horas ambas o ninguna, adjunto opcional < 8 MB.
4. Empleado de baja no debe aparecer / no debe guardar.
5. Segundo permiso que se solape (SOLICITADO o APROBADO) debe rechazarse.
6. Bandeja: PENDIENTE ambar; rechazar exige motivo; constancia solo si APROBADO.

### Leyes a la derecha

1. El menu izquierdo ya no tiene seccion Normativa. El inicio ya no tiene fila 06.
2. Arriba a la derecha, letra muy chica y apagada: LAIP · Cód. mun. · … Al pasar el mouse se lee un poco mejor.
3. En telefono: solo la palabra “Leyes”.
4. `/normativa/laip` sigue siendo solo Decreto 57-2008 (sin otras leyes mezcladas).

## Related Resources

- Handoff anterior: `.claude/handoffs/2026-08-14-153000-responsive-normativa-dafim-rrhh.md`
- Tests: `src/test/java/com/granados/sistema/config/ExclusiveJobsTest.java`, `rrhh/service/RrhhServiceTest.java`, `normativa/web/NormativaViewTest.java`
