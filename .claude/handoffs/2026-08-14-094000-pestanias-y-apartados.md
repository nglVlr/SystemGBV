# Handoff: Cascarón de pestañas + Apartados funcionales (módulo Presupuesto)

## Session Metadata
- Created: 2026-08-14 09:40:00
- Project: C:\Users\ngl\Documents\PROYECTOS\sistema-granados v2\sistema-granados (el código vive en la subcarpeta `sistema-granados/`)
- Branch: [no es repo git — no hay control de versiones]
- Session duration: ~2 horas (dos bloques: rediseño de navegación con pestañas anidadas, luego apartados persistentes)

### Recent Commits (for context)
  - No hay git. El handoff anterior documenta el módulo presupuesto original (consultivo) y el front "Noche en Verapaz".

## Handoff Chain

- **Continues from**: [2026-08-11-160358-rediseno-front-modulo-presupuesto.md](2026-08-11-160358-rediseno-front-modulo-presupuesto.md)
- **Supersedes**: la parte de navegación del módulo (hub de 6 tarjetas + botones Volver). El resto de ese handoff (parser, entidades de carga, tema visual, gotchas PostgreSQL) sigue vigente.

## Current State Summary

Se completaron dos bloques en esta sesión, ambos con `mvnw test` en verde:

1. **Cascarón de pestañas anidadas** — el módulo ya no es un hub de tarjetas. Barra fija en las 10 (ahora 12) pantallas: 3 pestañas padre (Ver / Pagar / Cargas) con subpestañas. Folios cortos, tablas aligeradas (saldo y % primero), hilo en detalles (`Renglones / 211`). URLs antiguas intactas.

2. **Apartados funcionales** — ya no es solo análisis. Se puede reservar dinero para un pago concreto (ej. Q20,000 de fletes). Presupuesto y banco son independientes: se aparta presupuesto aunque el boletín no alcance (monto banco = 0). Lo apartado resta del saldo libre hasta Liberar o Marcar pagado. Vista en **Pagar → Apartados**.

Estado final: tests en verde (incluye `ApartadoCalculoTest`). La tabla `presupuesto_apartados` la crea Hibernate `ddl-auto=update` al arrancar. Si la app ya estaba corriendo, hace falta recompilar/reiniciar.

## Codebase Understanding

### Architecture Overview

- Spring Boot 3.2.5 / Java 17 / Thymeleaf / JPA PostgreSQL (`localhost:5432/sistema_granados`, `ddl-auto=update`). Puerto 8085. Código en `sistema-granados/`.
- El módulo presupuesto sigue siendo **un solo `@Service`** (`PresupuestoService`) con helpers estáticos puros + orquestación por repositorios. Se inyectó `ApartadoRepository` en el constructor (Spring lo cablea; no hay tests con `new PresupuestoService(...)`).
- Navegación: fragmento Thymeleaf `dafim/presupuesto/_nav :: cascaron` incluido en cada plantilla. Activo según `${uri}` de `GlobalModelAdvice` (`request.getRequestURI()`).
- Apartados **no escriben en SICOIN**. Son overlay operativo local. La clave de una línea es estable al reimportar el PDF: `renglon|fuente|actividadObra` (`PresupuestoService.claveLinea`), no el `id` de `presupuesto_lineas` (ese id cambia en cada carga).
- Seguridad: rutas nuevas caen bajo `/dafim/**` (ADMIN_DAFIM / SUPERADMIN). No se tocó `SecurityConfig`. POST usa CSRF automático de `th:action`. Confirmaciones con `form[data-confirmar]` (`app.js`).

### Critical Files

