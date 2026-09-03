# Handoff: Carga mensual de compras + parsers + vistas

## Session Metadata
- Created: 2026-08-14 12:00:00
- Project: C:\Users\ngl\Documents\PROYECTOS\sistema-granados v2\sistema-granados (el código vive en la subcarpeta `sistema-granados/`)
- Branch: [no es repo git — no hay control de versiones]
- Session duration: ~3 horas (LAN temporal, límite NPG 100 MB, luego parsers/UI de compras)

### Recent Commits (for context)
  - No hay git.

## Handoff Chain

- **Continues from**: [2026-08-14-094000-pestanias-y-apartados.md](2026-08-14-094000-pestanias-y-apartados.md)
- **Also related**: [2026-08-11-160358-rediseno-front-modulo-presupuesto.md](2026-08-11-160358-rediseno-front-modulo-presupuesto.md) (tema visual "Noche en Verapaz")
- **Does not supersede**: el cascarón de presupuesto ni los apartados. Este handoff cubre **compras directas** y el modo **servidor LAN temporal**.

## Current State Summary

En esta sesión se hicieron tres bloques:

1. **Servidor LAN temporal** — esta PC sirve el sistema a otras máquinas de la red (`server.address=0.0.0.0`, puerto 8085). Postgres quedó atado a `127.0.0.1:5432`. IP actual de Ethernet 2: `192.168.101.45`. Falta que el usuario ejecute `servidor/abrir-firewall.bat` **como administrador** (la red está en perfil Público).
2. **NPG hasta 100 MB** — `max-file-size=100MB`, request 150 MB, Tomcat swallow/form-post 150 MB, `max-part-count=1000`.
3. **Carga de mes de compras** — parsers endurecidos (BOM, monto `Q.`, IMPRESO case-insensitive, fechas Excel, TXT/PDF vacíos fallan con mensaje claro) y vistas de compras con overflow, overlay de procesamiento, nombres de archivo y más animaciones del mismo lenguaje visual.

Tests corridos al final: `ParserGuatecomprasTest`, `ParsersExcelTest`, `ParserNpgConfirmacionTest`, `ParserSicoinTest`, `MotorComprasServiceTest`, `ValidadorComprasServiceTest` — **verde**.

Hay que **reiniciar la app** (IntelliJ o `iniciar-lan.bat`) para que tomen efecto properties, parsers y estáticos.

## Codebase Understanding

### Architecture Overview

- Spring Boot 3.2.5 / Java 17 / Thymeleaf / JPA PostgreSQL 17 en Docker (`sistema-granados-db`). Puerto app **8085**.
- Flujo mensual (`POST /dafim/compras/procesar`): Cheques Excel + TXT Guatecompras + PDF SICOIN (+ planilla 029 opcional) → parsers → `MotorComprasService.construirFilas` → validador + remuneraciones + comparador → Excel legal → resultado en sesión → confirmar para persistir.
- Los parsers de compras viven en `dafim/compras/parser/`. SICOIN y el motor están calibrados contra junio 2026 (no tocar layout regex de SICOIN sin PDF real).
- Front: `static/css/app.css` + `static/js/app.js` (tema "Noche en Verapaz"). Hooks: `data-procesando`, `data-nombre`, `data-confirmar`, `.aviso[data-auto]`, `.revelar`.
- Seguridad: `/dafim/**` = ADMIN_DAFIM o SUPERADMIN. CSRF en POST vía `th:action`.

### Critical Files

