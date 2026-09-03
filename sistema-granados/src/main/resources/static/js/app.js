/* ============================================================================
   SISTEMA GRANADOS · Interacciones del armazon
   ----------------------------------------------------------------------------
   1. Comportamiento original: menu lateral, confirmaciones, nombres de
      archivo, avisos automaticos y estados de "procesando".
   2. Interacciones de la edicion "Noche en Verapaz": aparicion al hacer
      scroll, tarjetas con inclinacion 3D y brillo que sigue al cursor, y
      contadores animados en las estadisticas.
   ========================================================================== */
(function () {
  'use strict';

  var REDUCIR = window.matchMedia
    && window.matchMedia('(prefers-reduced-motion: reduce)').matches;

  /* ------------------------------------------------------------------ *
   * 1 · Comportamiento original                                        *
   * ------------------------------------------------------------------ */

  var CLAVE_MENU = 'granados.menuPlegado';
  var CLAVE_TEMA = 'granados.tema';

  function temaActual() {
    return document.documentElement.getAttribute('data-tema') === 'claro' ? 'claro' : 'oscuro';
  }

  function aplicarTema(t) {
    var tema = t === 'claro' ? 'claro' : 'oscuro';
    document.documentElement.setAttribute('data-tema', tema);
    document.documentElement.setAttribute('data-bs-theme', tema === 'claro' ? 'light' : 'dark');
    try { localStorage.setItem(CLAVE_TEMA, tema); }
    catch (e) { /* sin persistencia */ }
    var btn = document.getElementById('botonTema');
    if (btn) {
      var aClaro = tema !== 'claro';
      btn.setAttribute('aria-label', aClaro ? 'Cambiar a modo claro' : 'Cambiar a modo oscuro');
      btn.setAttribute('title', aClaro ? 'Modo claro' : 'Modo oscuro');
      btn.setAttribute('aria-pressed', tema === 'claro' ? 'true' : 'false');
    }
    try {
      document.documentElement.dispatchEvent(new CustomEvent('granados:tema', { detail: tema }));
    } catch (e) { /* CustomEvent no disponible */ }
  }

  aplicarTema(temaActual());
  var botonTema = document.getElementById('botonTema');
  if (botonTema) {
    botonTema.addEventListener('click', function () {
      aplicarTema(temaActual() === 'claro' ? 'oscuro' : 'claro');
    });
  }

  // Restaurar preferencia de menu plegado (solo escritorio)
  try {
    if (localStorage.getItem(CLAVE_MENU) === '1' && window.innerWidth >= 992) {
      document.body.classList.add('menu-plegado');
    }
  } catch (e) { /* almacenamiento no disponible: continuar sin persistencia */ }

  var boton = document.getElementById('botonMenu');
  function syncAriaMenu() {
    if (!boton) return;
    var abierto = document.body.classList.contains('menu-abierto');
    boton.setAttribute('aria-expanded', abierto ? 'true' : 'false');
  }
  function cerrarMenuMovil() {
    document.body.classList.remove('menu-abierto');
    syncAriaMenu();
  }
  if (boton) {
    boton.addEventListener('click', function () {
      if (window.innerWidth < 992) {
        document.body.classList.toggle('menu-abierto');
        syncAriaMenu();
      } else {
        var plegado = document.body.classList.toggle('menu-plegado');
        try { localStorage.setItem(CLAVE_MENU, plegado ? '1' : '0'); }
        catch (e) { /* sin persistencia */ }
      }
    });
  }

  var veloMenu = document.getElementById('veloMenu');
  if (veloMenu) {
    veloMenu.addEventListener('click', cerrarMenuMovil);
  }

  // Cerrar el menu movil al tocar el fondo oscuro o un enlace del menu
  document.addEventListener('click', function (ev) {
    if (document.body.classList.contains('menu-abierto')
        && !ev.target.closest('.menu-lateral')
        && !ev.target.closest('#botonMenu')) {
      cerrarMenuMovil();
    }
  });
  document.querySelectorAll('.menu-lateral a.menu-item').forEach(function (a) {
    a.addEventListener('click', function () {
      if (window.innerWidth < 992) cerrarMenuMovil();
    });
  });
  window.addEventListener('resize', function () {
    if (window.innerWidth >= 992) cerrarMenuMovil();
  });
  document.addEventListener('keydown', function (ev) {
    if (ev.key !== 'Escape') return;
    if (document.getElementById('dialogoConfirmar')) return;
    var campo = document.activeElement;
    if (esCampoBusquedaFiltro(campo) && campo.value) {
      ev.preventDefault();
      campo.value = '';
      try { campo.dispatchEvent(new Event('input', { bubbles: true })); }
      catch (e) { /* Event no disponible */ }
      return;
    }
    cerrarMenuMovil();
  });

  // Confirmaciones: dialogo con el texto completo (no recorta data-confirmar).
  // Si el usuario acepta, el submit sigue y data-procesando puede correr.
  function confirmarTexto(texto, hecho) {
    var previo = document.getElementById('dialogoConfirmar');
    if (previo) previo.remove();

    var velo = document.createElement('div');
    velo.id = 'dialogoConfirmar';
    velo.className = 'dialogo-confirmar-velo';
    velo.setAttribute('role', 'dialog');
    velo.setAttribute('aria-modal', 'true');
    velo.setAttribute('aria-labelledby', 'dialogoConfirmarTitulo');
    velo.setAttribute('aria-describedby', 'dialogoConfirmarTexto');

    var caja = document.createElement('div');
    caja.className = 'dialogo-confirmar';

    var titulo = document.createElement('h2');
    titulo.id = 'dialogoConfirmarTitulo';
    titulo.className = 'dialogo-confirmar-titulo';
    titulo.textContent = 'Confirmar';

    var cuerpo = document.createElement('p');
    cuerpo.id = 'dialogoConfirmarTexto';
    cuerpo.className = 'dialogo-confirmar-texto';
    cuerpo.textContent = texto || '';

    var acciones = document.createElement('div');
    acciones.className = 'acciones-fila dialogo-confirmar-acciones';

    var cancelar = document.createElement('button');
    cancelar.type = 'button';
    cancelar.className = 'btn-neutro';
    cancelar.textContent = 'Cancelar';

    var aceptar = document.createElement('button');
    aceptar.type = 'button';
    aceptar.className = 'btn-jade';
    aceptar.textContent = 'Confirmar';

    acciones.appendChild(cancelar);
    acciones.appendChild(aceptar);
    caja.appendChild(titulo);
    caja.appendChild(cuerpo);
    caja.appendChild(acciones);
    velo.appendChild(caja);
    document.body.appendChild(velo);

    var origen = document.activeElement;
    var cerrado = false;
    function terminar(ok) {
      if (cerrado) return;
      cerrado = true;
      velo.remove();
      if (origen && typeof origen.focus === 'function') {
        try { origen.focus(); } catch (e) { /* foco perdido */ }
      }
      hecho(ok);
    }
    cancelar.addEventListener('click', function () { terminar(false); });
    aceptar.addEventListener('click', function () { terminar(true); });
    velo.addEventListener('keydown', function (ev) {
      if (ev.key === 'Escape') {
        ev.preventDefault();
        ev.stopPropagation();
        terminar(false);
      } else if (ev.key === 'Tab') {
        var ciclo = [cancelar, aceptar];
        var i = ciclo.indexOf(document.activeElement);
        if (ev.shiftKey) {
          if (i <= 0) { ev.preventDefault(); aceptar.focus(); }
        } else if (i === ciclo.length - 1) {
          ev.preventDefault();
          cancelar.focus();
        }
      }
    });
    aceptar.focus();
  }

  document.querySelectorAll('form[data-confirmar]').forEach(function (f) {
    f.addEventListener('submit', function (ev) {
      if (f._omitirConfirmGranados) return;
      ev.preventDefault();
      var texto = f.getAttribute('data-confirmar') || '';
      function seguirSi(ok) {
        if (!ok) return;
        f._omitirConfirmGranados = true;
        try {
          if (typeof f.requestSubmit === 'function') f.requestSubmit();
          else HTMLFormElement.prototype.submit.call(f);
        } finally {
          f._omitirConfirmGranados = false;
        }
      }
      try {
        confirmarTexto(texto, seguirSi);
      } catch (err) {
        seguirSi(window.confirm(texto));
      }
    });
  });

  // Mostrar el nombre del archivo elegido junto al campo
  document.querySelectorAll('input[type="file"][data-nombre]').forEach(function (inp) {
    var destino = document.getElementById(inp.getAttribute('data-nombre'));
    if (!destino) return;
    inp.addEventListener('change', function () {
      var caja = inp.closest('.campo-archivo');
      if (inp.files && inp.files.length > 1) {
        destino.textContent = inp.files.length + ' archivos seleccionados';
        if (caja) caja.classList.add('con-archivo');
      } else if (inp.files && inp.files.length === 1) {
        destino.textContent = inp.files[0].name;
        if (caja) caja.classList.add('con-archivo');
      } else {
        destino.textContent = destino.getAttribute('data-hint') || '';
        if (caja) caja.classList.remove('con-archivo');
      }
    });
  });

  document.querySelectorAll('.campo-archivo input[type="file"]:not([data-nombre])').forEach(function (inp) {
    inp.addEventListener('change', function () {
      var caja = inp.closest('.campo-archivo');
      if (!caja) return;
      if (inp.files && inp.files.length) caja.classList.add('con-archivo');
      else caja.classList.remove('con-archivo');
    });
  });

  function cerrarAviso(av) {
    if (!av || av.getAttribute('data-cerrando') === '1') return;
    av.setAttribute('data-cerrando', '1');
    if (av._avisoTimer) {
      clearTimeout(av._avisoTimer);
      av._avisoTimer = null;
    }
    av.style.transition = 'opacity .4s ease';
    av.style.opacity = '0';
    setTimeout(function () { av.remove(); }, 450);
  }

  document.querySelectorAll('.aviso').forEach(function (av) {
    if (av.querySelector('.cerrar-aviso')) return;
    var btn = document.createElement('button');
    btn.type = 'button';
    btn.className = 'cerrar-aviso';
    btn.setAttribute('aria-label', 'Cerrar');
    var ico = document.createElement('i');
    ico.className = 'bi bi-x';
    ico.setAttribute('aria-hidden', 'true');
    btn.appendChild(ico);
    av.appendChild(btn);
  });

  document.querySelectorAll('.aviso .cerrar-aviso').forEach(function (btn) {
    btn.addEventListener('click', function () {
      cerrarAviso(btn.closest('.aviso'));
    });
  });

  document.querySelectorAll('.aviso[data-auto]').forEach(function (av) {
    av._avisoTimer = setTimeout(function () { cerrarAviso(av); }, 6000);
  });

  // Al procesar archivos grandes, velo de pantalla + boton deshabilitado
  document.querySelectorAll('form[data-procesando]').forEach(function (f) {
    f.addEventListener('submit', function (ev) {
      if (ev.defaultPrevented) return;
      var texto = f.getAttribute('data-procesando') || 'Procesando...';
      var btn = f.querySelector('button[type="submit"]');
      if (btn) {
        btn.disabled = true;
        btn.innerHTML = '<span class="spinner-jade"></span> ' + texto;
      }
      if (!document.getElementById('veloProcesando')) {
        var velo = document.createElement('div');
        velo.id = 'veloProcesando';
        velo.className = 'velo-procesando';
        velo.setAttribute('role', 'status');
        velo.innerHTML = '<div class="mensaje"><span class="spinner-jade grande"></span>'
          + '<span></span></div>';
        velo.querySelector('.mensaje span:last-child').textContent = texto;
        document.body.appendChild(velo);
      }
    });
  });

  /* ------------------------------------------------------------------ *
   * 2 · Edicion "Noche en Verapaz"                                     *
   * ------------------------------------------------------------------ */

  /* Aparicion al hacer scroll: cualquier elemento con .revelar entra
     suavemente cuando asoma en el viewport. */
  var observador = null;
  if ('IntersectionObserver' in window && !REDUCIR) {
    observador = new IntersectionObserver(function (entradas) {
      entradas.forEach(function (en) {
        if (en.isIntersecting) {
          en.target.classList.add('visible');
          observador.unobserve(en.target);
        }
      });
    }, { threshold: 0.12, rootMargin: '0px 0px -6% 0px' });

    document.querySelectorAll('.revelar').forEach(function (el) {
      observador.observe(el);
    });
  } else {
    document.querySelectorAll('.revelar').forEach(function (el) {
      el.classList.add('visible');
    });
  }

  if (document.body.classList.contains('cuerpo-login') && !REDUCIR) {
    requestAnimationFrame(function () {
      document.querySelectorAll('.cuerpo-login .revelar').forEach(function (el) {
        el.classList.add('visible');
      });
    });
  }

  /* Tarjetas de accion: inclinacion 3D + brillo que persigue el cursor.
     El brillo se posiciona con las variables --mx / --my que usa el CSS. */
  if (!REDUCIR && window.matchMedia('(hover: hover)').matches) {
    document.querySelectorAll('.tarjeta-accion').forEach(function (tarjeta) {
      var giroX = 0, giroY = 0, objX = 0, objY = 0, raf = null, rect = null;

      function pintar() {
        raf = null;
        giroX += (objX - giroX) * 0.18;
        giroY += (objY - giroY) * 0.18;
        tarjeta.style.transform =
          'perspective(850px) rotateX(' + giroX.toFixed(2) + 'deg)'
          + ' rotateY(' + giroY.toFixed(2) + 'deg) translateY(-4px)';
        if (Math.abs(objX - giroX) > 0.02 || Math.abs(objY - giroY) > 0.02) {
          raf = requestAnimationFrame(pintar);
        }
      }
      function pedirPintura() { if (!raf) raf = requestAnimationFrame(pintar); }

      tarjeta.addEventListener('pointerenter', function () {
        rect = tarjeta.getBoundingClientRect();
      });
      tarjeta.addEventListener('pointermove', function (ev) {
        var r = rect;
        if (!r) {
          r = rect = tarjeta.getBoundingClientRect();
        }
        var px = (ev.clientX - r.left) / r.width;
        var py = (ev.clientY - r.top) / r.height;
        tarjeta.style.setProperty('--mx', (px * 100).toFixed(1) + '%');
        tarjeta.style.setProperty('--my', (py * 100).toFixed(1) + '%');
        objY = (px - 0.5) * 7;      // gira hacia el cursor
        objX = (0.5 - py) * 7;
        pedirPintura();
      });
      tarjeta.addEventListener('pointerleave', function () {
        rect = null;
        objX = 0; objY = 0;
        pedirPintura();
        setTimeout(function () { tarjeta.style.transform = ''; }, 350);
      });
    });
  }

  /* Fecha del dia y saludo segun la hora (dashboard) */
  var fechaHoy = document.getElementById('fechaHoy');
  if (fechaHoy) {
    try {
      var hoy = new Date().toLocaleDateString('es-GT', {
        weekday: 'long', day: 'numeric', month: 'long', year: 'numeric'
      });
      fechaHoy.textContent = hoy.charAt(0).toUpperCase() + hoy.slice(1);
    } catch (e) { fechaHoy.textContent = ''; }
  }
  var saludo = document.getElementById('saludoMomento');
  if (saludo) {
    var h = new Date().getHours();
    saludo.textContent = h < 12 ? 'Buenos dias' : (h < 19 ? 'Buenas tardes' : 'Buenas noches');
  }

  /* Contadores animados en .tarjeta-stat .valor: "Q 12,345.67" sube desde
     cero al entrar en pantalla. Si el texto no es numerico, se deja igual. */
  function animarValor(el) {
    var texto = el.textContent.trim();
    var m = texto.match(/^([^\d\-]*)(\-?[\d.,]+)(.*)$/);
    if (!m) return;
    var prefijo = m[1], numeroTxt = m[2], sufijo = m[3];
    var decimales = /\.\d{1,2}$/.test(numeroTxt)
      ? numeroTxt.split('.').pop().length : 0;
    var destino = parseFloat(numeroTxt.replace(/,/g, ''));
    if (!isFinite(destino) || destino === 0) return;

    var formato = new Intl.NumberFormat('es-GT', {
      minimumFractionDigits: decimales,
      maximumFractionDigits: decimales
    });
    var duracion = 950;
    var t0 = null;

    function paso(ts) {
      if (!t0) t0 = ts;
      var p = Math.min((ts - t0) / duracion, 1);
      var suavizado = 1 - Math.pow(1 - p, 3);      // ease-out cubico
      el.textContent = prefijo + formato.format(destino * suavizado) + sufijo;
      if (p < 1) requestAnimationFrame(paso);
      else el.textContent = texto;                  // valor exacto al final
    }
    requestAnimationFrame(paso);
  }

  var valores = document.querySelectorAll('.tarjeta-stat .valor, .cinta-dato .valor');
  if (valores.length && !REDUCIR) {
    if ('IntersectionObserver' in window) {
      var obsValores = new IntersectionObserver(function (entradas) {
        entradas.forEach(function (en) {
          if (en.isIntersecting) {
            animarValor(en.target);
            obsValores.unobserve(en.target);
          }
        });
      }, { threshold: 0.4 });
      valores.forEach(function (el) { obsValores.observe(el); });
    } else {
      valores.forEach(animarValor);
    }
  }

  /* Submenu de Compras: el chevron abre/cierra; el enlace sigue yendo al panel */
  document.querySelectorAll('.menu-item.activo, .pestania.activa, .pestanias-sub a.activa').forEach(function (el) {
    el.setAttribute('aria-current', 'page');
  });

  document.querySelectorAll('.menu-toggle').forEach(function (btn) {
    function syncToggle() {
      var grupo = btn.closest('.menu-grupo');
      btn.setAttribute('aria-expanded',
        grupo && grupo.classList.contains('abierto') ? 'true' : 'false');
    }
    syncToggle();
    btn.addEventListener('click', function (ev) {
      ev.preventDefault();
      ev.stopPropagation();
      var grupo = btn.closest('.menu-grupo');
      if (grupo) grupo.classList.toggle('abierto');
      syncToggle();
    });
  });
  var chip = document.getElementById('menuUsuario');
  if (chip) {
    function syncChipAria() {
      var activo = chip.classList.contains('abierto')
        || (document.activeElement && chip.contains(document.activeElement));
      chip.setAttribute('aria-expanded', activo ? 'true' : 'false');
    }
    chip.addEventListener('click', function (ev) {
      if (ev.target.closest('form') || ev.target.closest('button')) return;
      chip.classList.toggle('abierto');
      syncChipAria();
    });
    document.addEventListener('click', function (ev) {
      if (!chip.contains(ev.target)) {
        chip.classList.remove('abierto');
        syncChipAria();
      }
    });
    chip.addEventListener('keydown', function (ev) {
      if (ev.key === 'Escape') {
        chip.classList.remove('abierto');
        syncChipAria();
      }
      if (ev.key === 'Enter' || ev.key === ' ') {
        if (ev.target === chip) {
          ev.preventDefault();
          chip.classList.toggle('abierto');
          syncChipAria();
        }
      }
    });
    chip.addEventListener('focusin', syncChipAria);
    chip.addEventListener('focusout', function () {
      setTimeout(syncChipAria, 0);
    });
  }

  /* Login full-bleed: foco que sigue al cursor (transform, sin pintar el fondo). */
  var foco = document.getElementById('focoLogin');
  if (foco && !REDUCIR && window.matchMedia('(hover: hover)').matches) {
    var fx = window.innerWidth * 0.62, fy = window.innerHeight * 0.40;
    var ox = fx, oy = fy, rafFoco = null;
    function pintarFoco() {
      rafFoco = null;
      fx += (ox - fx) * 0.08;
      fy += (oy - fy) * 0.08;
      foco.style.transform = 'translate3d(' + fx.toFixed(1) + 'px,' + fy.toFixed(1) + 'px,0)';
      if (Math.abs(ox - fx) > 0.5 || Math.abs(oy - fy) > 0.5) {
        rafFoco = requestAnimationFrame(pintarFoco);
      }
    }
    window.addEventListener('pointermove', function (ev) {
      ox = ev.clientX;
      oy = ev.clientY;
      if (!rafFoco) rafFoco = requestAnimationFrame(pintarFoco);
    }, { passive: true });
  }

  /* Arrastre visual sobre campos de archivo (no cambia el envio) */
  document.querySelectorAll('.campo-archivo').forEach(function (caja) {
    ['dragenter', 'dragover'].forEach(function (evNombre) {
      caja.addEventListener(evNombre, function (ev) {
        ev.preventDefault();
        caja.classList.add('arrastre');
      });
    });
    ['dragleave', 'drop'].forEach(function (evNombre) {
      caja.addEventListener(evNombre, function () {
        caja.classList.remove('arrastre');
      });
    });
  });

  /* Cursor municipal: sello + anillo 1:1 con el puntero (estados, no lerp). */
  (function cursorMunicipal() {
    var anillo = document.getElementById('cursorSeguidor');
    var punto = document.getElementById('cursorPunto');
    var raiz = document.getElementById('cursorRaiz');
    var fino = window.matchMedia && window.matchMedia('(hover: hover)').matches
      && window.matchMedia('(pointer: fine)').matches;
    if (!fino || !anillo || !punto || REDUCIR) return;
    if (!raiz) {
      raiz = document.createElement('div');
      raiz.id = 'cursorRaiz';
      raiz.className = 'cursor-raiz';
      raiz.setAttribute('aria-hidden', 'true');
      anillo.parentNode.insertBefore(raiz, anillo);
      raiz.appendChild(anillo);
      raiz.appendChild(punto);
    }
    document.documentElement.classList.add('cursor-personal');

    var SEL_CLICK = 'a, button, .btn, [role="button"], .modulo, .modulo-fila, summary, label, .menu-item, .chip-filtro, .pestania, input[type="button"], input[type="submit"], input[type="reset"], input[type="checkbox"], input[type="radio"], input[type="file"]';

    function nodoEl(n) {
      if (!n) return null;
      return n.nodeType === 1 ? n : n.parentElement;
    }
    function esTexto(el) {
      el = nodoEl(el);
      if (!el || !el.closest) return false;
      if (el.isContentEditable || el.closest('[contenteditable="true"]')) return true;
      var campo = el.closest('input, textarea');
      if (!campo) return false;
      if (campo.tagName === 'TEXTAREA') return true;
      if (campo.tagName !== 'INPUT') return false;
      var tipo = (campo.type || 'text').toLowerCase();
      return tipo !== 'checkbox' && tipo !== 'radio' && tipo !== 'file'
        && tipo !== 'button' && tipo !== 'submit' && tipo !== 'reset'
        && tipo !== 'range' && tipo !== 'color' && tipo !== 'hidden';
    }
    function esDeshabilitado(el) {
      if (!el || !el.closest) return false;
      var d = el.closest('[disabled], .disabled, [aria-disabled="true"]');
      if (!d) return false;
      return !!(d.disabled || d.getAttribute('aria-disabled') === 'true' || d.classList.contains('disabled'));
    }
    function esClickable(el) {
      el = nodoEl(el);
      if (!el || !el.closest) return null;
      return el.closest(SEL_CLICK);
    }
    function setClase(nombre, on) {
      raiz.classList.toggle(nombre, on);
      anillo.classList.toggle(nombre, on);
      punto.classList.toggle(nombre, on);
    }
    var encendido = false;
    var ultimoEstado = '';
    function aplicarEstado(el) {
      var texto = esTexto(el);
      var objetivo = texto ? null : esClickable(el);
      var muted = !!(objetivo && esDeshabilitado(objetivo));
      var clave = (texto ? 't' : '-') + (objetivo ? 'c' : '-') + (muted ? 'd' : '-');
      if (clave === ultimoEstado) return;
      ultimoEstado = clave;
      setClase('is-text', texto);
      setClase('is-hover', !!objetivo && !muted);
      setClase('is-disabled', muted);
    }
    function pintar(x, y) {
      var t = 'translate3d(' + x + 'px,' + y + 'px,0)';
      anillo.style.transform = t;
      punto.style.transform = t;
    }

    window.addEventListener('pointermove', function (ev) {
      pintar(ev.clientX, ev.clientY);
      if (!encendido) {
        encendido = true;
        setClase('is-on', true);
        aplicarEstado(ev.target);
      }
    }, { passive: true });
    window.addEventListener('pointerover', function (ev) {
      aplicarEstado(ev.target);
    }, { passive: true });
    window.addEventListener('pointerout', function (ev) {
      if (!ev.relatedTarget) {
        encendido = false;
        ultimoEstado = '';
        setClase('is-on', false);
        setClase('is-hover', false);
        setClase('is-press', false);
        setClase('is-text', false);
        setClase('is-disabled', false);
        return;
      }
      aplicarEstado(ev.relatedTarget);
    }, { passive: true });
    window.addEventListener('pointerdown', function (ev) {
      if (ev.pointerType === 'touch' || esTexto(ev.target)) return;
      var obj = esClickable(ev.target);
      if (obj && !esDeshabilitado(obj)) setClase('is-press', true);
    }, { passive: true });
    window.addEventListener('pointerup', function () {
      setClase('is-press', false);
    }, { passive: true });
    window.addEventListener('pointercancel', function () {
      setClase('is-press', false);
    }, { passive: true });
  })();

  /* ------------------------------------------------------------------ *
   * 3 · Pulido generico (filtros, copiar, fechas, scroll de tablas)    *
   * ------------------------------------------------------------------ */

  function esFormFiltro(form) {
    if (!form) return false;
    if (form.hasAttribute('data-filtro')) return true;
    if (form.classList.contains('filtro-rapido') || form.classList.contains('buscador-caja')) {
      return true;
    }
    return !!(form.querySelector('[name="q"], input[data-filtro], [data-filtro-q]'));
  }

  function esCampoBusquedaFiltro(el) {
    if (!el || el.disabled || el.readOnly) return false;
    if (el.tagName !== 'INPUT' && el.tagName !== 'TEXTAREA') return false;
    if (el.tagName === 'TEXTAREA') return false;
    if (el.name === 'q' || el.hasAttribute('data-filtro') || el.hasAttribute('data-filtro-q')) {
      return true;
    }
    var form = el.closest('form');
    return !!(form && form.hasAttribute('data-filtro') && (el.type === 'search' || el.type === 'text'));
  }

  document.addEventListener('keydown', function (ev) {
    if (ev.key !== 'Enter' || ev.defaultPrevented) return;
    if (ev.ctrlKey || ev.metaKey || ev.altKey) return;
    var t = ev.target;
    if (!t || t.isContentEditable) return;
    if (t.tagName === 'TEXTAREA' || t.tagName === 'BUTTON' || t.tagName === 'A') return;
    if (t.type === 'submit' || t.type === 'button' || t.type === 'reset' || t.type === 'file') return;
    var form = t.closest('form');
    if (!esFormFiltro(form)) return;
    if (t.tagName === 'SELECT') {
      ev.preventDefault();
      if (typeof form.requestSubmit === 'function') form.requestSubmit();
      else form.submit();
    }
  });

  var anuncios = document.getElementById('granadosAnuncios');
  if (!anuncios) {
    anuncios = document.createElement('div');
    anuncios.id = 'granadosAnuncios';
    anuncios.className = 'solo-lector';
    anuncios.setAttribute('aria-live', 'polite');
    anuncios.setAttribute('aria-atomic', 'true');
    document.body.appendChild(anuncios);
  }
  function anunciar(msg) {
    anuncios.textContent = '';
    setTimeout(function () { anuncios.textContent = msg; }, 30);
  }

  function textoACopiar(btn) {
    var directo = btn.getAttribute('data-copiar');
    if (directo) return directo;
    var sel = btn.getAttribute('data-copiar-de');
    if (sel) {
      var nodo = document.querySelector(sel);
      if (!nodo) return '';
      return (nodo.value != null && nodo.value !== undefined && nodo.tagName !== 'BUTTON'
        ? String(nodo.value) : (nodo.textContent || '')).trim();
    }
    var cerca = btn.closest('[data-codigo]');
    if (cerca && cerca !== btn) {
      return (cerca.getAttribute('data-codigo') || cerca.textContent || '').trim();
    }
    var prev = btn.previousElementSibling;
    if (prev) return (prev.value || prev.textContent || '').trim();
    return '';
  }

  function copiarAlPortapapeles(texto, ok, fail) {
    if (navigator.clipboard && navigator.clipboard.writeText) {
      navigator.clipboard.writeText(texto).then(ok, function () { fallbackCopiar(texto, ok, fail); });
      return;
    }
    fallbackCopiar(texto, ok, fail);
  }

  function fallbackCopiar(texto, ok, fail) {
    var ta = document.createElement('textarea');
    ta.value = texto;
    ta.setAttribute('readonly', '');
    ta.style.position = 'fixed';
    ta.style.left = '-9999px';
    document.body.appendChild(ta);
    ta.select();
    var bien = false;
    try { bien = document.execCommand('copy'); } catch (e) { bien = false; }
    ta.remove();
    if (bien) ok();
    else fail();
  }

  document.addEventListener('click', function (ev) {
    var btn = ev.target.closest('[data-copiar]');
    if (!btn) return;
    ev.preventDefault();
    var texto = textoACopiar(btn);
    if (!texto) return;
    copiarAlPortapapeles(texto, function () {
      btn.classList.add('copiado');
      anunciar('Copiado al portapapeles');
      setTimeout(function () { btn.classList.remove('copiado'); }, 1600);
    }, function () {
      anunciar('No se pudo copiar');
    });
  });

  function textoFechaRelativa(date) {
    var seg = Math.round((Date.now() - date.getTime()) / 1000);
    var abs = Math.abs(seg);
    var futuro = seg < 0;
    function frase(n, uno, muchos) {
      var cuerpo = n === 1 ? uno : muchos.replace('#', String(n));
      return futuro ? ('en ' + cuerpo) : ('hace ' + cuerpo);
    }
    if (abs < 45) return futuro ? 'en un momento' : 'hace un momento';
    if (abs < 3600) return frase(Math.round(abs / 60), '1 minuto', '# minutos');
    if (abs < 86400) return frase(Math.round(abs / 3600), '1 hora', '# horas');
    if (abs < 604800) return frase(Math.round(abs / 86400), '1 dia', '# dias');
    try {
      return date.toLocaleDateString('es-GT', { day: 'numeric', month: 'short', year: 'numeric' });
    } catch (e) {
      return date.toISOString().slice(0, 10);
    }
  }

  function tituloFechaAbsoluta(date) {
    try {
      return date.toLocaleString('es-GT', {
        weekday: 'short',
        year: 'numeric',
        month: 'short',
        day: 'numeric',
        hour: '2-digit',
        minute: '2-digit'
      });
    } catch (e) {
      return date.toISOString();
    }
  }

  document.querySelectorAll('[data-fecha]').forEach(function (el) {
    var bruto = el.getAttribute('data-fecha');
    if (!bruto) return;
    var date = new Date(bruto);
    if (isNaN(date.getTime())) return;
    if (!el.getAttribute('title')) el.setAttribute('title', tituloFechaAbsoluta(date));
    if (el.tagName === 'TIME' && !el.getAttribute('datetime')) {
      el.setAttribute('datetime', date.toISOString());
    }
    el.textContent = textoFechaRelativa(date);
  });

  function syncScrollTabla(caja) {
    var max = caja.scrollWidth - caja.clientWidth;
    caja.classList.toggle('hay-scroll-x', max > 8);
    caja.classList.toggle('tabla-scroll-izq', caja.scrollLeft > 4);
    caja.classList.toggle('tabla-scroll-der', caja.scrollLeft < max - 4);
  }

  document.querySelectorAll('.tabla-envoltura, .tabla-scroll').forEach(function (caja) {
    syncScrollTabla(caja);
    caja.addEventListener('scroll', function () { syncScrollTabla(caja); }, { passive: true });
    window.addEventListener('resize', function () { syncScrollTabla(caja); });
    if (typeof ResizeObserver === 'function') {
      var ro = new ResizeObserver(function () { syncScrollTabla(caja); });
      ro.observe(caja);
      if (caja.firstElementChild) ro.observe(caja.firstElementChild);
    }
  });

  document.addEventListener('click', function (ev) {
    var btn = ev.target.closest('[data-porcentaje]');
    if (!btn) return;
    ev.preventDefault();
    var dest = document.getElementById(btn.getAttribute('data-destino') || '');
    var max = parseFloat(String(btn.getAttribute('data-max') || '0').replace(',', '.'));
    var pct = parseFloat(btn.getAttribute('data-porcentaje') || '0');
    if (!dest || !isFinite(max) || !isFinite(pct)) return;
    dest.value = (Math.round(max * pct) / 100).toFixed(2);
    dest.focus();
  });

  document.querySelectorAll('details.panel-desplegable').forEach(function (d) {
    var inp = d.querySelector('#fBanco[name="montoBanco"]');
    if (!inp) return;
    function syncBanco() { inp.disabled = !d.open; }
    syncBanco();
    d.addEventListener('toggle', syncBanco);
  });
})();
