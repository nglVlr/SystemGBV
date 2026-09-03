# Sistema Granados

Sistema web interno de la **Municipalidad de Granados, Baja Verapaz** con estos modulos:

| Modulo | Ruta | Quien lo usa |
|---|---|---|
| **DAFIM · Compras directas** | `/dafim/compras` | ADMIN_DAFIM |
| **DAFIM · Paquetes de facturas** | `/dafim/paquetes` | ADMIN_DAFIM |
| **DAFIM · Presupuesto** | `/dafim/presupuesto` | ADMIN_DAFIM |
| **Recursos Humanos** (en construccion) | `/rrhh` | ADMIN_RRHH |
| **Usuarios del sistema** | `/admin/usuarios` | SUPERADMIN |

El modulo de presupuesto importa dos reportes SICOIN GL, cada uno con su
propia carga **ACTIVA** (independientes; el boletin se actualiza a diario):

* **"Ejecucion de Egresos del Ejercicio" (R00814981)** — vigente, devengado,
  pagado y saldo por renglon/fuente. Se cruza con `historial_compras`.
* **"Boletin de Caja Consolidado Diario" (R00815627)** — dinero real
  (nuevo saldo) por cuenta monetaria. Se agrupa a la fuente
  `XX-XXXX-XXXX` sumando cuentas cuyo codigo es igual o empieza con
  `fuente-` (ej. `21-0101-0001` = funcionamiento + inversion). Las filas
  de cuenta fisica y el TOTAL del PDF no se usan (evitan doble conteo).

En **Fuentes**, detalle de fuente y **¿Con que pago esto?** se ve saldo
presupuestario y dinero en banco. Un monto alcanza si cubre **ambos**.
La disponibilidad mensual de compra directa (`/disponibilidad`) sigue
siendo saldo SICOIN menos pagos del mes; no es el boletin de caja.

El modulo de compras genera el **informe mensual de compras y contrataciones**
(Art. 10, Numeral 11, Ley de Acceso a la Informacion Publica) a partir de tres
archivos: el reporte de cheques del banco, el TXT de publicaciones de
Guatecompras y el PDF de SICOIN. Es el mismo motor que ya se usaba en Google
Colab, portado a Java con **paridad verificada linea por linea** (ver la
seccion "Como se verifico" al final).

---

## 1. Requisitos

* **Docker Desktop** corriendo (la app y PostgreSQL van en contenedores).
* Conexion a internet la primera vez (Docker descarga las imagenes y Maven
  las dependencias al construir).
* **Opcional, solo si desarrollas en el IDE:** Java 17 (JDK) e IntelliJ.

## 2. Puesta en marcha

### Opcion A — Todo con Docker (recomendado)

1. **Descomprime** el proyecto donde quieras, por ejemplo
   `C:\proyectos\sistema-granados`.
2. Desde la carpeta del proyecto (donde esta `docker-compose.yml`):

   ```
   docker compose up -d --build
   ```

   Eso construye la imagen de la app, crea `sistema-granados-db` (Postgres)
   y `sistema-granados-app` (Spring Boot). Hibernate crea las tablas al
   arrancar (`ddl-auto=update`).
3. Abre el navegador en **http://localhost:8085**

La primera vez tarda unos minutos (compila el jar). Las siguientes:
`docker compose up -d`.

### Opcion B — App en IntelliJ, solo la BD en Docker

1. Abre IntelliJ en la carpeta del proyecto.
2. Arranca solo Postgres: `docker compose up -d postgres`
3. Ejecuta `SistemaGranadosApplication` o `mvnw.cmd spring-boot:run`.

Si el puerto 8085 esta ocupado, cambialo en
`src/main/resources/application.properties` (`server.port`).

### Usuarios iniciales

| Usuario | Contrasena | Rol |
|---|---|---|
| `superadmin` | `pro9876` | SUPERADMIN |
| `admin_dafim` | `Dafim2026*` | ADMIN_DAFIM |
| `admin_rrhh` | `Rrhh2026*` | ADMIN_RRHH |

**Importante:** despues del primer ingreso, entra como `superadmin` a
`Usuarios del sistema` y cambia las tres contrasenas.

### Si cambias la contrasena de Postgres

