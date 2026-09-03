# Instalar sistema-granados como servidor en la Municipalidad

Guia paso a paso para dejar el sistema corriendo 24/7 en la maquina Windows
de la oficina, disponible para todas las computadoras conectadas por LAN.

## 1. IP fija de la maquina servidor

En el router (normalmente entras escribiendo `192.168.1.1` o `192.168.0.1`
en el navegador):

1. Busca la seccion "DHCP" o "Reserva de IP" / "Static Lease"
2. Ubica la maquina servidor en la lista de dispositivos conectados
3. Asignale una IP fija fuera del rango dinamico, ejemplo `192.168.1.50`
4. Reinicia esa maquina para que tome la IP

Para confirmar la IP: abre `cmd` y escribe `ipconfig`, busca "Direccion IPv4".

## 2. Ponerle contrasena a MySQL (IMPORTANTE)

Ahora mismo el sistema usa el usuario `root` de MySQL SIN contrasena. Eso
esta bien en una maquina de pruebas, pero NO en un servidor que va a estar
prendido en la red. Pasos:

1. Abre "MySQL Command Line Client" o una terminal y entra:
   ```
   mysql -u root
   ```
2. Cambia la contrasena (usa una fuerte, guardala en un lugar seguro):
   ```sql
   ALTER USER 'root'@'localhost' IDENTIFIED BY 'TU_CONTRASENA_AQUI';
   FLUSH PRIVILEGES;
   ```
3. Edita `application.properties` (dentro de la carpeta del programa) y
   pon esa misma contrasena en la linea:
   ```
   spring.datasource.password=TU_CONTRASENA_AQUI
   ```

Nota: MySQL puede seguir escuchando solo en `localhost` (127.0.0.1) sin
problema, porque el programa Java corre EN esa misma maquina y le habla a
MySQL localmente. Las demas computadoras de la oficina NUNCA hablan
directo con MySQL, solo con el programa Java via navegador. Eso ya es mas
seguro de por si.

## 3. Firewall de Windows: abrir el puerto 8085

Las demas maquinas necesitan poder llegar al puerto 8085 de la maquina
servidor. Abre "Firewall de Windows Defender con seguridad avanzada":

1. Reglas de entrada > Nueva regla
2. Tipo: Puerto
3. TCP, puerto especifico: 8085
4. Permitir la conexion
5. Marca las 3 casillas (Dominio, Privado, Publico) o solo Privado si tu
   red esta marcada asi
6. Nombrala "Sistema Granados"

## 4. Instalar el programa como servicio de Windows (arranca solo)

Asi evitas dejar una ventana de cmd abierta que alguien puede cerrar sin
querer. Usamos NSSM (Non-Sucking Service Manager), gratis:

1. Descarga NSSM de https://nssm.cc/download (version 2.24 o mas nueva)
2. Descomprime, copia `nssm.exe` (de la carpeta win64) a
   `C:\sistema-granados\nssm.exe`
3. Abre `cmd` como Administrador y ejecuta:
   ```
   C:\sistema-granados\nssm.exe install SistemaGranados
   ```
4. Se abre una ventana:
   - Path: la ruta a tu `java.exe` (normalmente
     `C:\Program Files\Java\jdk-17\bin\java.exe`)
   - Startup directory: `C:\sistema-granados`
   - Arguments: `-jar sistema-granados.jar`
     (usa el nombre real del .jar que te entregamos)
   - En la pestana "Details", ponle Display name "Sistema Granados"
5. Click "Install service"
6. Iniciar el servicio:
   ```
   net start SistemaGranados
   ```

Con esto: si la maquina se reinicia (por corte de luz, ejemplo), el
programa arranca solo. Si el programa se cae por algun error, Windows lo
puede reiniciar solo (configurable en las propiedades del servicio, pestana
"Recovery" en services.msc).

## 5. Como entran las demas maquinas

Desde cualquier computadora de la red, en el navegador:
```
http://192.168.1.50:8085
```
(cambia la IP por la que le asignaste en el paso 1)

Tip: crea un acceso directo en el escritorio de cada maquina apuntando a
esa direccion, para que el personal no tenga que escribirla cada vez.

## 6. Respaldo automatico de la base de datos (todos los dias)

Crea el archivo `C:\sistema-granados\respaldo_bd.bat` con este contenido
(ajusta la contrasena y la ruta si tu MySQL esta en otro lugar):

```bat
@echo off
set FECHA=%date:~-4%-%date:~3,2%-%date:~0,2%
set RUTA=C:\sistema-granados\respaldos
if not exist "%RUTA%" mkdir "%RUTA%"
"C:\Program Files\MySQL\MySQL Server 8.0\bin\mysqldump.exe" -u root -pTU_CONTRASENA_AQUI sistema_granados > "%RUTA%\respaldo_%FECHA%.sql"

REM Borra respaldos de mas de 30 dias para no llenar el disco
forfiles /p "%RUTA%" /m *.sql /d -30 /c "cmd /c del @path" 2>nul
```

Luego, en el "Programador de tareas" de Windows (`taskschd.msc`):

1. Crear tarea basica > nombre "Respaldo Sistema Granados"
2. Desencadenador: Diario, a una hora de baja actividad (ej. 11pm)
3. Accion: Iniciar un programa > selecciona `respaldo_bd.bat`
4. Guardar

Con esto, todas las noches queda un archivo `.sql` con copia completa de
la base de datos. Si algun dia algo sale mal, se puede restaurar con:
```
mysql -u root -p sistema_granados < respaldo_2026-08-15.sql
```

## 7. Por que agregar modulos nuevos NO va a romper lo que ya funciona

El sistema usa `spring.jpa.hibernate.ddl-auto=update` en
`application.properties`. Esto significa:

- Cuando agreguemos una tabla nueva (como paso con RRHH y Paquetes de
  facturas), MySQL la crea sola, SIN TOCAR las tablas que ya existen
- Los datos que ya tengas cargados (compras, permisos, facturas) se
  quedan intactos
- NUNCA vamos a cambiar esa configuracion a `create` o `create-drop`,
  porque esas SI borrarian todo

Lo unico que se debe evitar es que alguien renombre o borre una columna
de una tabla que ya tiene datos importantes sin avisar primero: eso lo
manejamos siempre con cuidado cuando toquemos el codigo, revisando el
impacto antes de aplicar cualquier cambio de estructura.

## 8. Actualizar el sistema en el futuro (cuando agreguemos algo nuevo)

Cuando tengamos una version nueva del programa (el .jar actualizado):

1. Detener el servicio: `net stop SistemaGranados`
2. Hacer un respaldo manual de la BD por si acaso (paso 6, se puede correr
   el .bat a mano)
3. Reemplazar el archivo `sistema-granados.jar` viejo por el nuevo en
   `C:\sistema-granados\`
4. Iniciar el servicio: `net start SistemaGranados`

El sistema va a arrancar, ver que faltan tablas o columnas nuevas, y las
va a crear solo (por el `ddl-auto=update`), sin tocar lo que ya existia.

## Resumen de rutas importantes

| Que | Donde |
|---|---|
| Programa | `C:\sistema-granados\sistema-granados.jar` |
| Configuracion | `C:\sistema-granados\application.properties` (o dentro del jar) |
| Respaldos | `C:\sistema-granados\respaldos\` |
| Acceso desde la red | `http://192.168.1.50:8085` (ajusta la IP) |
