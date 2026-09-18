/* ============================================================================
   SISTEMA GRANADOS · Distribuir pagos (asistente con IA)
   ----------------------------------------------------------------------------
   Hoja de calculo embebida (jspreadsheet CE, vendored) sobre las facturas del
   mes. Flujo: Clasificar con IA -> revisar/ajustar grupo-renglon-fuente ->
   Aplicar y crear apartados. Nada toca la base hasta el POST de aplicar.

   Contrato fijo con el backend (no cambiar sin avisar):
     POST .../distribuir/clasificar  {mes, anio}  -> {filas: [...], ia: bool}
     POST .../distribuir/aplicar     {filas: [...]} -> {creados: n, rechazados: [{fila, motivo}]}
     Fila: facturaId, descripcion, emisor, monto, grupo, renglon, fuente,
           lineaId, explicacion, saldoSuficiente
   ========================================================================== */
(function () {
  'use strict';

  var CFG = window.DISTRIBUIR;
  var contenedor = document.getElementById('hojaDistribuir');
  if (!CFG || !contenedor) return;

  /* ------------------------------------------------------------------ *
   * Utilidades                                                         *
   * ------------------------------------------------------------------ */

  var Q = new Intl.NumberFormat('es-GT', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  });

  function dinero(n) {
    var v = parseFloat(n);
    return 'Q ' + (isFinite(v) ? Q.format(v) : '0.00');
  }

  function csrf() {
    var token = document.querySelector('meta[name="_csrf"]');
    var header = document.querySelector('meta[name="_csrf_header"]');
    return {
      header: header ? header.getAttribute('content') : null,
      token: token ? token.getAttribute('content') : null
    };
  }

  function postJson(url, cuerpo, hecho, fallo) {
    var c = csrf();
    var cabeceras = { 'Content-Type': 'application/json' };
    if (c.header && c.token) cabeceras[c.header] = c.token;
    fetch(url, {
      method: 'POST',
      headers: cabeceras,
      body: JSON.stringify(cuerpo)
    }).then(function (resp) {
      if (resp.status === 403) {
        fallo('Sesión vencida o sin permiso. Recarga la página e intenta de nuevo.');
        return;
      }
      if (!resp.ok) {
        fallo('El servidor respondió con error (' + resp.status + ').');
        return;
      }
      resp.json().then(hecho, function () {
        fallo('La respuesta del servidor no se pudo leer.');
      });
    }).catch(function () {
      fallo('Sin conexión con el servidor. Revisa tu red e intenta de nuevo.');
    });
  }

  /* Avisos con el mismo idioma visual que app.js (los dinamicos traen su
     propio boton de cierre porque app.js solo enlaza los del primer render). */
  function aviso(tipo, html, auto) {
    var caja = document.createElement('div');
    caja.className = 'aviso aviso-' + tipo;
    caja.setAttribute('role', tipo === 'error' ? 'alert' : 'status');

    var ico = document.createElement('i');
    ico.className = 'bi ' + (tipo === 'exito' ? 'bi-check-circle'
      : (tipo === 'error' ? 'bi-exclamation-triangle' : 'bi-exclamation-circle'));
    caja.appendChild(ico);

    var cuerpo = document.createElement('div');
    cuerpo.innerHTML = html;
    caja.appendChild(cuerpo);

    var cerrar = document.createElement('button');
    cerrar.type = 'button';
    cerrar.className = 'cerrar-aviso';
    cerrar.setAttribute('aria-label', 'Cerrar');
    cerrar.innerHTML = '<i class="bi bi-x"></i>';
    cerrar.addEventListener('click', function () { quitar(caja); });
    caja.appendChild(cerrar);

    if (auto) caja._t = setTimeout(function () { quitar(caja); }, 8000);

    var ancla = document.getElementById('resultadoAplicar') || contenedor.parentNode;
    ancla.parentNode.insertBefore(caja, ancla);
    return caja;
  }

  function quitar(caja) {
    if (!caja || caja.getAttribute('data-cerrando') === '1') return;
    caja.setAttribute('data-cerrando', '1');
    if (caja._t) clearTimeout(caja._t);
    caja.style.transition = 'opacity .4s ease';
    caja.style.opacity = '0';
    setTimeout(function () { caja.remove(); }, 450);
  }

  /* Dialogo de confirmacion: mismo marcado y clases que el de app.js. */
  function confirmar(texto, hecho) {
    var previo = document.getElementById('dialogoConfirmar');
    if (previo) previo.remove();

    var velo = document.createElement('div');
    velo.id = 'dialogoConfirmar';
    velo.className = 'dialogo-confirmar-velo';
    velo.setAttribute('role', 'dialog');
    velo.setAttribute('aria-modal', 'true');
    velo.innerHTML =
      '<div class="dialogo-confirmar">' +
      '<h2 class="dialogo-confirmar-titulo">Confirmar</h2>' +
      '<p class="dialogo-confirmar-texto"></p>' +
      '<div class="acciones-fila dialogo-confirmar-acciones">' +
      '<button type="button" class="btn-neutro" data-rol="cancelar">Cancelar</button>' +
      '<button type="button" class="btn-jade" data-rol="aceptar">Confirmar</button>' +
      '</div></div>';
    velo.querySelector('.dialogo-confirmar-texto').textContent = texto;
    document.body.appendChild(velo);

    var cerrado = false;
    function terminar(ok) {
      if (cerrado) return;
      cerrado = true;
      velo.remove();
      hecho(ok);
    }
    velo.querySelector('[data-rol="cancelar"]').addEventListener('click', function () { terminar(false); });
    velo.querySelector('[data-rol="aceptar"]').addEventListener('click', function () { terminar(true); });
    velo.addEventListener('keydown', function (ev) {
      if (ev.key === 'Escape') {
        ev.preventDefault();
        ev.stopPropagation();
        terminar(false);
      }
    });
    velo.querySelector('[data-rol="aceptar"]').focus();
  }

  function escapeHtml(s) {
    return String(s == null ? '' : s)
      .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
  }

  /* ------------------------------------------------------------------ *
   * La hoja                                                            *
   * ------------------------------------------------------------------ */

  // Columnas: 0:# 1:Descripcion 2:Emisor 3:Monto 4:Grupo 5:Renglon 6:Fuente
  //           7:Saldo 8:Explicacion | ocultas -> 9:facturaId 10:lineaId 11:saldoSuficiente
  var COL = {
    NUM: 0, DESC: 1, EMISOR: 2, MONTO: 3, GRUPO: 4, RENGLON: 5, FUENTE: 6,
    SALDO: 7, EXPLICACION: 8, FACTURA_ID: 9, LINEA_ID: 10, SALDO_OK: 11
  };
  var VISIBLES = [0, 1, 2, 3, 4, 5, 6, 7, 8];

  function letra(x) {
    // A..L (12 columnas); suficiente para esta hoja
    var s = '';
    x = parseInt(x, 10);
    do {
      s = String.fromCharCode(65 + (x % 26)) + s;
      x = Math.floor(x / 26) - 1;
    } while (x >= 0);
    return s;
  }

  function fuenteNombre(codigo) {
    var f = (CFG.fuentes || []).find(function (x) { return String(x.codigo) === String(codigo); });
    return f ? f.nombre : '';
  }

  function filaAArreglo(f, i) {
    var ok = f.saldoSuficiente === true || f.saldoSuficiente === 'true';
    return [
      i + 1,
      f.descripcion || '',
      f.emisor || '',
      f.monto != null ? f.monto : '',
      f.grupo || '',
      f.renglon != null ? String(f.renglon) : '',
      f.fuente != null ? String(f.fuente) : '',
      f.grupo ? (ok ? '✓' : '✗') : '',
      f.explicacion || '',
      f.facturaId != null ? f.facturaId : '',
      f.lineaId != null ? f.lineaId : '',
      f.grupo ? ok : ''
    ];
  }

  function arregloAFila(a) {
    return {
      facturaId: a[COL.FACTURA_ID] === '' ? null : a[COL.FACTURA_ID],
      descripcion: a[COL.DESC],
      emisor: a[COL.EMISOR],
      monto: parseFloat(a[COL.MONTO]) || 0,
      grupo: a[COL.GRUPO] || null,
      renglon: a[COL.RENGLON] === '' ? null : a[COL.RENGLON],
      fuente: a[COL.FUENTE] === '' ? null : a[COL.FUENTE],
      lineaId: a[COL.LINEA_ID] === '' ? null : a[COL.LINEA_ID],
      explicacion: a[COL.EXPLICACION] || null,
      saldoSuficiente: a[COL.SALDO_OK] === true || a[COL.SALDO_OK] === 'true'
    };
  }

  var hoja = null;
  var clasificando = false;
  var aplicando = false;

  function datosIniciales() {
    return (CFG.filas || []).map(filaAArreglo);
  }

  function opcionesRenglon() {
    return (CFG.renglones || []).map(function (r) {
      return { id: String(r.codigo), name: r.codigo + ' · ' + (r.descripcion || '') };
    });
  }

  function opcionesFuente() {
    return (CFG.fuentes || []).map(function (f) {
      return { id: String(f.codigo), name: f.codigo + ' · ' + (f.nombre || '') };
    });
  }

  function pintarSaldos() {
    if (!hoja) return;
    var data = hoja.getData();
    for (var y = 0; y < data.length; y++) {
      var ok = data[y][COL.SALDO_OK] === true || data[y][COL.SALDO_OK] === 'true';
      var clasificada = !!(data[y][COL.GRUPO] || data[y][COL.RENGLON] || data[y][COL.FUENTE]);
      for (var k = 0; k < VISIBLES.length; k++) {
        var celda = letra(VISIBLES[k]) + (y + 1);
        if (clasificada && !ok) {
          hoja.setStyle(celda, 'background-color', 'var(--rojo-tenue)', true);
        } else {
          hoja.setStyle(celda, 'background-color', '', true);
        }
      }
      var celdaSaldo = letra(COL.SALDO) + (y + 1);
      hoja.setStyle(celdaSaldo, 'color', !clasificada ? '' : (ok ? 'var(--jade-oscuro)' : 'var(--rojo)'), true);
      hoja.setStyle(celdaSaldo, 'font-weight', clasificada ? '700' : '', true);
    }
  }

  function totales() {
    var data = hoja ? hoja.getData() : [];
    var porGrupo = {};
    var porFuente = {};
    var total = 0;
    var listas = 0;
    for (var y = 0; y < data.length; y++) {
      if (data[y][COL.FACTURA_ID] === '' || data[y][COL.FACTURA_ID] == null) continue;
      var monto = parseFloat(data[y][COL.MONTO]) || 0;
      var g = data[y][COL.GRUPO] || 'SIN CLASIFICAR';
      var f = data[y][COL.FUENTE] || 'SIN FUENTE';
      porGrupo[g] = (porGrupo[g] || 0) + monto;
      porFuente[f] = (porFuente[f] || 0) + monto;
      total += monto;
      if (data[y][COL.SALDO_OK] === true || data[y][COL.SALDO_OK] === 'true') listas++;
    }
    return { porGrupo: porGrupo, porFuente: porFuente, total: total, listas: listas, filas: data.length };
  }

  function pintarTotales() {
    var t = totales();
    var tg = document.getElementById('totalesGrupo');
    var tf = document.getElementById('totalesFuente');
    if (tg) {
      tg.innerHTML = '';
      Object.keys(t.porGrupo).sort(function (a, b) { return t.porGrupo[b] - t.porGrupo[a]; })
        .forEach(function (g) {
          var n = contarPor(COL.GRUPO, g);
          tg.appendChild(filaTotal(escapeHtml(g), n, t.porGrupo[g], g === 'SIN CLASIFICAR'));
        });
      tg.appendChild(filaTotal('TOTAL', t.filas, t.total, false, true));
    }
    if (tf) {
      tf.innerHTML = '';
      Object.keys(t.porFuente).sort(function (a, b) { return t.porFuente[b] - t.porFuente[a]; })
        .forEach(function (f) {
          var nombre = fuenteNombre(f);
          var n = contarPor(COL.FUENTE, f);
          tf.appendChild(filaTotal(
            escapeHtml(f) + (nombre ? ' <span class="texto-suave">· ' + escapeHtml(nombre) + '</span>' : ''),
            n, t.porFuente[f], f === 'SIN FUENTE'));
        });
      tf.appendChild(filaTotal('TOTAL', t.filas, t.total, false, true));
    }
    var resumen = document.getElementById('resumenAplicar');
    if (resumen) {
      resumen.textContent = t.filas === 0
        ? 'Sin facturas pendientes'
        : t.filas + ' factura(s) · ' + dinero(t.total) + ' · ' + t.listas + ' con saldo validado';
    }
    var conteo = document.getElementById('conteoFilas');
    if (conteo) conteo.textContent = t.filas;

    // Stats del hero (visibles sin bajar)
    var clasificadas = 0;
    var data2 = hoja ? hoja.getData() : [];
    for (var y2 = 0; y2 < data2.length; y2++) {
      if (data2[y2][COL.FACTURA_ID] === '' || data2[y2][COL.FACTURA_ID] == null) continue;
      if (data2[y2][COL.GRUPO] && data2[y2][COL.RENGLON] && data2[y2][COL.FUENTE]) clasificadas++;
    }
    ponerTexto('statFacturas', String(t.filas));
    ponerTexto('statMonto', dinero(t.total));
    ponerTexto('statClasificadas', String(clasificadas));
    ponerTexto('statSaldo', String(t.listas));
  }

  function ponerTexto(id, txt) {
    var el = document.getElementById(id);
    if (el) el.textContent = txt;
  }

  /* Pasos del asistente: marca el activo y los ya listos. */
  function marcarPaso(activo) {
    for (var n = 1; n <= 3; n++) {
      var paso = document.getElementById('paso' + n);
      if (!paso) continue;
      paso.classList.toggle('activo', n === activo);
      paso.classList.toggle('listo', n < activo);
    }
  }

  function contarPor(col, valor) {
    var data = hoja ? hoja.getData() : [];
    var n = 0;
    for (var y = 0; y < data.length; y++) {
      if (data[y][COL.FACTURA_ID] === '' || data[y][COL.FACTURA_ID] == null) continue;
      var v = data[y][col] || (col === COL.GRUPO ? 'SIN CLASIFICAR' : 'SIN FUENTE');
      if (String(v) === String(valor)) n++;
    }
    return n;
  }

  function filaTotal(nombreHtml, n, total, pendiente, esTotal) {
    var tr = document.createElement('tr');
    var td1 = document.createElement('td');
    td1.innerHTML = esTotal ? '<strong>' + nombreHtml + '</strong>' : nombreHtml;
    if (pendiente) td1.innerHTML = '<span class="insignia insignia-ambar">' + nombreHtml + '</span>';
    var td2 = document.createElement('td');
    td2.className = 'centrado numeros';
    td2.textContent = n;
    var td3 = document.createElement('td');
    td3.className = 'monto numeros';
    td3.textContent = dinero(total);
    if (esTotal) td3.innerHTML = '<strong>' + dinero(total) + '</strong>';
    tr.appendChild(td1);
    tr.appendChild(td2);
    tr.appendChild(td3);
    return tr;
  }

  function sincronizarVisibilidad() {
    var n = hoja ? hoja.getData().length : 0;
    var vacia = document.getElementById('hojaVacia');
    var totalesZona = document.getElementById('zonaTotales');
    var barra = document.getElementById('barraAplicar');
    if (vacia) vacia.hidden = n > 0;
    if (totalesZona) totalesZona.hidden = n === 0;
    if (barra) barra.hidden = n === 0;
    contenedor.style.display = n === 0 ? 'none' : '';
  }

  function alCambiar(instance, cell, x, y) {
    x = parseInt(x, 10);
    y = parseInt(y, 10);
    if (x === COL.GRUPO || x === COL.RENGLON || x === COL.FUENTE) {
      // La validacion de saldo ya no corresponde a la linea elegida:
      // se marca como pendiente y el backend revalida al aplicar.
      hoja.setValue(letra(COL.SALDO) + (y + 1), '?', true);
      hoja.setValue(letra(COL.SALDO_OK) + (y + 1), false, true);
      hoja.setStyle(letra(COL.SALDO) + (y + 1), 'color', 'var(--ambar)', true);
      for (var k = 0; k < VISIBLES.length; k++) {
        hoja.setStyle(letra(VISIBLES[k]) + (y + 1), 'background-color', '', true);
      }
    }
    pintarTotales();
  }

  function iniciarHoja() {
    if (typeof window.jspreadsheet !== 'function') {
      aviso('error', 'No se pudo cargar la hoja de cálculo (librería no disponible). Recarga la página.', false);
      return;
    }
    var creada = window.jspreadsheet(contenedor, {
      data: datosIniciales(),
      columns: [
        { title: '#', type: 'numeric', readOnly: true, width: 44, align: 'center' },
        { title: 'Descripción', type: 'text', readOnly: true, width: 240 },
        { title: 'Emisor', type: 'text', readOnly: true, width: 140 },
        { title: 'Monto', type: 'numeric', readOnly: true, width: 100, mask: 'Q #,##0.00', align: 'right' },
        { title: 'Grupo', type: 'dropdown', width: 135, source: CFG.grupos || [] },
        { title: 'Renglón', type: 'dropdown', width: 170, source: opcionesRenglon(), autocomplete: true },
        { title: 'Fuente', type: 'dropdown', width: 180, source: opcionesFuente(), autocomplete: true },
        { title: 'Saldo', type: 'text', readOnly: true, width: 54, align: 'center' },
        { title: 'Explicación IA', type: 'text', readOnly: true, width: 190 },
        { title: 'facturaId', type: 'hidden' },
        { title: 'lineaId', type: 'hidden' },
        { title: 'saldoSuficiente', type: 'hidden' }
      ],
      tableOverflow: true,
      tableHeight: '480px',
      defaultColAlign: 'left',
      columnSorting: false,
      rowDrag: false,
      columnDrag: false,
      allowInsertRow: false,
      allowDeleteRow: false,
      allowInsertColumn: false,
      allowDeleteColumn: false,
      allowRenameColumn: false,
      allowComments: false,
      onchange: alCambiar
    });
    hoja = Array.isArray(creada) ? creada[0] : creada;
    pintarSaldos();
    pintarTotales();
    sincronizarVisibilidad();
  }

  /* ------------------------------------------------------------------ *
   * Clasificar con IA                                                  *
   * ------------------------------------------------------------------ */

  function botonOcupado(btn, texto) {
    btn.disabled = true;
    btn.setAttribute('aria-busy', 'true');
    btn.innerHTML = '<span class="spinner-jade"></span> ' + texto;
  }

  function botonLibre(btn, html) {
    btn.disabled = false;
    btn.removeAttribute('aria-busy');
    btn.innerHTML = html;
  }

  function clasificar() {
    if (clasificando || aplicando || !hoja) return;
    if (CFG.mes == null || CFG.anio == null) {
      aviso('ambar', 'Elige primero un mes con facturas.', true);
      return;
    }
    clasificando = true;
    var btn = document.getElementById('btnClasificar');
    var btnAplicar = document.getElementById('btnAplicar');
    var estado = document.getElementById('estadoClasificar');
    botonOcupado(btn, 'Clasificando...');
    if (btnAplicar) btnAplicar.disabled = true;
    if (estado) estado.textContent = 'Consultando al clasificador, espera unos segundos...';

    postJson(CFG.urlClasificar, { mes: CFG.mes, anio: CFG.anio }, function (resp) {
      var filas = (resp && resp.filas) || [];
      hoja.setData(filas.map(filaAArreglo));
      pintarSaldos();
      pintarTotales();
      sincronizarVisibilidad();
      if (resp && resp.ia === true) {
        aviso('exito', 'Listo: <strong>' + filas.length + '</strong> factura(s) clasificadas con IA (Gemini). Revisa grupo, renglón y fuente antes de aplicar.', true);
      } else {
        aviso('ambar', 'Listo: <strong>' + filas.length + '</strong> factura(s) clasificadas con <strong>reglas locales</strong> (falta GEMINI_API_KEY en el servidor). Revisa bien cada fila antes de aplicar.', false);
      }
      if (estado) estado.textContent = 'Clasificación lista. Puedes ajustar cualquier fila.';
      clasificando = false;
      marcarPaso(2);
      botonLibre(btn, '<i class="bi bi-stars"></i> Clasificar con IA');
      if (btnAplicar) btnAplicar.disabled = false;
    }, function (msg) {
      aviso('error', escapeHtml(msg), false);
      if (estado) estado.textContent = 'No se pudo clasificar. Intenta de nuevo.';
      clasificando = false;
      botonLibre(btn, '<i class="bi bi-stars"></i> Clasificar con IA');
      if (btnAplicar) btnAplicar.disabled = false;
    });
  }

  /* ------------------------------------------------------------------ *
   * Aplicar y crear apartados                                          *
   * ------------------------------------------------------------------ */

  function filasParaAplicar() {
    var data = hoja ? hoja.getData() : [];
    var filas = [];
    for (var y = 0; y < data.length; y++) {
      if (data[y][COL.FACTURA_ID] === '' || data[y][COL.FACTURA_ID] == null) continue;
      filas.push(arregloAFila(data[y]));
    }
    return filas;
  }

  function aplicar() {
    if (clasificando || aplicando || !hoja) return;
    var filas = filasParaAplicar();
    if (filas.length === 0) {
      aviso('ambar', 'No hay facturas en la hoja. Clasifica primero.', true);
      return;
    }
    var sinClasificar = filas.filter(function (f) { return !f.grupo || !f.renglon || !f.fuente; }).length;
    var t = totales();
    var texto = '¿Crear ' + filas.length + ' apartado(s) por ' + dinero(t.total) + '? '
      + 'El presupuesto quedará reservado y cada factura quedará ligada a su apartado.';
    if (sinClasificar > 0) {
      texto += ' Ojo: ' + sinClasificar + ' fila(s) no tienen grupo, renglón y fuente completos; el servidor las rechazará.';
    }

    confirmar(texto, function (ok) {
      if (!ok) return;
      aplicando = true;
      var btn = document.getElementById('btnAplicar');
      var btnClasificar = document.getElementById('btnClasificar');
      botonOcupado(btn, 'Creando apartados...');
      if (btnClasificar) btnClasificar.disabled = true;

      postJson(CFG.urlAplicar, { filas: filas }, function (resp) {
        var creados = resp && resp.creados != null ? resp.creados : 0;
        var rechazados = (resp && resp.rechazados) || [];
        var salida = document.getElementById('resultadoAplicar');
        if (salida) salida.innerHTML = '';

        if (creados > 0) {
          aviso('exito', 'Se crearon <strong>' + creados + '</strong> apartado(s). El presupuesto ya quedó reservado; puedes verlos en <strong>Apartados</strong>.', false);
        }
        if (rechazados.length > 0) {
          var lista = rechazados.map(function (r) {
            var filaTxt = r.fila != null ? ('#' + escapeHtml(r.fila)) : '';
            return '<div class="linea linea-rev">' + filaTxt + ' ' + escapeHtml(r.motivo || 'Rechazada') + '</div>';
          }).join('');
          aviso('ambar', '<strong>' + rechazados.length + '</strong> fila(s) no se aplicaron:'
            + '<div class="acta mt-8">' + lista + '</div>', false);
        }
        if (creados === 0 && rechazados.length === 0) {
          aviso('ambar', 'El servidor no creó ningún apartado y no reportó rechazos. Recarga la página y revisa.', false);
        }

        quitarAplicadas(rechazados);
        pintarSaldos();
        pintarTotales();
        sincronizarVisibilidad();
        if (creados > 0) marcarPaso(3);

        aplicando = false;
        botonLibre(btn, '<i class="bi bi-check2-circle"></i> Aplicar y crear apartados');
        if (btnClasificar) btnClasificar.disabled = false;
      }, function (msg) {
        aviso('error', escapeHtml(msg), false);
        aplicando = false;
        botonLibre(btn, '<i class="bi bi-check2-circle"></i> Aplicar y crear apartados');
        if (btnClasificar) btnClasificar.disabled = false;
      });
    });
  }

  /* Deja en la hoja solo las filas rechazadas (lo creado ya es apartado).
     `fila` se interpreta como el numero visible de la columna #; si no
     coincide con ninguna, se intenta como facturaId. */
  function quitarAplicadas(rechazados) {
    var data = hoja.getData();
    if (!rechazados || rechazados.length === 0) {
      hoja.setData([]);
      return;
    }
    var quedan = {};
    rechazados.forEach(function (r) {
      if (r.fila != null) quedan[String(r.fila)] = true;
    });
    var conservar = data.filter(function (fila) {
      var num = String(fila[COL.NUM]);
      var fid = String(fila[COL.FACTURA_ID]);
      return quedan[num] || quedan[fid];
    });
    // Renumerar la columna # para que siga el orden visible
    conservar = conservar.map(function (fila, i) {
      fila[COL.NUM] = i + 1;
      return fila;
    });
    hoja.setData(conservar);
  }

  /* ------------------------------------------------------------------ *
   * Selector de mes (combo mes-anio -> parametros mes/anio del GET)    *
   * ------------------------------------------------------------------ */

  function iniciarSelectorMes() {
    var combo = document.getElementById('fMesAnio');
    var form = document.getElementById('formMes');
    var fMes = document.getElementById('fMes');
    var fAnio = document.getElementById('fAnio');
    if (!combo || !form || !fMes || !fAnio) return;
    combo.addEventListener('change', function () {
      var op = combo.options[combo.selectedIndex];
      if (!op) return;
      fMes.value = op.getAttribute('data-mes') || '';
      fAnio.value = op.getAttribute('data-anio') || '';
      form.submit();
    });
  }

  /* ------------------------------------------------------------------ */

  var btnClasificar = document.getElementById('btnClasificar');
  if (btnClasificar) btnClasificar.addEventListener('click', clasificar);
  var btnAplicar = document.getElementById('btnAplicar');
  if (btnAplicar) btnAplicar.addEventListener('click', aplicar);

  iniciarSelectorMes();
  iniciarHoja();
  marcarPaso(1);
})();
