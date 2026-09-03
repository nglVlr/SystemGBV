# Handoff: Rediseño front "Noche en Verapaz" + Módulo de Presupuesto (DAFIM)

## Session Metadata
- Created: 2026-08-11 16:03:58
- Project: C:\Users\angl\Documents\Proyectos\sistema-granados (el proyecto está en la subcarpeta `sistema-granados/`)
- Branch: [no es repo git — no hay control de versiones]
- Session duration: ~4 horas (tres bloques de trabajo: rediseño del front, módulo de presupuesto, transferencias + vistas + fondo v2)

### Recent Commits (for context)
  - No hay git. Respaldo manual del front original en `sistema-granados/_respaldo-front-original/` (css/js/templates anteriores al rediseño).

## Handoff Chain

- **Continues from**: None (fresh start)
- **Supersedes**: None

> Primer handoff. Documentación de contexto anterior del proyecto: `sistema-granados/docs/CONTEXTO_MIGRACION_CHAT.md` (ojo: dice MySQL, pero el proyecto YA migró a PostgreSQL).

## Current State Summary

Se completaron tres bloques, todo verificado en vivo contra la app corriendo (PostgreSQL local arriba, puerto 8085):

1. **Rediseño total del frontend** — tema oscuro cinematográfico "Noche en Verapaz" (tipo SpaceX pero municipal): reescritura completa de `app.css` conservando el contrato de clases, fondo 3D WebGL propio (`fondo3d.js`, luego v2 sin wireframe), `app.js` extendido (tilt 3D en tarjetas, contadores animados, reveal on scroll), login y dashboard con hero.
2. **Módulo DAFIM · Presupuesto** (`/dafim/presupuesto`) — NUEVO, completo: importa el PDF SICOIN GL "Ejecución de Egresos" con cuadre al centavo, consulta por renglón y por fuente, cruce con pagos de `historial_compras`, disponibilidad mensual con semáforo, buscador "¿con qué presupuesto pago esto?", desglose por fuente y simulador de transferencias (consultivo, no persiste).
3. **Mejoras de vistas + fondo v2** — buscador de fuente por número, barras de ejecución por línea, botones Volver arriba/abajo en todo el módulo, dashboard con hero municipal e indicadores reales.

Estado final: **suite 80 tests en verde** (`mvnw test`, BUILD SUCCESS), app verificada de punta a punta con el PDF real importado en la BD local. La app quedó corriendo en segundo plano con devtools (tarea `bash-y8soyc8f` de esta sesión; si no vive, arrancar con `./mvnw spring-boot:run`).

## Codebase Understanding

## Architecture Overview

- Spring Boot 3.2.5 / Java 17 / Thymeleaf (+extras springsecurity6) / Spring Data JPA con **PostgreSQL** (`localhost:5432/sistema_granados`, `ddl-auto=update`). Puerto 8085. Devtools activo: recompilar con `./mvnw compile` reinicia la app sola.
- Paquetes por módulo bajo `com.granados.sistema`: `dafim.compras`, `dafim.paquetes`, `dafim.presupuesto` (NUEVO), `rrhh`, `usuarios`, `web` (solo DashboardController), `config` (SecurityConfig, StorageService, GlobalModelAdvice expone `uri`, DataInitializer siembra roles/usuarios, GlobalExceptionHandler).
- Seguridad por prefijo: `/dafim/**` → ADMIN_DAFIM o SUPERADMIN; `/rrhh/**` → ADMIN_RRHH o SUPERADMIN; `/admin/**` → SUPERADMIN. El módulo presupuesto NO requirió tocar SecurityConfig (cae bajo `/dafim/**`).
- Convenciones: español sin tildes en identificadores; entidades con getters/setters planos (sin Lombok); un `@Service` por módulo con inyección por constructor; escrituras `@Transactional`; agregaciones en memoria con streams (volúmenes pequeños); controladores con `@RequestParam` individuales (sin DTOs de formulario), patrón POST/redirect con flash keys exactas `"exito"`/`"error"`; validación con `IllegalArgumentException` en servicio o manual en controller.
- Tests: JUnit5 puro, SIN Spring/Mockito; parsers y servicios se prueban por helpers estáticos puros y fixtures reales en `src/test/resources/parser/`. Tradición del proyecto: **paridad/cuadre exacto contra archivos reales** (al centavo, conteos exactos de filas).
- Frontend: plantillas Thymeleaf con fragmento `layout/base :: layout(titulo, contenido)`; assets vendored en `static/vendor/` (bootstrap, bootstrap-icons, fuentes Inter/Bitter); NADA de CDN; contrato de clases CSS en `sistema-granados/src/main/resources/static/css/app.css` (las ~20 plantillas lo usan — cambiar clases existentes rompe todo).

