# Handoff: Responsive global + modulo normativa (LAIP y leyes)

## Session Metadata
- Created: 2026-08-14 15:30:00
- Project: C:\Users\ngl\Documents\PROYECTOS\sistema-granados v2\sistema-granados (el codigo vive en la subcarpeta `sistema-granados/`)
- Branch: [no es repo git — no hay control de versiones]
- Session duration: bloque responsive + leyes + ideas DAFIM/RRHH + este handoff

### Recent Commits (for context)
  - No hay git.

## Handoff Chain

- **Continues from**: [2026-08-14-145200-dashboard-mesa-mando-catalogos.md](2026-08-14-145200-dashboard-mesa-mando-catalogos.md)
- **Also related**: [2026-08-14-120000-carga-mes-compras-ui.md](2026-08-14-120000-carga-mes-compras-ui.md)
- **Also related**: [2026-08-14-094000-pestanias-y-apartados.md](2026-08-14-094000-pestanias-y-apartados.md)
- **Does not supersede**: dashboard mesa de mando, logo 3D, catalogos, LAN, parsers de compras. Siguen vigentes.

## Current State Summary

El usuario pidio tres entregables de producto y un handoff. Quedaron implementados:

1. **Vistas responsive** — el CSS institucional ya tenia hamburguesa a 992px y `.tabla-envoltura` con scroll. Se reforzo telefono (~375) y tablet (~768): columnas que apilan, tablas que scrollean en X, formularios a 16px (sin zoom iOS), nav tactil, modal de factura a pantalla completa, login apilado, titulo de barra con ellipsis, chip de usuario que esconde el nombre en 575px. No se reescribio el design system.
2. **Vistas de leyes** — no existia modulo. Se creo `/normativa` con secciones separadas. **LAIP solo en `/normativa/laip`** (Decreto 57-2008 de Guatemala, Art. 10 numeral 11). El resto: Codigo Municipal, Contrataciones, LOP, Codigo de Trabajo, Probidad. Letra chica (`.texto-legal` 12px).
3. **Este handoff** incluye ideas concretas para expandir DAFIM y RRHH y una ruta pruebas → produccion.

Hay que **reiniciar la app y recargar con Ctrl+F5** para ver CSS/JS/plantillas. Python no esta en PATH: el scaffold de handoff se escribio a mano.

## Codebase Understanding

### Architecture Overview

- Spring Boot 3.2.5 / Java 17 / Thymeleaf / PostgreSQL 17 Docker. Puerto **8085**, bind `0.0.0.0`. Codigo en `sistema-granados/`.
- Front: CSS propio `static/css/app.css` (tema cine/noche oro-jade) + Bootstrap vendor solo para grid puntual y form-control. Layout unico: `templates/layout/base.html`.
- Breakpoint de menu: **992px**. Debajo: drawer + `.menu-velo`. Encima: sidebar, opcionalmente `body.menu-plegado`.
- Seguridad: `/admin/**` SUPERADMIN, `/dafim/**` ADMIN_DAFIM|SUPERADMIN, `/rrhh/**` ADMIN_RRHH|SUPERADMIN, `/normativa/**` cae en `anyRequest().authenticated()` (cualquier rol logueado).
- Municipio: **Granados, Baja Verapaz, Guatemala**. La LAIP aplicable es Decreto **57-2008**, no la de El Salvador (Decreto 534). El Excel de compras ya citaba Art. 10 Num. 11; coincide con Guatemala.

### Critical Files

| File | Purpose | Relevance |
|------|---------|-----------|
| `templates/layout/base.html` | Shell, menu, viewport, skip-link, velo | Nav normativa + a11y movil |
| `static/css/app.css` | Design system + media queries | Responsive extra y `.texto-legal` |
| `static/js/app.js` | Menu, aria-expanded, Escape, resize | Cierra drawer en movil |
| `normativa/web/NormativaController.java` | GET `/normativa/**` | Rutas de leyes |
| `templates/normativa/*.html` | Indice + 6 leyes | LAIP aislada del resto |
| `templates/dashboard.html` | Mesa de mando | Item 06 Normativa |
| `test/.../NormativaViewTest.java` | MockMvc | LAIP no mezcla otros decretos |