Edita `application.properties` **y** `docker-compose.yml` (servicio `postgres`
y variables `SPRING_DATASOURCE_*` del servicio `app`) para que coincidan:

```
spring.datasource.username=postgres
spring.datasource.password=TU_CONTRASENA
```

---

## 3. Flujo mensual de DAFIM (el trabajo de cada mes)

1. Entra con `admin_dafim` y ve a **Procesar el mes**.
2. Elige mes y anio, y sube los tres archivos de siempre:
   * Reporte de **cheques** del banco (`.xls` o `.xlsx`)
   * **TXT** de publicaciones de Guatecompras
   * **PDF** de SICOIN (detalle de gastos por proveedor)
   * Opcional: planilla de **remuneraciones** para cruzar el personal 029
3. Pulsa **Procesar**. El sistema:
   * cruza cada cheque con SICOIN y Guatecompras,
   * completa NPG, contrato y cargo del personal 029 usando la base historica,
   * valida todo al centavo (acta de validacion),
   * compara contra el mes anterior (quien entro, quien salio, cambios de
     monto en 029),
   * y genera el **Excel legal** con el formato oficial de 15 columnas.
4. Revisa en pantalla las **alertas** (cheques sin cruce, personal nuevo,
   pagos sin NIT) y descarga el Excel para verlo.
5. Si todo esta bien: **Confirmar y guardar en la BD**. Hasta ese momento
   nada se guarda; si algo no cuadra puedes **Descartar** y volver a procesar.

Los archivos que subes quedan respaldados en `storage/uploads` y los Excel
generados en `storage/generados`, con fecha y hora en el nombre.

## 4. Presupuesto y dinero real (DAFIM)

Rutas bajo `/dafim/presupuesto` (rol ADMIN_DAFIM o SUPERADMIN).

1. **Cargar PDFs** (`/dafim/presupuesto/cargar`):
   * Ejecucion de egresos → reemplaza la carga de presupuesto activa.
   * Boletin de caja → reemplaza la carga de caja activa (no toca el
     presupuesto). Historial de ambos en `/dafim/presupuesto/cargas`.
2. **Fuentes**: vigente/devengado/pagado/saldo + columna **Dinero real Q**.
   Sin boletin, esa columna sale en `—`.
3. **Detalle de una fuente**: tarjeta dinero en banco, cuentas monetarias
   que suman a esa fuente, y con un monto: *presupuesto alcanza* /
   *banco alcanza*.
4. **¿Con que pago esto?**: mismo cruce por linea/fuente.

Stack: Spring Boot 3.2 / Java 17 / Thymeleaf / JPA / PostgreSQL 17
(`ddl-auto=update`, puerto app **8085**). Tablas de caja: `caja_cargas`,
`caja_cuentas`. Tablas de presupuesto: `presupuesto_cargas`,
`presupuesto_lineas`, `presupuesto_fuentes`.

## 5. Cargas historicas (una sola vez, para alimentar la BD)

* **Cargar NPGs**: sube los PDFs de confirmacion de publicacion de
  Guatecompras del personal 029 (varios a la vez o un ZIP). El sistema
  extrae NPG, NIT, nombre y contrato y actualiza la base.
* **Cargar machote**: para meses que ya se trabajaron a mano en Excel.
  Eliges mes y anio, subes el archivo, revisas la vista previa y confirmas:
  se suma al historial y alimenta contratos y proveedores. Si el renglon
  viene vacio, el sistema lo infiere por la descripcion.

## 6. Consultas

* **Buscar en la BD**: por NIT o por nombre (ignora tildes y mayusculas).
  Muestra contratos 029, proveedores y todos los pagos del historial.
* **Resumen**: totales por mes, monto acumulado y top 10 por monto.
* **Exportar la BD**: descarga un Excel con las tres tablas completas.

---

## 7. Estructura del proyecto