## Critical Files

| File | Purpose | Relevance |
|------|---------|-----------|
| `src/main/resources/static/css/app.css` | Sistema de diseño completo (tema oscuro, tokens en `:root`) | Contrato de clases de TODAS las plantillas; no renombrar clases |
| `src/main/resources/static/js/fondo3d.js` | Fondo 3D WebGL sin dependencias (v2: cielo aurora + colinas rellenas + luciernagas) | Si WebGL falla → `body.sin-webgl` y CSS muestra degradado estático |
| `src/main/resources/static/js/app.js` | Menú, confirmaciones, avisos + tilt 3D, contadores, reveal, `#fechaHoy` | Los `.tarjeta-stat .valor` numéricos se animan solos |
| `sistema-granados/src/main/resources/templates/layout/base.html` | Layout con menú lateral, canvas `#fondo3d`, `.veladura` | Menú hardcodeado con `sec:authorize`; agregar módulos = editar aquí |
| `sistema-granados/src/main/java/com/granados/sistema/dafim/presupuesto/parser/ParserEjecucionEgresos.java` | Parser del PDF SICOIN GL (R00814981) | Calibrado: 367 líneas, cuadre al centavo vs fila TOTAL |
| `sistema-granados/src/main/java/com/granados/sistema/dafim/presupuesto/service/PresupuestoService.java` | Toda la lógica del módulo (importar, agregaciones, búsquedas, simulación) | Helpers estáticos puros testeables; tipos anidados de resultado |
| `sistema-granados/src/main/java/com/granados/sistema/dafim/presupuesto/web/PresupuestoController.java` | Rutas del módulo (javadoc de clase las documenta) | Patrón PRG con flash exito/error |
| `sistema-granados/src/main/java/com/granados/sistema/dafim/compras/util/Constantes.java` | `RENGLONES_CD` (31 renglones compra directa), `KEYWORDS` (palabra→renglón), `MESES_NOMBRE` (Map<Integer,String> claves 1-12) | Reusado por presupuesto (búsqueda por concepto) |
| `sistema-granados/src/main/java/com/granados/sistema/dafim/compras/service/GestionBaseDatosComprasService.java` | `estadisticasDashboard()` → Map con `ultimoMesNombre`, `ultimoEstado`, `totalHistorial` | Lo consume el DashboardController para indicadores |
| `sistema-granados/src/main/resources/application.properties` | PostgreSQL, puerto 8085, multipart 20/25MB, `rrhh.encargada.*` | Credenciales de BD aquí (postgres local) |

## Key Patterns Discovered

- **Cuadre al centavo**: el parser de ejecución calcula totales de las líneas y el test los compara contra la fila TOTAL impresa del reporte. La importación valida ≥1 línea. Verificado: vigente Q70,213,584.41 · devengado/pagado Q36,681,152.27 (agosto 2026).
- El PDF SICOIN con PDFBox `sortByPosition=true` entrega las filas de renglón **completas en una línea** (los quiebres que muestra `pdftotext -layout` no ocurren); el parser aun así acumula continuaciones por defensa. Ancla: `^\s*(\d{3})\s+(\d{2}-\d{4}-\d{4})` (renglón+fuente); 11 importes por fila en orden fijo (asignado…saldoPorPagar). Código "Act O" (000/100/200) viene como línea suelta previa al bloque y se propaga.
- FK plana con `@Index` (patrón `PermisoAdjunto`): `LineaPresupuesto.cargaId` sin `@ManyToOne` para no arrastrar la carga en listados.
- `CargaPresupuesto`: la ACTIVA es la más reciente; importar marca las anteriores REEMPLAZADA; eliminar la ACTIVA promueve la más reciente restante. `presupuesto_fuentes` se auto-siembra con códigos nuevos (nombre vacío, editable inline en `/fuentes`, nunca pisa nombres ya editados).
- El cruce presupuesto↔pagos usa `historial_compras.renglon` (cada pago ya tiene su renglón de 3 dígitos).