| File | Purpose | Relevance |
|------|---------|-----------|
| `src/main/resources/application.properties` | Puerto, bind LAN, multipart 100 MB, JDBC | Servidor temporal + límite de subida |
| `docker-compose.yml` | Postgres solo en 127.0.0.1:5432 | No exponer BD a la LAN |
| `servidor/iniciar-lan.bat` | Arranca Docker + Spring | Cómo servir a otras PCs |
| `servidor/abrir-firewall.bat` | Abre TCP 8085 (requiere admin) | Bloqueador actual de la LAN |
| `dafim/compras/web/ComprasDirectasController.java` | Flujo /procesar | Guards TXT/PDF vacíos, errores por archivo |
| `parser/ParserCheques.java` | Excel banco | IMPRESO case-insensitive, fechas via `CeldaUtil.fecha` |
| `parser/ParserGuatecompras.java` | TXT publicaciones | BOM UTF-8, monto `Q.` |
| `parser/ParserSicoin.java` | PDF detalle proveedor | Sólido; el vacío ahora se rechaza en el controller |
| `parser/ParserMachote.java` | Excel 15 columnas | Salta filas en blanco; fechas Excel |
| `parser/ParserNpgConfirmacion.java` | PDFs NPG históricos | NIT con `TextoUtil.limpiarNit` |
| `parser/CeldaUtil.java` | Lectura POI | `fecha()` para seriales Excel 30000–80000 |
| `templates/dafim/compras/procesar.html` | Carga del mes | Overlay, nombres de archivo, tabla scroll, barra pegajosa |
| `static/css/app.css` | Estilos globales | `.velo-procesando`, `.scroll-alto`, `.mt-12`, `.campo` |
| `static/js/app.js` | Overlay + nombres de archivo | `data-procesando` ahora pinta velo a pantalla completa |

### Key Patterns Discovered

- Mensajes al personal municipal en español, sin jerga POI/PDFBox.
- Fallar **antes** del motor si un archivo obligatorio parsea a lista vacía (antes el proceso “salía bien” con cientos de REVISAR).
- No inventar un lenguaje visual nuevo: reutilizar `--jade`, `aparecer`, `deslizarAviso`, `data-procesando`.
- `.tarjeta` ya anima con `aparecer` al cargar. **No** mezclar `.revelar` en la misma tarjeta (pelea opacity).
- `form[data-confirmar]` se registra **antes** que `data-procesando`; el overlay respeta `ev.defaultPrevented` si el usuario cancela el confirm.
- Tests de Excel se fabrican en memoria con POI (`ParsersExcelTest`). PDF SICOIN real: `src/test/resources/parser/`.

## Work Completed

### Tasks Finished

- [x] Configurar esta PC como servidor LAN temporal (bind 0.0.0.0, Postgres localhost, scripts en `servidor/`)
- [x] Subir límite de NPG de 20 MB a 100 MB (multipart + Tomcat)
- [x] Endurecer parsers de la carga mensual y NPG
- [x] Guards en el controller: TXT/PDF vacíos, errores por tipo de archivo, aviso si remuneraciones no produce R029
- [x] Tests extra: BOM+Q., IMPRESO en minúsculas, fila vacía en machote
- [x] Vistas compras: overflow, overlay, nombres de archivo, estados vacíos, animaciones, utilidades CSS faltantes
- [x] Tests de parsers/motor en verde

### Files Modified