```
sistema-granados/
├── pom.xml                      dependencias (Spring Boot 3, POI, PDFBox)
├── Dockerfile                   imagen de la app (Java 17, multi-stage Maven)
├── docker-compose.yml           app :8085 + PostgreSQL 17 (puerto 5432 local)
├── database/                    scripts SQL de referencia (compras; presupuesto/caja los crea Hibernate)
├── storage/                     archivos subidos y Excel generados (se crea sola)
└── src/
    ├── main/java/com/granados/sistema/
    │   ├── SistemaGranadosApplication.java
    │   ├── config/              seguridad, almacenamiento, datos iniciales
    │   ├── usuarios/            entidades, servicio y pantalla de usuarios
    │   ├── web/                 dashboard y login
    │   ├── rrhh/                modulo RRHH (en construccion)
    │   └── dafim/
    │       ├── compras/         motor mensual, parsers, Excel legal
    │       └── presupuesto/     egresos + boletin de caja (parser, entidades, UI)
    ├── main/resources/
    │   ├── application.properties   JDBC localhost:5432/sistema_granados
    │   ├── templates/
    │   └── static/
    └── test/java/               JUnit (incluye ParserBoletinCaja + cruce por fuente)
        └── resources/parser/    PDFs reales (ejecucion, boletin-caja, factura, NPG)
```

## 8. Pruebas

```
mvnw test        (Windows: mvnw.cmd test)
```

Corre 35 pruebas de utilerias, motor, validador, comparador y parsers,
incluido un viaje redondo del Excel legal (se genera y se vuelve a leer).
El parser del boletin de caja se prueba con texto plano y con
`src/test/resources/parser/boletin-caja.pdf`. Hay dos pruebas desactivadas
con `@Disabled` que esperan PDFs reales de compras: si quieres activarlas,
coloca un PDF de SICOIN y uno de confirmacion NPG en
`src/test/resources/parser/` y quita la anotacion.

## 9. Subir a GitHub

```
git init
git add .
git commit -m "Sistema Granados: version inicial"
git branch -M main
git remote add origin https://github.com/TU_USUARIO/sistema-granados.git
git push -u origin main
```

El `.gitignore` ya excluye `target/`, `storage/` y archivos del IDE.

## 10. Problemas frecuentes

* **"Connection to localhost:5432 refused" al arrancar**: Docker Desktop
  no esta encendido o el contenedor esta abajo. Desde la carpeta del
  proyecto: `docker compose up -d`. Si desarrollas en IntelliJ, arranca
  solo la BD: `docker compose up -d postgres`. Si el servicio Windows
  `postgresql-x64-17` esta corriendo, para el servicio o cambia el puerto
  del compose: no pueden ocupar el 5432 a la vez.
* **Puerto 8085 ocupado**: no corras la app en IntelliJ y en Docker a la
  vez. Para o bien el run de IntelliJ, o bien `docker compose stop app`.
* **"password authentication failed"**: la clave de
  `application.properties` no coincide con `POSTGRES_PASSWORD` del
  `docker-compose.yml`. Si ya existia el volumen `pgdata` con otra clave,
  hay que recrearlo (`docker compose down -v` borra datos) o alinear la clave.
* **El navegador no abre nada**: revisa la consola de IntelliJ; el sistema
  imprime `Tomcat started on port 8085` cuando ya esta listo.
* **Subi un archivo equivocado**: el sistema avisa con un mensaje claro
  (por ejemplo "no se encontraron cheques con estado IMPRESO"); vuelve a
  procesar con el archivo correcto.
* **Un mes quedo mal guardado**: procesalo de nuevo y confirma; el sistema
  reemplaza los registros anteriores de ese mes y te avisa cuantos reemplazo.
* **Archivo muy grande**: el limite es 20 MB por archivo (configurable en
  `application.properties`).

## 11. Como se verifico este porteo

* **Paridad del motor**: se ejecuto el motor Python original y el motor Java
  con exactamente las mismas entradas (13 cheques que cubren todos los
  caminos: match directo, lineas multiples, fuzzy 029, keywords, NPG
  historico, anti duplicados, personal nuevo, sin NIT, machote, historial
  con renglon `29` sin ceros, etc.). Las salidas fueron **identicas**:
  filas, alertas, acta de validacion, cruce de remuneraciones, comparacion
  mensual y datos para la BD.
* **Parsers**: probados contra archivos reales `.xls` y textos con el mismo
  formato de los reportes (35 pruebas JUnit en verde).
* **SQL de compras**: los scripts de `database/` son de referencia
  (origen MySQL); en produccion el esquema lo mantiene Hibernate sobre
  PostgreSQL (`ddl-auto=update`).

## 12. Calibracion con datos reales (junio 2026)