### Key Patterns Discovered

- Contrato de clases: `.folio`, `.tarjeta`, `.tabla-envoltura`, `.rejilla-N`, `.menu-grupo` / `.menu-sub`, `.pestanias-mod`. No inventar otro sistema.
- `.tabla-envoltura` ya envolvía casi todas las tablas; el overflow del `body` recorta lo que no va dentro. No quitar esos wrappers.
- `.hero-muni` / grilla de `.tarjeta-accion` en el **inicio** sigue prohibido (handoff 14:52). En DAFIM/RRHH las tarjetas de operacion SI existen y se dejaron.
- Tipografia legal: `.texto-legal` 12px / line-height 1.58 / color `--tinta-2`. No pegar el Diario Oficial entero.
- `data-procesando`, `data-confirmar`, `data-nombre` siguen siendo los hooks JS.
- Hibernate `ddl-auto=update` — nunca `create`.

## Work Completed

### Tasks Finished

- [x] Responsive global (layout, nav, tablas, forms, modales, dashboard, login, constancia)
- [x] Modulo normativa con LAIP aislada y 5 leyes mas
- [x] Enlaces discretos desde compras / presupuesto / RRHH hacia su ley (no mezclar textos)
- [x] Test MockMvc `NormativaViewTest` (3 tests, BUILD SUCCESS via `mvnw.cmd`)
- [x] Handoff + ideas DAFIM/RRHH + ruta a produccion

### Files Modified

| File | Changes | Rationale |
|------|---------|-----------|
| `static/css/app.css` | Media 992/575, velo, legal, modal, login corto | Adaptar UI sin reescribir tema |
| `static/js/app.js` | Cerrar menu movil, aria, Escape, resize | Drawer usable en telefono |
| `templates/layout/base.html` | Skip link, velo, grupo Normativa, chip-nombre | Nav leyes + a11y |
| `templates/dashboard.html` | Modulo 06 Normativa | Entrada visible para todos los roles |
| `templates/login.html` | Pie cita Decreto 57-2008 | No afirmar LAIP salvadoreña |
| `templates/dafim/compras/index.html` | Link a `/normativa/laip` | Puntero, no texto legal mezclado |
| `templates/dafim/presupuesto/index.html` | Link a LOP | Igual |
| `templates/rrhh/index.html` | Link a Codigo de Trabajo | Igual |
| `templates/dafim/paquetes/mes.html` | Modal sin estilos inline | CSS responsive del modal |
| `templates/dafim/paquetes/buscar.html` | Idem | Idem |
| `templates/rrhh/constancia.html` | `min-width: min(320px, 100%)` | Firma no desborda en 375px |
| `normativa/web/NormativaController.java` | **Nuevo** | Rutas |
| `templates/normativa/_nav.html` | **Nuevo** | Pestañas entre leyes |
| `templates/normativa/index.html` | **Nuevo** | Hub + aviso El Salvador vs GT |
| `templates/normativa/laip.html` | **Nuevo** | Solo Decreto 57-2008 |
| `templates/normativa/codigo-municipal.html` | **Nuevo** | Decreto 12-2002 |
| `templates/normativa/contrataciones.html` | **Nuevo** | Decreto 57-92 |
| `templates/normativa/presupuesto.html` | **Nuevo** | Decreto 101-97 |
| `templates/normativa/trabajo.html` | **Nuevo** | Decreto 1441 |
| `templates/normativa/probidad.html` | **Nuevo** | Decreto 89-2002 |
| `test/.../NormativaViewTest.java` | **Nuevo** | No mezclar LAIP con otras leyes |