## Work Completed

## Tasks Finished

- [x] Rediseño frontend completo (tema "Noche en Verapaz": oscuro, dorado del escudo, vidrio esmerilado)
- [x] Fondo 3D WebGL v1 (wireframe) → **v2** (cielo con aurora + colinas RELLENAS con luz difusa + luciernagas; menos retro, más web pública)
- [x] Login cinematográfico + dashboard con hero municipal (escudo con pulso, fecha del día vía `#fechaHoy`)
- [x] Módulo presupuesto: parser + 3 entidades + 3 repos + servicio + controller + 10 templates
- [x] Importación PDF con cuadre exacto (verificado E2E: POST real del PDF de agosto)
- [x] Buscador "¿Con qué presupuesto pago esto?" (`/donde-pagar`) con keywords + texto libre + código
- [x] Desglose por fuente (`/fuentes/{codigo}`) agrupado programa→proyecto con subtotales
- [x] Simulador de transferencias (`/transferencias`) consultivo (no escribe BD)
- [x] Buscador de fuente por número (`/fuentes/ir?q=`) con manejo exacto/ambiguo/inexistente
- [x] Barras de ejecución por línea y por fuente; botones Volver arriba y abajo en todo el módulo
- [x] Iconos en botones; tarjeta del módulo en dashboard + ítem de menú (bloque DAFIM)
- [x] README actualizado con el módulo; respaldo del front original

## Files Modified (principales)

| File | Changes | Rationale |
|------|---------|-----------|
| `sistema-granados/src/main/resources/static/css/app.css` | Reescrita completa (~900 líneas): tokens oscuros, vidrio, `.barra-progreso` (con variantes grande y slim, mas niveles de color), `.hero-muni`, `.apilado-grupos`, print light | Mismo contrato de clases → las 20 plantillas heredan el look sin tocarlas |
| `sistema-granados/src/main/resources/static/js/fondo3d.js` | NUEVO. v2: sky shader con aurora, terreno por triángulos con normales por diferencias finitas, partículas aditivas | WebGL puro sin deps; respeta reduced-motion; pausa al ocultar pestaña |
| `sistema-granados/src/main/resources/static/js/app.js` | Extendido: IntersectionObserver `.revelar`, tilt+glow en `.tarjeta-accion`, contadores animados, `#fechaHoy` | Comportamiento original intacto |
| `sistema-granados/src/main/resources/templates/layout/base.html` | Canvas + veladura + script fondo3d + ítem menú "Presupuesto" (bi-bank) | — |
| `sistema-granados/src/main/resources/templates/login.html`, `dashboard.html` | Reescritos (hero cinematográfico / hero municipal + indicadores reales) | — |
| `sistema-granados/src/main/java/com/granados/sistema/dafim/presupuesto/` (10 clases) | NUEVAS: parser, dto (2), entity (3), repository (3), service, controller | Estructura por módulo del proyecto |
| `sistema-granados/src/main/resources/templates/dafim/presupuesto/` (10 templates) | NUEVAS: index, cargar, renglones, renglon-detalle, fuentes, fuente-detalle, disponibilidad, donde-pagar, transferencias, cargas | — |
| `sistema-granados/src/main/java/com/granados/sistema/web/DashboardController.java` | Inyecta PresupuestoService + GestionBaseDatosComprasService; atributos solo si hay datos | Indicadores reales en portada |
| `README.md` | Tabla de módulos + párrafo del módulo presupuesto | Docs al día |
| `src/test/.../presupuesto/**` | ParserEjecucionEgresosTest + PresupuestoServiceTest (22 tests) + fixture `ejecucion-egresos.pdf` | Calibrados con el PDF real |