| File | Purpose | Relevance |
|------|---------|-----------|
| `sistema-granados/src/main/resources/templates/dafim/presupuesto/_nav.html` | Cascarón Ver/Pagar/Cargas + subpestañas + leyenda + hilo | Incluir con `th:replace="~{dafim/presupuesto/_nav :: cascaron}"`. `enPagar` cubre donde-pagar, apartados, apartar, transferencias |
| `sistema-granados/src/main/resources/static/css/app.css` | Tokens + clases nuevas (no se renombró el contrato) | `.pestanias-mod`, `.pestanias-sub`, `.leyenda-cifras`, `.hilo`, `.folio.compacto`, `.apartado-cabeza/origen/montos` |
| `sistema-granados/src/main/java/com/granados/sistema/dafim/presupuesto/entity/Apartado.java` | Reserva persistente | Tabla `presupuesto_apartados`. Estados: `ACTIVO`, `LIBERADO`, `USADO` |
| `sistema-granados/src/main/java/com/granados/sistema/dafim/presupuesto/repository/ApartadoRepository.java` | Listados por estado / anio | `findByEstadoOrderByFechaDesc`, `findAllByOrderByFechaDesc` |
| `sistema-granados/src/main/java/com/granados/sistema/dafim/presupuesto/service/PresupuestoService.java` | Importar + consultas + apartar/liberar/usar + overlay | Helpers: `claveLinea`, `saldoLibre`, `apartadoPresupuestoPorClave`, `apartadoBancoPorFuente`, `validarApartado`. Instancia: `apartar`, `vistaApartar`, `listarApartados`, `liberarApartado`, `marcarApartadoUsado` |
| `sistema-granados/src/main/java/com/granados/sistema/dafim/presupuesto/web/PresupuestoController.java` | Rutas | GET/POST `/apartar`, GET `/apartados`, POST `/apartados/{id}/liberar` y `/usar`. `parsearMonto` admite `model == null` |
| `sistema-granados/src/main/resources/templates/dafim/presupuesto/apartar.html` | Formulario de reserva | Dos montos independientes; banco puede ser 0 |
| `sistema-granados/src/main/resources/templates/dafim/presupuesto/apartados.html` | Lista para armar pagos | Filtros Activos / Pagados / Liberados / Todos |
| `sistema-granados/src/test/java/.../ApartadoCalculoTest.java` | Tests puros del overlay y validación | Sin Spring |

### Key Patterns Discovered

- **Identificadores en español sin tildes**; UI también sin tildes (`Ejecucion`, `renglon`, `anio`).
- Flash PRG: `"exito"` / `"error"`. Redirects de error en apartar usan `flash.addAttribute(...)` (query string) + `addFlashAttribute("error", ...)`.
- Agregaciones en memoria; overlay de apartados se aplica en `dondePagar` y `desgloseFuente` (los números SICOIN oficiales no se mutan en las entidades).
- `buscarDondePagar` tiene overload de 7 args (mapas de apartado). Los de 4 y 5 args delegan con `Map.of()` → tests viejos intactos.
- `LineaFuente.saldoDisponible` / `dineroReal` en la búsqueda ya son **libres** (SICOIN − apartado). `apartadoPresupuesto` / `apartadoBanco` van aparte para la nota "apartado Q …".
- `LineaDesglose` ahora tiene `saldoLibre` y `apartadoPresupuesto`. La vista de fuente usa `ld.saldoLibre`, no `ld.linea.saldoDisponible`.
- Confirmar acciones destructivas: `data-confirmar` en el **form**, no en el botón (`app.js` solo escucha `form[data-confirmar]`).
- URI: `/dafim/presupuesto/cargar` NO es prefijo de `/cargas` (cargar vs cargas). `/apartar` y `/apartados` son distintos. `enCargas` = cargar OR cargas.

## Work Completed

### Tasks Finished

- [x] Fragmento `_nav.html` con 3 pestañas padre, subpestañas, leyenda y hilo según `uri`
- [x] CSS de pestañas sticky + folio compacto + tarjetas de apartado (sin romper clases existentes)
- [x] 10 plantillas originales: cascarón, sin Volver al hub, folios cortos
- [x] `index.html` convertido en Resumen puro (se quitó la rejilla "Qué quieres hacer")
- [x] Listas de renglones/fuentes aligeradas (saldo y % primero)
- [x] Entidad/repositorio Apartado; persistencia ACTIVO / LIBERADO / USADO
- [x] Overlay: saldo libre = SICOIN − apartados activos; banco libre por fuente, independiente
- [x] Validación: presupuesto > 0 y ≤ libre; banco ≥ 0 y ≤ libre (0 permitido sin efectivo)
- [x] Formulario Apartar + vista Apartados + botones en ¿Con qué pago? y detalle de fuente
- [x] Pestaña **Pagar → Apartados**; leyenda con término Apartado
- [x] Tests `ApartadoCalculoTest` + suite completa en verde

