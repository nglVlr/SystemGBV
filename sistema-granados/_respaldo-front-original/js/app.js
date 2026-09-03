/* Sistema Granados: interacciones basicas del armazon */
(function () {
  'use strict';

  var CLAVE_MENU = 'granados.menuPlegado';

  // Restaurar preferencia de menu plegado (solo escritorio)
  try {
    if (localStorage.getItem(CLAVE_MENU) === '1' && window.innerWidth >= 992) {
      document.body.classList.add('menu-plegado');
    }
  } catch (e) { /* almacenamiento no disponible: continuar sin persistencia */ }

  var boton = document.getElementById('botonMenu');
  if (boton) {
    boton.addEventListener('click', function () {
      if (window.innerWidth < 992) {
        document.body.classList.toggle('menu-abierto');
      } else {
        var plegado = document.body.classList.toggle('menu-plegado');
        try { localStorage.setItem(CLAVE_MENU, plegado ? '1' : '0'); }
        catch (e) { /* sin persistencia */ }
      }
    });
  }

  // Cerrar el menu movil al tocar el fondo oscuro
  document.addEventListener('click', function (ev) {
    if (document.body.classList.contains('menu-abierto')
        && !ev.target.closest('.menu-lateral')
        && !ev.target.closest('#botonMenu')) {
      document.body.classList.remove('menu-abierto');
    }
  });

  // Confirmaciones en formularios delicados (eliminar, reprocesar, etc.)
  document.querySelectorAll('form[data-confirmar]').forEach(function (f) {
    f.addEventListener('submit', function (ev) {
      if (!window.confirm(f.getAttribute('data-confirmar'))) {
        ev.preventDefault();
      }
    });
  });

  // Mostrar el nombre del archivo elegido junto al campo
  document.querySelectorAll('input[type="file"][data-nombre]').forEach(function (inp) {
    var destino = document.getElementById(inp.getAttribute('data-nombre'));
    if (!destino) return;
    inp.addEventListener('change', function () {
      if (inp.files && inp.files.length > 1) {
        destino.textContent = inp.files.length + ' archivos seleccionados';
      } else if (inp.files && inp.files.length === 1) {
        destino.textContent = inp.files[0].name;
      } else {
        destino.textContent = '';
      }
    });
  });

  // Ocultar avisos automaticamente despues de unos segundos
  document.querySelectorAll('.aviso[data-auto]').forEach(function (av) {
    setTimeout(function () {
      av.style.transition = 'opacity .4s ease';
      av.style.opacity = '0';
      setTimeout(function () { av.remove(); }, 450);
    }, 6000);
  });

  // Al procesar archivos grandes, avisar que puede tardar
  document.querySelectorAll('form[data-procesando]').forEach(function (f) {
    f.addEventListener('submit', function () {
      var btn = f.querySelector('button[type="submit"]');
      if (btn) {
        btn.disabled = true;
        btn.innerHTML = '<span class="spinner-border spinner-border-sm me-2"></span>'
          + f.getAttribute('data-procesando');
      }
    });
  });
})();