## Decisions Made

| Decision | Options Considered | Rationale |
|----------|-------------------|-----------|
| Reescribir app.css conservando el contrato de clases | Editar las 20 plantillas una a una | Un solo archivo transforma todo sin riesgo de romper pantallas |
| WebGL propio sin Three.js | Vendor three.min.js | Sin dependencias, ~350 líneas, control total; el proyecto evita CDNs |
| Presupuesto 100% consultivo (simulación no persiste) | Guardar borradores de transferencia | SICOIN es el sistema de registro; la app no debe modificar el presupuesto |
| Catálogo de fuentes con nombres vacíos editables | Inventar nombres oficiales | No se conocen con certeza; Angel los rotula inline en `/fuentes` |
| Ensanchar columnas en PostgreSQL a mano (`ALTER COLUMN` a varchar(90)) | Drop/recreate tabla | `ddl-auto=update` NO ensancha columnas existentes; la tabla estaba vacía pero el ALTER preserva todo |
| Búsqueda por concepto con doble dirección de keywords | Solo `consulta.contains(keyword)` | "camionadas" no contiene "CAMIONADAS DE 10"; se agregó prefijo (consulta ≥4 chars que inicia la keyword) |
| Fondo v2 con terreno relleno + aurora | Mantener wireframe / partículas tipo constelación | El usuario pidió "menos retro, más web pública" |

## Pending Work

## Immediate Next Steps

(ideas priorizadas, ninguna iniciada)

1. **Conectar disponibilidad con "Procesar el mes"** de compras: alertar si un cheque excede el saldo del renglón en la carga activa (consultivo, no bloqueante).
2. **Exportar desglose por fuente a Excel** (Apache POI ya está; patrón en `ExcelGeneradorService`).
3. **Borrador de modificación presupuestaria**: guardar varias simulaciones como propuesta para tramitar en SICOIN.

## Blockers/Open Questions

- [ ] `contexto muni.pdf` (raíz del proyecto) está **escaneado sin capa de texto** — no se pudo leer; si tiene reglas de negocio relevantes, pedir versión con OCR o que Angel la resuma.
- [ ] Los nombres de las 16 fuentes están vacíos en el catálogo — Angel debe rotularlas en `/fuentes`.

## Deferred Items

- Nada bloqueado técnicamente. El cruce con `historial_compras` solo aplica a renglones de compra directa (`RENGLONES_CD`); los de planilla (011, 015, 051...) se muestran solo con ejecución SICOIN (por diseño: esos pagos no pasan por el sistema).

## Context for Resuming Agent

## Important Context

(LO ESENCIAL que la próxima sesión debe saber)

- **La BD local YA TIENE la carga de agosto 2026 importada** (367 líneas; estado ACTIVA) de la verificación E2E. Al reimportar un mes nuevo, la anterior queda REEMPLAZADA (historial en `/cargas`).
- **Gotcha PostgreSQL**: si cambias `length` de un `@Column` en una tabla existente, Hibernate `update` NO lo aplica — ejecuta el `ALTER TABLE ... ALTER COLUMN ... TYPE varchar(n)` a mano (psql en `C:\Program Files\PostgreSQL\17\bin\psql.exe`, credenciales en `sistema-granados/src/main/resources/application.properties`). Ya se hizo para `presupuesto_lineas.programa/subprograma/proyecto` → varchar(90) (el parser guarda etiqueta completa "01 ACTIVIDADES CENTRALES").
- **Gotcha Thymeleaf**: `${meses[mes]}` NO indexa un `Map<Integer,String>` (renderiza null). Usar `${meses.get(p.mes)}` o resolver el nombre en el controller (`nombreMes`).
- **Gotcha SpringEL/BigDecimal**: para porcentajes en vistas usar `${dev * 100.0 / vig}` (el literal `100.0` fuerza double); `BigDecimal.divide` puede lanzar ArithmeticException.
- Verificación E2E sin navegador manual: login por curl con cookie jar + `_csrf` del form; capturas visuales con Edge headless vía CDP (`--remote-debugging-port=9222`, script Node con WebSocket nativo que hace `Network.setCookie` con el JSESSIONID y `Page.captureScreenshot`). Credenciales iniciales: ver README sección 2 (no repetirlas aquí).
- La app usa devtools: `./mvnw compile` dispara el reinicio; las sesiones caen (re-login en cada verificación).
- Flujo de swarm usado (Angel lo pidió y gustó): olas de agentes coder con archivos disjuntos + prompts con API exacta; verificación final del agente principal con tests + capturas.

