# CONTEXTO COMPLETO: sistema-granados + integración futura con sistema de quejas

Soy Angel ("bro"). Tengo DOS proyectos separados que en algún momento
futuro quiero fusionar en un solo producto más completo para vender:

1. **sistema-granados** (MÍO, personal): sistema de la Municipalidad de
   Granados, Baja Verapaz, Guatemala. Ya está en producción/casi listo
   para servidor local.
2. **Sistema de quejas** (proyecto de la UNIVERSIDAD, me tocó también con
   una municipalidad): necesito migrar su base de datos de MySQL a
   PostgreSQL. Es un proyecto académico, aparte del mío.

**Meta a futuro**: integrar ambos en un solo sistema municipal más
completo, para ofrecerlo como producto. Por ahora son independientes.

En esta sesión nueva quiero que me ayudes primero con la migración de
MySQL a PostgreSQL del sistema de quejas (proyecto de la universidad), y
que tengas todo el contexto de sistema-granados por si lo necesito
retomar o mencionar cosas de ahí.

---

## PARTE 1: sistema-granados (contexto completo, ya construido)

### Qué es
Sistema web para la DAFIM (Dirección de Administración Financiera Integrada
Municipal) y RRHH de la Municipalidad de Granados, Baja Verapaz. Porteo de
un Colab de Python a una aplicación Java completa, con calibración exacta
contra archivos reales de la municipalidad (no solo datos de prueba).

### Stack tecnológico
- **Backend**: Spring Boot 3.2.5, Java 17
- **Vistas**: Thymeleaf (server-side rendering, sin frontend framework)
- **Base de datos**: MySQL (`jdbc:mysql://localhost:3306/sistema_granados`)
- **ORM**: Spring Data JPA / Hibernate, con `spring.jpa.hibernate.ddl-auto=update`
  (las tablas se crean/actualizan solas, NUNCA se usa `create`/`create-drop`
  para no perder datos)
- **Seguridad**: Spring Security con roles (SUPERADMIN, ADMIN_DAFIM,
  ADMIN_RRHH), rutas protegidas por prefijo (`/dafim/**`, `/rrhh/**`,
  `/admin/**`)
- **Librerías de parsing**: Apache POI 4.0.1 (Excel: .xls/.xlsx), PDFBox 2.0.29
  (extracción de texto y merge de PDFs)
- **CSS propio**: sin frameworks (Bootstrack solo para íconos `bi-*`), paleta
  de variables CSS (`--pino`, `--jade`, `--papel`, etc.) tema dorado/bronce
  oscuro inspirado en el escudo real de la municipalidad
- **Empaquetado**: `spring-boot-maven-plugin` genera un fat-jar ejecutable
  con `mvn package` → `java -jar sistema-granados.jar`

### Estructura de paquetes Java
```
com.granados.sistema
├── usuarios/          (gestión de usuarios y roles del sistema)
├── web/                (DashboardController general)
├── dafim/
│   ├── compras/        (Compras Directas — módulo original portado de Python)
│   │   ├── dto/         RegistroSicoin, FilaCompra, Cheque, RegistroGuatecompras,
│   │   │                NpgConfirmacion, ContratoInfo, DatosBd, etc.
│   │   ├── parser/      ParserSicoin, ParserCheques, ParserGuatecompras,
│   │   │                ParserNpgConfirmacion, ParserMachote, ParserRemuneraciones,
│   │   │                CeldaUtil
│   │   ├── service/     MotorComprasService (el "motor" central que arma las
│   │   │                filas), ValidadorComprasService, ComparadorMensualService,
│   │   │                ExcelGeneradorService, GestionBaseDatosComprasService
│   │   ├── entity/       HistorialCompra, Proveedor, ContratoPersonal029, etc.
│   │   └── web/          ComprasDirectasController
│   └── paquetes/        (Paquetes de facturas SAT — módulo nuevo)
│       ├── dto/          FacturaSatDatos, PaqueteDatos (con Linea interna)
│       ├── parser/       ParserFacturaSat (lee PDFs FEL de la SAT),
│       │                ParserPaquetesExcel (cada HOJA del Excel = un paquete)
│       ├── service/      EmparejadorFacturas (casa facturas con líneas por
│       │                monto exacto + similitud Jaccard de texto),
│       │                PaquetesService (orquesta todo, guarda en BD, arma
│       │                el PDF unido para imprimir)
│       ├── entity/       FacturaSat, FacturaPdf (blob separado), PaqueteFacturas,
│       │                LineaPaquete
│       ├── repository/   los 4 repos JPA correspondientes
│       └── web/          PaquetesFacturasController
└── rrhh/                 (Recursos Humanos — módulo nuevo)
    ├── entity/            Empleado, Permiso, PermisoAdjunto (el escaneado
    │                     físico firmado, blob separado)
    ├── repository/        EmpleadoRepository, PermisoRepository,
    │                     PermisoAdjuntoRepository
    ├── service/           RrhhService (con filtros combinables)
    └── web/               RrhhController
```