El sistema se calibro contra el mes de junio 2026 completo, comparando la
salida del motor Java contra el Excel generado por el Colab original con
los mismos archivos (PDF SICOIN de 51 paginas, reporte de cheques .xls,
bloque TXT de Guatecompras):

- 543 filas generadas, identicas en numero al informe real.
- Total del mes Q2,951,154.59 cuadrado al centavo.
- Modalidades: 278 BAJA CUANTIA y 265 CASO DE EXCEPCION, igual al informe.
- Montos, renglones, NITs, proveedores, fechas y contratos: 0 diferencias.
- NPGs: los 275 publicados en el TXT del mes se asignan igual; los 265
  restantes salen de la BD historica (se llenan al cargar los machotes o
  los PDFs de NPG de meses anteriores).
- Descripciones: se corrigio el layout de PDFBox (la fuente de
  financiamiento venia pegada a la continuacion del texto). En 15 casos
  el Java quedo incluso MAS limpio que el Colab, que arrastraba texto
  duplicado de la transaccion vecina.
- Los PDFs reales quedaron como recursos de prueba en
  `src/test/resources/parser/` y los 36 tests JUnit pasan.

## 13. NPGs: carga por PDF, ZIP o manual

En `/dafim/compras/cargar-npgs` se puede:

- Subir VARIOS PDFs de confirmacion a la vez, o un solo ZIP con todos.
- Ingresar un NPG a mano (NPG, NIT, nombre, contrato opcional) cuando no
  se tiene el PDF o viene en un formato raro.
- El lector de PDFs ahora tolera formatos alternativos de la constancia:
  si no encuentra "Publicacion (NPG)" usa el E-numero mas repetido del
  documento, y reconoce variantes de "NIT" y "Descripcion".

## 14. Paquetes de facturas SAT

En `/dafim/paquetes` se procesa el envio mensual de la oficina:

1. Se sube el Excel de paquetes (CADA HOJA es un paquete, con columnas de
   concepto y monto; la ultima fila con monto y sin concepto es el total)
   junto con TODAS las facturas FEL en PDF, sueltas o en un solo ZIP, sin
   importar el orden.
2. El sistema lee cada factura (autorizacion, serie, DTE, NIT y nombre del
   emisor, fecha, monto y descripcion) y casa cada linea de cada paquete
   con UNA factura: monto exacto al centavo y la descripcion mas parecida
   (tolera tildes, typos y espacios dobles).
3. Cuando varias facturas traen la misma descripcion y monto, se asignan
   una a una en el orden de los paquetes: el numero de autorizacion es
   UNICO en la base de datos, asi que una factura jamas se repite, ni
   siquiera contra meses anteriores ya guardados.
4. Todo queda en la BD por mes (paquetes, lineas, facturas y sus PDFs) y
   cada paquete se imprime como UN SOLO PDF con las facturas en el orden
   del Excel: se manda a imprimir paquete por paquete y en fisico solo se
   engrapan.
5. Las lineas que quedaron pendientes se pueden resolver a mano con el
   selector de facturas libres del mes (solo permite montos iguales).

Calibrado con el Excel real de julio 2026 (8 paquetes, 244 lineas,
Q1,981,000, todos los totales cuadran al centavo) y con una factura FEL
real de la SAT que quedo como prueba de regresion en
`src/test/resources/parser/factura.pdf`.

## 15. Instalar como servidor local en la Municipalidad

Guia completa paso a paso (IP fija, firewall, servicio de Windows,
respaldos) en `servidor/LEEME_SERVIDOR.md`. La BD de esta version es
**PostgreSQL en Docker**, no MySQL/Wamp: en el servidor corre
`docker compose up -d --build` (app + BD).

Resumen: `docker compose up -d --build` deja app y Postgres corriendo.
Las demas maquinas de la red LAN entran por navegador a
`http://IP_DEL_SERVIDOR:8085`. Como `ddl-auto=update` esta activo,
agregar modulos nuevos en el futuro nunca borra los datos existentes.

Si prefieres el jar a mano: `mvn package` genera un .jar ejecutable.
Se copia a la maquina servidor y se corre con
`java -jar sistema-granados.jar` (o como servicio de Windows con NSSM).

---

Municipalidad de Granados, Baja Verapaz · DAFIM