### Decisions Made

| Decision | Options Considered | Rationale |
|----------|-------------------|-----------|
| CSS extra sobre el tema actual, no Tailwind/rewrite | Reescribir front vs parches en `app.css` | Pedido: no inventar design system paralelo |
| LAIP = Decreto 57-2008 GT, no Decreto 534 SV | Seguir el brief “El Salvador” vs el municipio real | Granados es Baja Verapaz; el Excel ya citaba Art. 10.11 GT. Aviso solo en el indice |
| `/normativa` autenticado para todos los roles | Meterlo en `/dafim` o publico | RRHH tambien debe leer Trabajo/Probidad; no es dato sensible |
| Resúmenes operativos, no texto autentico completo | Pegar la ley entera vs guia de uso | Letra chica + util para quien opera DAFIM/RRHH |
| Breakpoints 992 (ya existia) + 575 telefono + 2 col en tablet | Mobile-first rewrite | Menor riesgo; el menu ya pivotaba a 992 |

## Pending Work

### Immediate Next Steps

1. Reiniciar Spring (`.\mvnw.cmd spring-boot:run` o IntelliJ) y Ctrl+F5.
2. Verificar en el navegador: DevTools 375 / 768 / 1280. Login, inicio, compras, presupuesto (pestañas), paquetes+modal PDF, bitacora, RRHH permisos, usuarios, `/normativa/laip` vs otras leyes.
3. En telefono real de la LAN (si el firewall 8085 esta abierto): hamburguesa, scroll de tablas, no zoom al enfocar inputs.
4. Si el usuario insiste en mostrar la LAIP de El Salvador: **no mezclarla** en `/normativa/laip`; seria otra ruta, claramente marcada como derecho comparado y no vigente.

### Blockers/Open Questions

- [ ] App puede seguir sirviendo estaticos viejos hasta reinicio/devtools.
- [ ] Firewall 8085 / IP LAN: ver handoff 12:00. No se toco.
- [ ] Python no esta en PATH: `create_handoff.py` / `validate_handoff.py` no corrieron. Este md se redacto a mano siguiendo el template.
- [ ] Textos legales son resúmenes operativos, no dictamen juridico. Un abogado municipal deberia revisarlos antes de tratarlos como “oficiales”.
- [ ] No se hizo prueba visual en un telefono fisico en esta sesion (solo CSS + test MockMvc).

### Deferred Items

- Servicio Windows NSSM 24/7.
- `ddl-auto=validate` (o `none` + Flyway) para produccion. Hoy `update`.
- HTTPS / reverse proxy.
- Cambiar passwords por defecto (README) antes de exponer fuera de LAN.
- Tests MockMvc de guards TXT/PDF vacios (handoff 12:00).
- ZIP en carga mensual de compras.
- `servidor/LEEME_SERVIDOR.md` sigue hablando de MySQL.

## Context for Resuming Agent

### Important Context

- Codigo en `sistema-granados/`, no en la raiz del workspace.
- **No restaurar** el dashboard de tarjetas. Item 06 Normativa es una fila mas de `.modulo-fila`.
- LAIP vive **solo** en `templates/normativa/laip.html`. Otras leyes no deben copiar Art. 10 numeral 11 ni Decreto 57-2008.
- El indice SÍ menciona Decreto 534 para decir que **no aplica**. La pagina LAIP no lo menciona (el test lo exige: `not(containsString("Decreto 534"))`).
- Credenciales iniciales: las del README. No copiar passwords ni las de Postgres aqui.
- `target/classes/` no se edita; grep ahi puede mentir hasta recompilar.

### Assumptions Made

- “Leyes relacionadas con este proyecto” = marco guatemalteco que explica DAFIM, compras, SICOIN y RRHH.
- Responsive “todas las vistas” se logra mejor en CSS global + 2-3 plantillas puntuales (modal, constancia) que tocando 30 HTML.
- El usuario quiere expansion futura de DAFIM y RRHH, no implementar esas funciones ahora.