### Módulo 1: Compras Directas (el original, portado de Python)
Genera el informe mensual de compras directas / contrataciones de bienes y
servicios de la municipalidad, a partir de:
- PDF del SICOIN (reporte de ejecución, ~50 páginas)
- Reporte de cheques (.xls)
- Bloque TXT de Guatecompras (NPGs publicados)
- PDFs de confirmación de NPG (opcional, uno por publicación)

**Calibrado con datos reales de junio 2026**: 543 filas generadas
idénticas al Colab original, total Q2,951,154.59 cuadrado al centavo, 0
diferencias reales (solo 15 casos donde el motor Java quedó MÁS limpio
que el Colab por un bug de pdfplumber que arrastraba texto duplicado).

Incluye carga de NPGs por PDF individual, ZIP masivo, o ingreso manual (para
cuando no se tiene el PDF o viene en formato raro). El parser de NPG tiene
respaldos (regex alternativos) para tolerar layouts distintos.

### Módulo 2: Paquetes de facturas SAT (nuevo, el más reciente)
La oficina manda mensualmente un Excel (cada HOJA es un "paquete" con
líneas de concepto+monto) y por separado los PDFs de facturas FEL de la
SAT, desordenados. El sistema:
1. Lee cada factura FEL (autorización/UUID, serie, DTE, NIT y nombre del
   emisor, fecha, monto, descripción de ítems)
2. Casa cada línea de cada paquete con UNA factura por monto exacto +
   similitud de texto (tokens normalizados, Jaccard, con stopwords en
   español)
3. **Nunca repite una factura**: el número de autorización es UNIQUE en
   la BD, ni dentro del mismo lote ni contra meses anteriores ya guardados
4. Guarda todo por mes (paquetes, líneas, facturas, PDFs originales)
5. Imprime cada paquete como UN SOLO PDF con las facturas mergeadas en el
   orden del Excel (para imprimir físico y engrapar en ese orden)
6. Asignación manual de respaldo para las líneas que no encuentran match
   automático