### Files Modified

| File | Changes | Rationale |
|------|---------|-----------|
| `templates/dafim/presupuesto/_nav.html` | NUEVO cascarón | Navegación sin volver al hub |
| `templates/dafim/presupuesto/*.html` (10 originales) | Incluyen nav, folios cortos, sin Volver | UX pedida |
| `templates/dafim/presupuesto/apartar.html` | NUEVO formulario | Reservar pago |
| `templates/dafim/presupuesto/apartados.html` | NUEVA lista | Armar / liberar / marcar pagado |
| `static/css/app.css` | Clases nuevas al final del bloque cascarón | Pestañas + layout de apartados |
| `entity/Apartado.java` | NUEVO | Persistencia |
| `repository/ApartadoRepository.java` | NUEVO | Consultas |
| `service/PresupuestoService.java` | Repo extra, helpers, CRUD, overlay en búsqueda y desglose | Funcional, no solo análisis |
| `web/PresupuestoController.java` | Rutas apartar/apartados | PRG |
| `test/.../ApartadoCalculoTest.java` | NUEVO | Paridad de reglas de negocio |

### Decisions Made

| Decision | Options Considered | Rationale |
|----------|-------------------|-----------|
| 3 pestañas padre + subpestañas (no SPA, no 6 ítems en el menú lateral) | SPA de una página; nav mínima sin agrupar | Thymeleaf; menos ruido en el menú; no hay que "regresar" |
| URLs antiguas se mantienen | Fusionar en una sola ruta | No romper enlaces; el fragmento resuelve la pestaña activa por `uri` |
| Un registro Apartado con **dos montos** (presupuesto y banco) | Dos tablas / dos filas por tipo | Un pago = un concepto; los cubos son independientes dentro de la misma reserva |
| Presupuesto obligatorio; banco opcional (0 válido) | Exigir ambos; permitir solo banco | El usuario pidió apartar presupuesto aunque no haya disponibilidad bancaria |
| Clave `renglon\|fuente\|actividadObra`, no `linea.id` | FK a presupuesto_lineas | Al reimportar el PDF las líneas se recrean; la reserva debe sobrevivir |
| ACTIVO resta del libre; USADO/LIBERADO no | Borrar al reimportar; restar también USADO | Si se marca pagado y luego entra el PDF nuevo, no hay doble resta. Si se olvida marcar USADO y se reimporta, SÍ puede doble-contar (gotcha) |
| Overlay solo en donde-pagar y desglose de fuente | Restar también en renglones/fuentes/resumen | Menos riesgo; el flujo de armar el pago vive en Pagar. Las listas Ver siguen mostrando SICOIN crudo |

## Pending Work

### Immediate Next Steps

1. **Reiniciar/recompilar** la app (`./mvnw compile` o `spring-boot:run`) para que Hibernate cree `presupuesto_apartados` y recargue plantillas.
2. Probar E2E el flujo: buscar fletes + 20000 → Apartar (banco 0 o con efectivo) → ver en Apartados → Liberar / Marcar pagado → comprobar que el saldo libre en la búsqueda cambia.
3. Si Angel quiere, restar apartados también en **Ver → Renglones / Fuentes / Resumen** (hoy esas pantallas siguen con cifras SICOIN sin overlay).

### Blockers/Open Questions

- [ ] Si un apartado ACTIVO sigue vivo y se reimporta el PDF de SICOIN (el saldo oficial ya bajó porque el cheque se pagó), el overlay vuelve a restar → saldo libre de menos. Mitigación actual: marcar **Pagado** cuando el cheque sale. Alternativa futura: avisar al importar si los ACTIVO superan el nuevo saldo SICOIN.
- [ ] Los nombres de fuentes en el catálogo pueden seguir vacíos (del handoff anterior): se editan en Ver → Fuentes.

### Deferred Items