### Potential Gotchas

- `body { overflow-x: hidden }` recorta tablas si alguien mete un `<table class="tabla-datos">` sin `.tabla-envoltura`.
- En 992px `.rejilla-3/4` pasan a 2 columnas; en 575px a 1. Un `style="grid-template-columns:..."` inline ganaria al CSS.
- `#modal-factura` ahora se muestra/oculta con `style.display` desde JS; el CSS base es `display:none`. No volver a poner `display:none` inline que pelee.
- `html { overflow-x: clip }` es global. Si un dropdown se corta, revisar z-index (chip usuario ya es 120).
- WebMvcTest de normativa no carga `SecurityConfig` real (mismo patron que `PresupuestoFuentesViewTest`).
- Menu plegado en desktop oculta textos; en movil se fuerza ancho completo aunque exista `menu-plegado`.

## Environment State

### Tools/Services Used

- Docker: `sistema-granados-db` (Postgres 17, `127.0.0.1:5432`) — no se toco
- JDK 17
- Maven wrapper `mvnw.cmd` — `NormativaViewTest` exit 0

### Active Processes

- Postgres Docker: probablemente up
- App Spring: reiniciar para recoger resources

### Environment Variables

- Ninguna extra. JDBC en `application.properties` / `docker-compose.yml` (no copiar secretos).

## Ideas de expansion — DAFIM

Prioridad sugerida (P0 = proximo trimestre si hay usuarios reales).

### P0 — Cerrar el ciclo de compras y presupuesto
1. **Bitacora de quien cargo que** (usuario, timestamp, hash del PDF/Excel). Hoy hay bitacora de consulta; falta auditoria de escrituras para Contraloria.
2. **Alertas de ejecucion** al 80 / 90 / 100% del renglón+fuente, en el resumen y por correo interno.
3. **Vencimiento de contratos 029** (fecha fin, alerta 30 dias). El padron de contratos ya existe; falta el calendario.
4. **Export LAIP en PDF** ademas del Excel, con membrete, para colgar en el portal de transparencia.
5. **Aprobacion de apartados** (operador propone, jefe DAFIM confirma). Hoy el apartado es inmediato.

### P1 — Tesoreria y caja (el hueco natural)
6. **Boletin de caja como pantalla diaria** (saldos por cuenta, no solo al cargar PDF).
7. **Conciliacion bancaria** simple: extracto vs cheques IMPRESO del mes.
8. **Caja chica / vales** con tope y reposicion.
9. **Calendario de pagos** (“esta semana salen estos cheques”) cruzando apartados + disponibilidad.

### P2 — Mas funciones de una DAFIM municipal
10. **Inventario de bienes** (altas, custodios, bajas) ligado a renglones 3.x.
11. **Catalogo maestro de proveedores** con NIT SAT, inhabilitados, historial de montos.
12. **Multi-ejercicio** visible (2025 vs 2026) sin pisar la carga activa.
13. **Reportes para Concejo**: una pagina imprimible “ejecucion al corte”.
14. **Transferencias**: dejar de ser solo simulacion cuando exista el asiento SICOIN de vuelta (carga del PDF de modificaciones).

No hacer (todavia): contabilidad patrimonial completa, ni “API SICOIN” (no hay API publica estable). Seguir el modelo actual: **el PDF/Excel es la fuente**.

## Ideas de expansion — Recursos Humanos

Hoy: empleados, permisos, constancia de permiso. Eso es un MVP.

### P0 — Que RRHH deje de ser “en construccion”
1. **Saldo de vacaciones** por empleado y anio (el codigo de trabajo fija minimos; el sistema solo cuenta dias aprobados tipo VACACIONES).
2. **Constancia de trabajo** (labora desde / cargo / renglón), distinta de la de permiso.
3. **Expediente digital minimo**: DPI, nombramiento, contrato 029, alta IGSS (archivos, no ficha medica).
4. **Baja / inactivo** con fecha, para que no salga en el combo de permisos.