| File | Changes | Rationale |
|------|---------|-----------|
| `application.properties` | `server.address=0.0.0.0`; multipart 100/150 MB; Tomcat swallow/form/part-count | LAN + ZIPs grandes de NPG |
| `docker-compose.yml` | `127.0.0.1:5432:5432` | BD no sale a la red |
| `servidor/iniciar-lan.bat` | Arranque Docker + mvnw | Servir sin IntelliJ |
| `servidor/abrir-firewall.bat` | Regla TCP 8085 | Windows bloquea perfil Público |
| `GlobalExceptionHandler.java` | Mensaje 100 MB | Alineado al nuevo límite |
| `ParserCheques.java` | Estado case-insensitive; `CeldaUtil.fecha`; mensaje más claro | Reportes banco con "impreso" |
| `ParserGuatecompras.java` | BOM; prefijo `Q.` en monto | Export Windows / líneas `Q. 3,500.00` |
| `ParserMachote.java` | `continue` en fila vacía; `leerFecha` | Machotes editados a mano |
| `ParserNpgConfirmacion.java` | `limpiarNit`; error no-null | Match con BD |
| `CeldaUtil.java` | Dates como `dd/MM/yyyy`; `fecha()` serial | Celdas fecha sin formato Excel |
| `ComprasDirectasController.java` | Guards + try/catch por archivo + alerta remuneraciones | Evitar corridas silenciosas malas |
| `ParserGuatecomprasTest.java` | BOM+Q.; vacío | Regresión |
| `ParsersExcelTest.java` | "impreso"; fila en blanco machote | Regresión |
| `app.css` | overlay, spinner jade, scroll-alto, celdas, mt-12, campo, chip ellipsis, botones disabled, barra pegajosa | Bugs visuales + animaciones |
| `app.js` | Velo pantalla completa; `con-archivo`; cerrar aviso | Feedback al procesar |
| `layout/base.html` | Botón cerrar en error | Avisos rojos ya no se quedan eternos |
| `procesar.html` | data-nombre, scroll-alto, proveedor ellipsis, acta vacía oculta, barra pegajosa, overlay en confirmar | Carga del mes |
| `buscar.html` | form-select, mt-12, buscador flex, empty state | Filtros rotos visualmente |
| `cargar-npgs.html` | data-hint, scroll, truncar archivo, suma Thymeleaf | NPG |
| `cargar-machote.html` | data-nombre, scroll-alto, overlay confirmar | Machote |
| `index.html` | fallback `ultimoMesNombre` | Stat en blanco |

### Decisions Made

| Decision | Options Considered | Rationale |
|----------|-------------------|-----------|
| Rechazar TXT/PDF vacíos en el controller, no dentro de cada parser | Parser lanza vs controller valida | El parser SICOIN está calibrado y testeado con PDF real; el fallo de “archivo equivocado” es de orquestación |
| 100 MB archivo / 150 MB request | 100/100 vs 100/150 | Overhead multipart + varios PDFs sueltos |
| No usar `.revelar` en `.tarjeta` | Scroll-reveal vs aparecer de carga | Ambas pelean `opacity`; las tarjetas ya entran con `aparecer` |
| Overlay global en `data-procesando` | Solo spinner en botón | El mes tarda; sin velo se puede navegar o reenviar |
| Postgres solo localhost | Dejar 0.0.0.0:5432 | Otras PCs no deben hablar con la BD, solo con :8085 |
| NSSM/servicio Windows no instalado | Servicio permanente vs temporal | El usuario dijo que es mientras vende y sube a web |

## Pending Work

### Immediate Next Steps

1. **Reiniciar la app** para cargar parsers, properties y estáticos.
2. **Firewall**: clic derecho `sistema-granados/servidor/abrir-firewall.bat` → Ejecutar como administrador. Sin esto las otras PCs no entran.
3. Probar carga de mes con los 3 archivos reales (cheques + TXT + PDF SICOIN). Si el TXT o el PDF están mal, ahora debe salir un error claro en rojo, no un informe vacío.
4. En otras PCs de `192.168.101.x`: `http://192.168.101.45:8085` (si cambió la IP, `ipconfig` en Ethernet 2).

### Blockers/Open Questions

- [ ] Firewall 8085: la regla **no se creó** en la sesión (UAC). El usuario debe correr el .bat como admin.
- [ ] Perfil de red de Ethernet 2 es **Público**. Conviene marcarlo Privado.
- [ ] IP `192.168.101.45` es DHCP. Si cambia, hay que actualizar la URL en las otras PCs.
- [ ] Python no está en el PATH de esta máquina; el scaffold/validador de handoff no se pudo ejecutar.

### Deferred Items

- Servicio Windows (NSSM) 24/7 — fuera de alcance (setup temporal).
- `LEEME_SERVIDOR.md` sigue hablando de MySQL; desactualizado. No se reescribió.
- ZIP en la carga mensual (sigue siendo 3 archivos sueltos).
- Subir a hosting web — el usuario lo hará al vender el proyecto.
- Tests de controller (`MockMvc`) para los guards de TXT/PDF vacíos — no hay capa web-test aún.

