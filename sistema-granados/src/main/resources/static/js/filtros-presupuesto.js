/* Filtros de presupuesto DAFIM: recorte en vivo + GET si hay muchas filas.
   No toca app.js (Enter / Escape / data-confirmar viven alla). */
(function () {
  'use strict';

  var UMBRAL_DEF = 50;
  var DEBOUNCE_MS = 450;
  var timers = [];

  function norm(s) {
    return String(s || '')
      .toUpperCase()
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '')
      .replace(/\s+/g, ' ')
      .trim();
  }

  function ambitoDe(el) {
    return el.closest('[data-filtro-ambito]') || document;
  }

  function filas(ambito) {
    return ambito.querySelectorAll('[data-filtro-fila]');
  }

  function chipActivo(ambito, nombre) {
    var btn = ambito.querySelector('[data-filtro-chip="' + nombre + '"].activo');
    return btn ? (btn.getAttribute('data-valor') || '') : '';
  }

  function aplicar(ambito) {
    if (!ambito || !ambito.querySelector) return;
    var input = ambito.querySelector('[data-filtro-vivo]');
    var q = norm(input ? input.value : '');
    var saldo = chipActivo(ambito, 'saldo');
    var n = 0;
    var lista = filas(ambito);
    lista.forEach(function (fila) {
      var texto = norm(fila.getAttribute('data-filtro-texto') || fila.textContent);
      var ok = !q || texto.indexOf(q) !== -1;
      if (ok && saldo === 'con' && fila.getAttribute('data-saldo') !== '1') ok = false;
      if (ok && saldo === 'agotadas' && fila.getAttribute('data-saldo') === '1') ok = false;
      fila.classList.toggle('filtro-oculto', !ok);
      if (ok) n += 1;
    });
    ambito.querySelectorAll('[data-filtro-grupo]').forEach(function (g) {
      var hay = g.querySelector('[data-filtro-fila]');
      var vis = g.querySelectorAll('[data-filtro-fila]:not(.filtro-oculto)').length;
      g.classList.toggle('filtro-oculto', !!hay && vis === 0);
    });
    var conteo = ambito.querySelector('[data-filtro-conteo]');
    if (conteo) {
      conteo.textContent = lista.length ? (n + (n === 1 ? ' visible' : ' visibles')) : '';
    }
    var vacio = ambito.querySelector('[data-filtro-vacio-local]');
    if (vacio) vacio.hidden = n > 0 || lista.length === 0;
  }

  function programarServidor(form, ambito) {
    if (!form || !form.hasAttribute('data-filtro-servidor')) return;
    var umbral = parseInt(ambito.getAttribute('data-filtro-umbral') || String(UMBRAL_DEF), 10);
    if (isNaN(umbral)) umbral = UMBRAL_DEF;
    if (filas(ambito).length < umbral) return;
    var i;
    for (i = 0; i < timers.length; i += 1) {
      if (timers[i].form === form) {
        clearTimeout(timers[i].id);
        timers.splice(i, 1);
        break;
      }
    }
    var id = setTimeout(function () {
      if (typeof form.requestSubmit === 'function') form.requestSubmit();
      else form.submit();
    }, DEBOUNCE_MS);
    timers.push({ form: form, id: id });
  }

  document.addEventListener('change', function (ev) {
    var t = ev.target;
    if (!t || !t.getAttribute) return;
    var id = t.getAttribute('data-filtro-hidden');
    if (!id) return;
    var hidden = document.getElementById(id);
    if (!hidden) return;
    hidden.value = t.checked ? (t.getAttribute('data-valor') || '1') : '';
  });

  document.addEventListener('input', function (ev) {
    var t = ev.target;
    if (!t || !t.hasAttribute || !t.hasAttribute('data-filtro-vivo')) return;
    var ambito = ambitoDe(t);
    aplicar(ambito);
    programarServidor(t.closest('form'), ambito);
  });

  document.addEventListener('click', function (ev) {
    var chip = ev.target.closest('[data-filtro-chip]');
    if (!chip || chip.tagName === 'A') return;
    ev.preventDefault();
    var ambito = ambitoDe(chip);
    var nombre = chip.getAttribute('data-filtro-chip');
    var ya = chip.classList.contains('activo');
    ambito.querySelectorAll('[data-filtro-chip="' + nombre + '"]').forEach(function (c) {
      c.classList.remove('activo');
    });
    if (!ya) chip.classList.add('activo');
    aplicar(ambito);
  });

  document.addEventListener('submit', function (ev) {
    var form = ev.target;
    var i;
    for (i = 0; i < timers.length; i += 1) {
      if (timers[i].form === form) {
        clearTimeout(timers[i].id);
        timers.splice(i, 1);
        break;
      }
    }
  });

  document.addEventListener('keydown', function (ev) {
    if (ev.key !== '/' || ev.ctrlKey || ev.metaKey || ev.altKey) return;
    var t = ev.target;
    if (t && (t.tagName === 'INPUT' || t.tagName === 'TEXTAREA' || t.tagName === 'SELECT' || t.isContentEditable)) {
      return;
    }
    var input = document.querySelector('[data-filtro-vivo], .buscador-caja input[name="q"]');
    if (!input) return;
    ev.preventDefault();
    input.focus();
    if (typeof input.select === 'function') input.select();
  });

  document.querySelectorAll('[data-filtro-ambito]').forEach(aplicar);
})();