## Assumptions Made

- Los nombres oficiales de fuentes no se inventaron (quedan editables).
- "Objetos de gasto / letras grandes del PDF" = las descripciones en mayúsculas de los renglones (ya parseadas en `LineaPresupuesto.descripcion`).
- El saldo proyectado de `/disponibilidad` es estimado (saldo SICOIN al corte − pagos del mes en el sistema); la vista lo declara.

## Potential Gotchas

- `ParserEjecucionEgresosTest` espera EXACTAMENTE 367 líneas y los totales de agosto: si se reemplaza el fixture, recalibrar.
- `fondos3d.js` (v2): el terreno usa `Uint16Array` de índices — si se sube la resolución de malla por encima de ~65k vértices hay que pasar a `Uint32Array` + extensión.
- Si PostgreSQL está apagado, la app no arranca (la BD ya existe; no hace falta crearla).
- `_respaldo-front-original/` contiene el front viejo por si algo; no se empaqueta en el jar (está fuera de `src/`).
- Hay un directorio espurio vacío `dafim/compras/{entity,repository,...}` (error histórico de mkdir) — ignorarlo, no borrar sin revisar.

## Environment State

### Tools/Services Used

- PostgreSQL 17 local (corriendo), BD `sistema_granados`.
- Maven wrapper (`./mvnw` en Git Bash; `cmd //c mvnw.cmd` si falla).
- `pdftotext` (mingw) para inspeccionar PDFs; Edge headless (`--headless=new`) + CDP para capturas; Node 24 (WebSocket nativo).

### Active Processes

- La app Spring Boot pudo quedar corriendo en segundo plano (tarea de esta sesión, puerto 8085). Si el puerto está ocupado al arrancar otra instancia, detener la previa.

### Environment Variables

- Ninguna requerida. Credenciales de BD en `sistema-granados/src/main/resources/application.properties` (valores no repetidos aquí).

## Related Resources

- `sistema-granados/README.md` — arranque, usuarios iniciales, módulos.
- `sistema-granados/docs/CONTEXTO_MIGRACION_CHAT.md` — historia del proyecto (desactualizado: dice MySQL/POI viejo; el estado real es PostgreSQL 17 + POI 5.2.5 + PDFBox 2.0.31).
- `sistema-granados/servidor/LEEME_SERVIDOR.md` — despliegue en Windows Server (NSSM).
- PDF fuente del módulo: `EJECUCION DE EGRESOS AGOSTO 2026.pdf` (raíz del proyecto) — fixture en `src/test/resources/parser/ejecucion-egresos.pdf`.
- Planes de esta sesión: `.kimi-code/sessions/.../plans/` (rediseño + módulo presupuesto + transferencias/vistas).

---

**Resumen de una línea para la próxima sesión**: sistema-granados tiene front oscuro nuevo (contrato de clases intacto) y un módulo de presupuesto completo y verificado (`/dafim/presupuesto`) que importa el PDF SICOIN de ejecución de egresos con cuadre exacto, consulta renglones/fuentes, busca dónde pagar conceptos, desglosa fuentes y simula transferencias; 80 tests en verde; ojo con los gotchas de columnas varchar en PostgreSQL y mapas Integer en Thymeleaf.