### P1 — Operacion de oficina
5. **Organigrama / dependencia** (el campo ya existe; falta arbol y jefe que aprueba el permiso).
6. **Doble visto bueno**: jefe de unidad → RRHH. Hoy aprueba solo RRHH.
7. **Reporte mensual de permisos** para DAFIM (029/011 ausentes vs contratos).
8. **Recordatorio IGSS** si el permiso IGSS no tiene escaneo.

### P2 — Nomina y mas (alto riesgo, hacerlo tarde)
9. **Planilla / nominas**: solo cuando haya tesoreria estable y un contador dueño del formato. No mezclar con compras.
10. **Asistencia / reloj**: hardware + politica; no empezar por ahi.
11. **Evaluacion de desempeno y capacitaciones**: utiles, no bloquean DAFIM.

Principio: RRHH no debe calcular ISR/IGSS patronal hasta que un contador firme la regla. El sistema registra hechos (quien, cuando, documento).

## Hoja de ruta pruebas → produccion

Ordenar. No saltar a “subirlo a internet”.

### 1. Pruebas tecnicas (esta semana)
- `.\mvnw.cmd test` completo (no solo NormativaViewTest).
- Checklist manual responsive: 375 / 768 / 1280 en Chrome + un telefono Android de la oficina.
- Recorrido con PDFs **reales** del ultimo mes: procesar compras, cargar SICOIN, un permiso, una constancia impresa.
- Probar roles: `admin_dafim` no entra a `/rrhh`, `admin_rrhh` no entra a `/dafim`, ambos entran a `/normativa`.

### 2. UAT en LAN (oficina DAFIM)
- Misma PC servidor + 1-2 PCs de DAFIM y 1 de RRHH.
- Firewall 8085 (handoff 12:00) y `iniciar-lan.bat` como admin si hace falta.
- Backup de Postgres **antes** de que ellos carguen el mes vivo (`pg_dump`).
- Cambiar passwords del README. Desactivar cuentas de prueba.

### 3. Endurecer antes de “produccion”
- `spring.jpa.hibernate.ddl-auto=validate` (o Flyway). `update` en prod es deuda.
- Logs a archivo rotado; no solo consola IntelliJ.
- Cabeceras: HTTPS cuando salga de LAN (Caddy/nginx + certificado). En LAN HTTP puede bastar si no hay WiFi de visitas.
- `ddl` y carpeta de storage (`StorageProperties`) en disco con backup.
- Servicio Windows (NSSM) para que no dependa de una sesion de usuario.
- Revisar limite multipart (NPG 100 MB vs README 20 MB).

### 4. Produccion municipal (criterio de listo)
- Un responsable DAFIM y uno RRHH nombrados.
- Procedimiento escrito: “como se procesa el mes” + “como se pide permiso”.
- Restaurar un dump de prueba una vez (ensayo de desastre).
- No exponer 8085 a internet. Si algun dia hay portal publico LAIP, que sea un **export estatico** (PDF/Excel), no el admin.

### 5. Despues de estable
- Elegir **un** P0 de DAFIM y **un** P0 de RRHH por trimestre. No los 14 a la vez.
- Handoff nuevo al cerrar cada funcion.

## Related Resources

- Handoff dashboard/catalogos: `.claude/handoffs/2026-08-14-145200-dashboard-mesa-mando-catalogos.md`
- Handoff compras/LAN/parsers: `.claude/handoffs/2026-08-14-120000-carga-mes-compras-ui.md`
- Decreto 57-2008 (LAIP GT), 12-2002, 57-92, 101-97, 1441, 89-2002 — textos autenticos en el Diario de Centro America / Congreso. Las pantallas son guias, no la ley.