**Bug importante que se resolvió**: el parser de facturas originalmente
fallaba con layouts reales que traían una columna extra ("Otros
Descuentos"), dejando la descripción vacía. Se arregló con un enfoque
robusto: en vez de contar columnas exactas de dinero, se **restan** los
números decimales y la palabra "IVA" del texto del ítem, dejando solo la
descripción real. Esto se probó con 183 facturas reales de junio: **183/183
asignadas con similitud perfecta 1.00**.

**Validado también con julio real** (214 PDFs): 211 asignadas
automáticamente, 3 pendientes explicadas y correctas (1 PDF corrupto de 3
bytes, 2 pares de facturas duplicadas reenviadas con nombre de archivo
distinto pero mismo número de autorización interno — el sistema las
detectó bien y no las repitió).

También se descubrió y arregló un bug de hojas OCULTAS en Excel (el
parser ahora las salta, porque a veces quedan hojas viejas ocultas de
meses anteriores que inflaban el conteo de paquetes).

### Módulo 3: Recursos Humanos (nuevo)
- **Personal**: alta de empleados (cargo, dependencia, renglón
  presupuestario, DPI, fecha de ingreso), con búsqueda por texto
- **Permisos**: solicitud por días o por horas, tipos (IGSS, personal,
  vacaciones, luto, maternidad, estudio, comisión, otro), con/sin goce de
  salario, bandeja con aprobación/rechazo y filtros combinables (empleado,
  tipo, rango de fechas)
- **Permiso físico escaneado**: se puede adjuntar el PDF/imagen del papel
  firmado por el empleado y su encargado de oficina (queda en tabla aparte
  como blob, para auditoría)
- **Constancia formal imprimible**: redacción tipo oficio, con ñ/tildes
  correctas, firma ÚNICA de la encargada de RRHH (nombre configurable en
  `application.properties`, no el usuario del sistema ni el empleado), y
  al imprimir se oculta todo el HUD del sistema (para hojas membretadas)

### Decisiones de diseño importantes a recordar
- **Privacidad en memoria/DB**: nombres de familiares nunca se guardan
  literalmente en archivos de memoria de Claude; en la app sí se guardan
  nombres de empleados normalmente porque es información laboral pública
  de la municipalidad, no dato sensible personal
- **Nunca usar em dash (—)** en las respuestas de texto (preferencia
  explícita del usuario)
- **Nunca reescribir toda la clase/archivo si un cambio quirúrgico basta**
  (para no gastar tokens) — se prefieren ediciones puntuales con
  str_replace sobre archivos existentes
- El usuario prueba TODO con archivos reales de la municipalidad antes de
  dar por bueno un módulo; cuando algo falla, la respuesta correcta casi
  siempre es diagnosticar con los datos reales antes de tocar código a
  ciegas (varias veces el "bug" resultó ser un archivo corrupto o datos
  mal enviados, no un error de lógica)

### Estado del despliegue
Está por instalarse como servidor local en una máquina Windows de la
municipalidad, conectada por LAN a las demás computadoras vía router +
switch. Guía completa ya entregada en `servidor/LEEME_SERVIDOR.md` dentro
del proyecto: IP fija por reserva DHCP en el router, contraseña a MySQL
root (estaba en blanco, hay que cambiarla), regla de firewall para el
puerto 8085, servicio de Windows con NSSM para que arranque solo, y
respaldo automático diario con `mysqldump` + Programador de Tareas.

### Pendientes que quedaron abiertos (por si se retoman)
- Módulo de disponibilidad presupuestaria: cargar reporte de saldos
  SICOIN por estructura/renglón/fuente, pantalla "¿puedo pagar esta
  factura?" con sugerencia de renglón, semáforo presupuesto + dinero real
  en banco. Pendiente que el usuario mande un reporte real de saldos para
  calibrar.
- Módulo de remuneraciones auto-generadas: el usuario mencionó que lo
  tiene pendiente de enviar el archivo/Colab de referencia.

---

## PARTE 2: Lo que necesito AHORA en esta sesión nueva

Tengo un **proyecto de la universidad**, un sistema de quejas para OTRA
municipalidad (asignación académica, no relacionado directamente con
sistema-granados salvo que ambos son de municipalidades guatemaltecas).

### Qué es este proyecto (TDR resumido)

**Sistema de Quejas, Reclamos, Denuncias y Sugerencias con Módulo de
Bitácoras.** Plataforma web para que la institución registre, gestione y
dé seguimiento a quejas/reclamos/denuncias/sugerencias, con trazabilidad
y auditoría completa.

**Módulos requeridos por el TDR:**
1. Registro de Quejas y Denuncias
2. Administración de Casos
3. Seguimiento
4. Notificaciones
5. Reportes
6. Administración (usuarios/roles)
7. **Bitácoras y Auditoría** (módulo con más detalle en el TDR, ver abajo)

**Módulo de Bitácoras y Auditoría, requisitos explícitos:**
- Registrar inicios y cierres de sesión
- Registrar creación, modificación y cierre de casos
- Registrar cambios de usuarios y permisos
- Registrar carga y descarga de documentos
- Guardar usuario, fecha, hora, IP y descripción del evento
- Generar reportes de auditoría
- **Impedir modificación de bitácoras** (append-only / inmutable, esto es
  un requisito de integridad, no solo funcional)

**Entregables del TDR:** análisis de requerimientos, diseño funcional,
código fuente, base de datos, manuales, capacitación, sistema
implementado, bitácora funcional.

**Plazo:** 90 días calendario. **Perfil requerido:** desarrollador con 3+
años de experiencia y proyectos similares comprobables. **Garantía:** 12
meses posteriores a la aceptación.

**Estado actual: TENGO QUE HACER LOS CASOS DE USO DESDE CERO.** Todavía no
hay código ni diseño funcional armado, solo el TDR. Cuando lleguemos a esa
parte (después o en paralelo a la migración de BD), voy a necesitar ayuda
construyendo los casos de uso para los 7 módulos de arriba, especialmente
detallando el de Bitácoras porque tiene requisitos de integridad/auditoría
que hay que modelar bien desde el diseño (ej. tabla de bitácora sin UPDATE
ni DELETE permitido a nivel de aplicación, posiblemente reforzado también
a nivel de base de datos con permisos o triggers).

### La migración de base de datos

**Necesito migrar su base de datos de MySQL a PostgreSQL.**

Cuando te pase el código de ese proyecto (o te diga su stack actual, si
ya tiene algo empezado en MySQL), ayudame a:
1. Identificar todo lo que depende de sintaxis específica de MySQL
   (tipos de datos, funciones, AUTO_INCREMENT vs SERIAL/IDENTITY,
   comportamiento de fechas, backticks vs comillas dobles, etc.)
2. Adaptar el `pom.xml`/`build.gradle` (driver JDBC, dialecto de
   Hibernate si usa JPA, o el driver correspondiente si usa otro stack)
3. Ajustar la cadena de conexión y las migraciones/schema
4. Verificar que no se rompa nada existente, con el mismo cuidado que
   tuvimos en sistema-granados: probar con datos reales cuando sea posible,
   diagnosticar antes de asumir, cambios quirúrgicos

Y en paralelo o después, ayudame a **construir los casos de uso desde
cero** para los 7 módulos del TDR (arriba), pensando especialmente bien
el de Bitácoras y Auditoría por su requisito de inmutabilidad.

**Importante**: este proyecto de quejas es aparte, de la universidad, NO
se mezcla con sistema-granados por ahora. Lo de fusionarlos es una idea
para el futuro, cuando ambos estén maduros, para armar un producto más
completo y venderlo. Por ahora tratalos como dos proyectos separados,
pero yo sí quiero que vos tengas memoria de ambos para cuando llegue el
momento de integrarlos.

Todavía no te he pasado el código del sistema de quejas (si ya tengo algo
empezado) ni el diseño funcional. Preguntame por eso primero, y por el
stack tecnológico que voy a usar (¿Java/Spring como en sistema-granados,
u otro?), antes de asumir nada.