- Exportar desglose por fuente a Excel.
- Alertar en "Procesar el mes" si un cheque excede el saldo libre del renglón.
- Guardar simulaciones de transferencia como propuesta para SICOIN.
- Apartar solo banco sin presupuesto (no se implementó: presupuesto siempre > 0).

## Context for Resuming Agent

### Important Context

- **Workspace**: raíz `C:\Users\ngl\Documents\PROYECTOS\sistema-granados v2\sistema-granados`; Maven/Java en `sistema-granados/`. Plantillas y CSS están ahí (`src/main/resources/...`).
- **Cómo usar apartados (para Angel)**: Pagar → ¿Con qué pago? → buscar gasto y monto → botón Apartar en la fila → formulario con dos montos → Pagar → Apartados.
- **Estados**: `ACTIVO` (resta del libre), `USADO` (pagado, histórico, no resta), `LIBERADO` (cancelado, no resta).
- **Constructor de PresupuestoService** ahora tiene 8 dependencias (se añadió `ApartadoRepository` antes de `StorageService`).
- Gotchas del handoff anterior siguen: `${meses.get(p.mes)}` no `${meses[mes]}`; `100.0` en porcentajes Thymeleaf; `ddl-auto=update` NO ensancha varchar existentes.
- Tests: JUnit5 sin Spring. Correr `.\mvnw.cmd test` desde `sistema-granados/`.

### Assumptions Made

- Un apartado = un pago a armar (concepto + origen + dos montos), no dos documentos sueltos.
- El anio del apartado se copia de la carga activa al crearlo.
- Si hay varias líneas con el mismo renglon+fuente+actividadObra, se usa la de mayor saldo SICOIN.
- Marcar pagado es manual; no se engancha todavía a `historial_compras` ni a "Procesar el mes".

### Potential Gotchas

- `parsearMonto(texto, model)`: `model` puede ser null (POST apartar). Ya es null-safe.
- Thymeleaf: no anidar `${...}` dentro de otro `th:text="${...}"`. En apartados.html el programa/proyecto usa `a.proyecto` sin `${}` interno.
- `data-confirmar` va en el `<form>`, no en el `<button>`.
- `enSubir` = startsWith `/cargar` (no pisa `/cargas`). `enApartados` = `/apartados` OR `/apartar`.
- BigDecimal en vistas: `l.apartadoPresupuesto > 0` igual que el resto del módulo (SpringEL lo compara).
- PostgreSQL: tabla nueva `presupuesto_apartados`; si `ddl-auto` no corre, crearla a mano. No hace falta ALTER de columnas viejas para esta feature.
- Credenciales de BD: `application.properties` (no repetirlas aquí). Login inicial: README sección 2.

## Environment State

### Tools/Services Used

- PostgreSQL 17 local, BD `sistema_granados`.
- Maven wrapper (`.\mvnw.cmd test` en PowerShell desde `sistema-granados/`; última corrida BUILD SUCCESS, exit 0).

### Active Processes

- La app puede no estar corriendo. Arranque: `.\mvnw.cmd spring-boot:run` en `sistema-granados/` (puerto 8085). Devtools: `.\mvnw.cmd compile` recarga.

### Environment Variables

- Ninguna extra. BD en `sistema-granados/src/main/resources/application.properties`.

## Related Resources

- `.claude/handoffs/2026-08-11-160358-rediseno-front-modulo-presupuesto.md` — origen del módulo (parser, cargas, tema visual, gotchas).
- `sistema-granados/README.md` — arranque y usuarios.
- Plan de pestañas (Cursor): `c:\Users\ngl\.cursor\plans\presupuesto_pestañas_anidadas_2558ab15.plan.md` (no editar salvo que Angel lo pida).

---

**Resumen de una línea para la próxima sesión**: el presupuesto se navega con pestañas anidadas (Ver / Pagar / Cargas) y ya permite **apartar** pagos de verdad (presupuesto y banco separados, banco puede ser 0); lo ACTIVO resta del saldo libre en la búsqueda y el desglose de fuente; hay que reiniciar para crear `presupuesto_apartados` y conviene marcar Pagado cuando salga el cheque para no doble-contar al reimportar SICOIN.