## Context for Resuming Agent

### Important Context

- Código en `sistema-granados/` (no en la raíz del workspace).
- Credenciales iniciales: las del README (`superadmin`, `admin_dafim`, `admin_rrhh`). Cambiarlas tras el primer ingreso. **No** documentar ni commitear passwords de Postgres.
- Hibernate `ddl-auto=update` — no pasar a `create`.
- Parser SICOIN (`RE_REG`, 5 dígitos de cheque, `sortByPosition`) está validado con PDF real de 620 registros. No “limpiar” el regex sin un PDF nuevo.
- Motor de compras es porteo 1:1 del Python/Colab; los tests de `MotorComprasServiceTest` son la paridad. No cambiar matching fuzzy 029 ni pool de NPG a menos que haya un caso real que falle.
- Tema CSS: oro/jade sobre noche. Tokens `--jade`, `--hoja`, `--linea`. Animación `aparecer` ya está en folio/tarjeta/stat.
- Docker: `docker compose up -d` desde `sistema-granados/`. Volumen `pgdata` persiste.

### Assumptions Made

- Las otras PCs están en `192.168.101.0/24` (Wi-Fi de esta máquina está desconectado; solo Ethernet 2).
- El tope de 100 MB aplica a todos los uploads (multipart es global), no solo NPG. Aceptable para uso interno.
- “Bugs visuales” = overflow de tablas, `mt-12` inexistente, overlay ausente, selects sin `form-select`, actas vacías — no un rediseño de presupuesto.

### Potential Gotchas

- `MultipartFile.getInputStream()` se llama después de `storage.guardarSubida` / `getBytes()`; en Spring el archivo de disco se puede releer. No cachear el stream.
- `ParserCheques` sigue exigiendo cheque de 4–6 dígitos; SICOIN casa **exactamente 5**. Cheques de 4 o 6 no cruzan el PDF.
- `CeldaUtil.fecha` solo convierte seriales 30000–80000. Un número de cheque en columna de fecha no se vuelve Date.
- Si `data-procesando` y `data-confirmar` están en el mismo form, cancelar el confirm **no** debe mostrar el velo (`defaultPrevented`).
- `target/classes/` no se edita a mano; DevTools/reinicio copia resources.
- Conflicto de Postgres: si el servicio Windows `postgresql-x64-17` ocupa 5432, el contenedor no arranca. Esta sesión dejó Docker healthy en 127.0.0.1:5432.

## Environment State

### Tools/Services Used

- Docker: contenedor `sistema-granados-db` healthy, puerto `127.0.0.1:5432`
- JDK 17 (`java 17.0.12`) en PATH
- Maven wrapper `mvnw.cmd`
- IntelliJ suele ser el arranque diario; LAN usa `servidor/iniciar-lan.bat`

### Active Processes

- Postgres Docker: up (al momento del handoff)
- App Spring: **puede estar vieja** si no se reinició después de estos cambios

### Environment Variables

- Ninguna extra. JDBC y password viven en `application.properties` / `docker-compose.yml` (no copiar valores secretos aquí).

## Related Resources

- README módulo compras: `sistema-granados/README.md` (sección 3 flujo mensual; aún dice “20 MB” en problemas frecuentes — desactualizado)
- Guía servidor vieja (MySQL): `sistema-granados/servidor/LEEME_SERVIDOR.md`
- Handoff presupuesto/apartados: `.claude/handoffs/2026-08-14-094000-pestanias-y-apartados.md`
- Handoff front: `.claude/handoffs/2026-08-11-160358-rediseno-front-modulo-presupuesto.md`
- Tests parsers: `src/test/java/com/granados/sistema/dafim/compras/parser/`
- PDFs fixture: `src/test/resources/parser/`
