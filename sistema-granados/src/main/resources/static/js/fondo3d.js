/* ============================================================================
   SISTEMA GRANADOS · Fondo 3D cinematografico (edicion Cine DAFIM)
   ----------------------------------------------------------------------------
   Cielo estrellado de noche o cielo diurno con nubes, horizonte dorado
   y terreno con luz de cresta. El uniforme `uClaro` cambia la paleta.
   Camara lenta tipo toma de video. Si no hay WebGL, el CSS usa un cielo
   degradado equivalente.

   - Si WebGL no esta disponible, deja la clase `sin-webgl` en <body> y el
     CSS muestra un cielo degradado estatico equivalente.
   - Se pausa cuando la pestana no es visible y respeta
     `prefers-reduced-motion` (pinta solo unos cuadros estaticos).
   ========================================================================== */
(function () {
  'use strict';

  var canvas = document.getElementById('fondo3d');
  if (!canvas) return;

  function esClaro() {
    return document.documentElement.getAttribute('data-tema') === 'claro';
  }

  function usarRespaldo() {
    document.body.classList.add('sin-webgl');
    if (canvas.parentNode) canvas.parentNode.removeChild(canvas);
  }

  var glOpts = { antialias: false, alpha: false, depth: true, powerPreference: 'low-power' };
  var gl = canvas.getContext('webgl', glOpts)
        || canvas.getContext('experimental-webgl', glOpts);
  if (!gl) { usarRespaldo(); return; }

  /* ------------------------------ Shaders ------------------------------- */
  // Ruido de valor compacto (sin texturas): hash -> ruido -> 3 octavas fbm
  var GLSL_RUIDO =
    'float hash(vec2 p){ return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123); }' +
    'float ruido(vec2 p){' +
    '  vec2 i = floor(p); vec2 f = fract(p);' +
    '  vec2 u = f * f * (3.0 - 2.0 * f);' +
    '  return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), u.x),' +
    '             mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), u.x), u.y);' +
    '}' +
    'float fbm(vec2 p){' +
    '  float v = 0.0; float a = 0.55;' +
    '  for (int i = 0; i < 3; i++) { v += a * ruido(p); p = p * 2.03; a *= 0.5; }' +
    '  return v;' +
    '}';

  /* Cielo: triangulo de pantalla completa con degradado y aurora sutil */
  var VS_CIELO =
    'attribute vec2 aClip;' +
    'varying vec2 vUv;' +
    'void main(){ vUv = aClip * 0.5 + 0.5; gl_Position = vec4(aClip, 0.0, 1.0); }';

  var FS_CIELO =
    'precision mediump float;' +
    'varying vec2 vUv;' +
    'uniform float uTiempo;' +
    'uniform float uClaro;' +
    'float hash(vec2 p){ return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123); }' +
    'float ruido(vec2 p){' +
    '  vec2 i = floor(p); vec2 f = fract(p);' +
    '  vec2 u = f * f * (3.0 - 2.0 * f);' +
    '  return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), u.x),' +
    '             mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), u.x), u.y);' +
    '}' +
    'void main(){' +
    '  vec3 cenitN     = vec3(0.004, 0.004, 0.006);' +
    '  vec3 horizonteN = vec3(0.055, 0.042, 0.022);' +
    '  vec3 cenitC     = vec3(0.58, 0.78, 0.92);' +
    '  vec3 horizonteC = vec3(0.97, 0.88, 0.66);' +
    '  vec3 cenit = mix(cenitN, cenitC, uClaro);' +
    '  vec3 horizonte = mix(horizonteN, horizonteC, uClaro);' +
    '  vec3 col = mix(horizonte, cenit, smoothstep(0.0, 0.82, vUv.y));' +
    '  float n = hash(floor(vUv * vec2(520.0, 340.0)));' +
    '  float star = step(0.9958, n) * (0.55 + 0.45 * sin(uTiempo * 2.2 + n * 40.0));' +
    '  col += vec3(0.92, 0.90, 0.82) * star * smoothstep(0.15, 0.7, vUv.y) * (1.0 - uClaro);' +
    '  float b1 = sin(vUv.x * 2.4 + uTiempo * 0.04 + sin(vUv.y * 3.5 + uTiempo * 0.03) * 0.7);' +
    '  float a1 = smoothstep(0.58, 1.0, b1) * mix(0.07, 0.14, uClaro) * smoothstep(1.0, 0.38, vUv.y);' +
    '  col += mix(vec3(0.18, 0.16, 0.10), vec3(1.0, 0.97, 0.90), uClaro) * a1;' +
    '  float b2 = sin(vUv.x * 3.1 - uTiempo * 0.03 + 1.7);' +
    '  float a2 = smoothstep(0.62, 1.0, b2) * mix(0.045, 0.09, uClaro) * smoothstep(1.0, 0.5, vUv.y);' +
    '  col += mix(vec3(0.42, 0.32, 0.12), vec3(0.96, 0.90, 0.76), uClaro) * a2;' +
    '  float nubes = ruido(vUv * vec2(4.5, 2.4) + vec2(uTiempo * 0.018, 0.0));' +
    '  nubes += 0.5 * ruido(vUv * vec2(9.0, 5.0) + vec2(uTiempo * 0.03, 0.1));' +
    '  nubes = smoothstep(0.52, 0.95, nubes) * smoothstep(0.18, 0.58, vUv.y) * smoothstep(1.0, 0.52, vUv.y);' +
    '  col = mix(col, vec3(1.0, 0.98, 0.94), nubes * 0.5 * uClaro);' +
    '  col += vec3(0.22, 0.16, 0.06) * smoothstep(0.38, 0.0, vUv.y) * mix(0.55, 0.22, uClaro);' +
    '  float hazX = mix(0.52, 0.76, uClaro) - sin(uTiempo * 0.07) * 0.06;' +
    '  float haz = pow(max(0.0, 1.0 - abs(vUv.x - hazX) * mix(4.2, 3.4, uClaro)), 10.0);' +
    '  col += mix(vec3(0.62, 0.48, 0.18), vec3(1.0, 0.88, 0.48), uClaro) * haz * (1.0 - vUv.y) * mix(0.22, 0.4, uClaro);' +
    '  gl_FragColor = vec4(col, 1.0);' +
    '}';

  /* Terreno relleno: altura por fbm y normal suave por diferencias finitas */
  var VS_TERRENO =
    'attribute vec2 aPos;' +                       // x,z en el plano
    'uniform mat4 uP;' +                           // proyeccion
    'uniform mat4 uV;' +                           // vista
    'uniform float uTiempo;' +
    'varying float vAltura;' +
    'varying vec3 vNormal;' +
    'varying float vProfundidad;' +
    GLSL_RUIDO +
    'float altura(vec2 p, float deriva){' +
    '  return pow(fbm(vec2(p.x * 0.055, (p.y + deriva) * 0.055)), 1.6) * 9.0;' +
    '}' +
    'void main(){' +
    '  vec2 p = aPos;' +
    '  float deriva = uTiempo * 1.2;' +            // vuelo lento sobre las colinas
    '  float h = altura(p, deriva);' +
    '  float e = 0.85;' +                          // delta para la normal
    '  float hx = altura(p + vec2(e, 0.0), deriva) - altura(p - vec2(e, 0.0), deriva);' +
    '  float hz = altura(p + vec2(0.0, e), deriva) - altura(p - vec2(0.0, e), deriva);' +
    '  vNormal = normalize(vec3(-hx, 2.0 * e, -hz));' +
    '  vAltura = h / 9.0;' +
    '  vec4 vista = uV * vec4(p.x, h, p.y, 1.0);' +
    '  vProfundidad = -vista.z;' +                 // la camara mira hacia -z
    '  gl_Position = uP * vista;' +
    '}';

  var FS_TERRENO =
    'precision mediump float;' +
    'varying float vAltura;' +
    'varying vec3 vNormal;' +
    'varying float vProfundidad;' +
    'uniform float uClaro;' +
    'void main(){' +
    '  vec3 valle = mix(vec3(0.028, 0.026, 0.022), vec3(0.52, 0.60, 0.36), uClaro);' +
    '  vec3 loma  = mix(vec3(0.10, 0.09, 0.07), vec3(0.84, 0.75, 0.50), uClaro);' +
    '  vec3 base = mix(valle, loma, smoothstep(0.05, 0.75, vAltura));' +
    '  vec3 L = normalize(mix(vec3(-0.35, 0.85, 0.40), vec3(0.45, 0.78, 0.32), uClaro));' +
    '  float dif = max(dot(normalize(vNormal), L), 0.0);' +
    '  vec3 col = base * mix(0.38, 0.58, uClaro) + base * dif * mix(0.62, 0.52, uClaro);' +
    '  col += mix(vec3(0.55, 0.42, 0.16), vec3(0.96, 0.84, 0.42), uClaro)' +
    '       * smoothstep(0.55, 0.95, vAltura) * dif * mix(0.62, 0.38, uClaro);' +
    '  float niebla = smoothstep(22.0, 85.0, vProfundidad);' +
    '  vec3 nieblaCol = mix(vec3(0.012, 0.011, 0.010), vec3(0.76, 0.86, 0.92), uClaro);' +
    '  col = mix(col, nieblaCol, niebla);' +
    '  gl_FragColor = vec4(col, 1.0);' +
    '}';

  /* Luciernagas doradas (puntos aditivos con halo) */
  var VS_LUCES =
    'attribute vec3 aBase;' +                      // x, z de salida, fase
    'uniform mat4 uP;' +
    'uniform mat4 uV;' +
    'uniform float uTiempo;' +
    'uniform float uDpr;' +
    'varying float vBrillo;' +
    GLSL_RUIDO +
    'void main(){' +
    '  float fase = aBase.z;' +
    '  float deriva = uTiempo * 1.2;' +
    '  float x = aBase.x + sin(uTiempo * 0.20 + fase * 6.28) * 1.6;' +
    '  float z = mod(aBase.y + deriva, 78.0) - 70.0;' +
    '  float suelo = pow(fbm(vec2(x * 0.055, (z + deriva) * 0.055)), 1.6) * 9.0;' +
    '  float y = suelo + 0.9 + fract(fase * 7.31) * 3.4 + sin(uTiempo * 0.7 + fase * 40.0) * 0.35;' +
    '  vec4 vista = uV * vec4(x, y, z, 1.0);' +
    '  float prof = max(-vista.z, 4.0);' +
    '  vBrillo = 0.45 + 0.55 * sin(uTiempo * (0.9 + fract(fase * 3.7)) + fase * 50.0);' +
    '  gl_PointSize = (1.6 + fract(fase * 13.7) * 2.8) * uDpr * (34.0 / prof);' +
    '  gl_Position = uP * vista;' +
    '}';

  var FS_LUCES =
    'precision mediump float;' +
    'varying float vBrillo;' +
    'uniform float uClaro;' +
    'void main(){' +
    '  vec2 c = gl_PointCoord - 0.5;' +
    '  float d = length(c);' +
    '  float halo = smoothstep(0.5, 0.02, d);' +
    '  vec3 colN = mix(vec3(0.95, 0.78, 0.25), vec3(1.0, 0.92, 0.6), halo);' +
    '  vec3 colC = mix(vec3(1.0, 0.94, 0.72), vec3(1.0, 0.99, 0.92), halo);' +
    '  vec3 col = mix(colN, colC, uClaro);' +
    '  float alfa = halo * mix(0.30 + 0.55 * vBrillo, 0.16 + 0.28 * vBrillo, uClaro);' +
    '  gl_FragColor = vec4(col, alfa);' +
    '}';

  function compilar(tipo, fuente) {
    var s = gl.createShader(tipo);
    gl.shaderSource(s, fuente);
    gl.compileShader(s);
    if (!gl.getShaderParameter(s, gl.COMPILE_STATUS)) {
      throw new Error('Shader: ' + gl.getShaderInfoLog(s));
    }
    return s;
  }

  function programa(vs, fs) {
    var p = gl.createProgram();
    gl.attachShader(p, compilar(gl.VERTEX_SHADER, vs));
    gl.attachShader(p, compilar(gl.FRAGMENT_SHADER, fs));
    gl.linkProgram(p);
    if (!gl.getProgramParameter(p, gl.LINK_STATUS)) {
      throw new Error('Enlace: ' + gl.getProgramInfoLog(p));
    }
    return p;
  }

  /* --------------------- Matrices (column-major, mat4) ------------------- */
  function perspectiva(fovY, aspect, cerca, lejos) {
    var f = 1 / Math.tan(fovY / 2);
    var nf = 1 / (cerca - lejos);
    return new Float32Array([
      f / aspect, 0, 0, 0,
      0, f, 0, 0,
      0, 0, (lejos + cerca) * nf, -1,
      0, 0, 2 * lejos * cerca * nf, 0
    ]);
  }

  function mirarA(ojo, centro, arriba) {
    var zx = ojo[0] - centro[0], zy = ojo[1] - centro[1], zz = ojo[2] - centro[2];
    var zl = Math.hypot(zx, zy, zz); zx /= zl; zy /= zl; zz /= zl;
    var xx = arriba[1] * zz - arriba[2] * zy,
        xy = arriba[2] * zx - arriba[0] * zz,
        xz = arriba[0] * zy - arriba[1] * zx;
    var xl = Math.hypot(xx, xy, xz); xx /= xl; xy /= xl; xz /= xl;
    var yx = zy * xz - zz * xy, yy = zz * xx - zx * xz, yz = zx * xy - zy * xx;
    return new Float32Array([
      xx, yx, zx, 0,
      xy, yy, zy, 0,
      xz, yz, zz, 0,
      -(xx * ojo[0] + xy * ojo[1] + xz * ojo[2]),
      -(yx * ojo[0] + yy * ojo[1] + yz * ojo[2]),
      -(zx * ojo[0] + zy * ojo[1] + zz * ojo[2]), 1
    ]);
  }

  /* ------------------------------ Geometria ------------------------------ */
  var SEG_X = 72, SEG_Z = 52;           // malla mas ligera; el paisaje se lee igual
  var ANCHO = 96, LARGO = 82;           // x: -48..48, z: -72..10

  var progC, progT, progL, bufCielo, bufTerreno, bufTriangulos, totalTriangulos, bufLuces, totalLuces;
  var uT = {}, uL = {}, uC = {};
  var aClipLoc = -1, aPosLoc = -1, aBaseLoc = -1;
  try {
    progC = programa(VS_CIELO, FS_CIELO);
    progT = programa(VS_TERRENO, FS_TERRENO);
    progL = programa(VS_LUCES, FS_LUCES);
    uC.tiempo = gl.getUniformLocation(progC, 'uTiempo');
    uC.claro = gl.getUniformLocation(progC, 'uClaro');
    uT.P = gl.getUniformLocation(progT, 'uP');
    uT.V = gl.getUniformLocation(progT, 'uV');
    uT.tiempo = gl.getUniformLocation(progT, 'uTiempo');
    uT.claro = gl.getUniformLocation(progT, 'uClaro');
    uL.P = gl.getUniformLocation(progL, 'uP');
    uL.V = gl.getUniformLocation(progL, 'uV');
    uL.tiempo = gl.getUniformLocation(progL, 'uTiempo');
    uL.dpr = gl.getUniformLocation(progL, 'uDpr');
    uL.claro = gl.getUniformLocation(progL, 'uClaro');
    aClipLoc = gl.getAttribLocation(progC, 'aClip');
    aPosLoc = gl.getAttribLocation(progT, 'aPos');
    aBaseLoc = gl.getAttribLocation(progL, 'aBase');

    // Cielo: triangulo que cubre toda la pantalla
    bufCielo = gl.createBuffer();
    gl.bindBuffer(gl.ARRAY_BUFFER, bufCielo);
    gl.bufferData(gl.ARRAY_BUFFER,
      new Float32Array([-1, -1, 3, -1, -1, 3]), gl.STATIC_DRAW);

    // Malla del terreno (posiciones x,z)
    var verts = new Float32Array((SEG_X + 1) * (SEG_Z + 1) * 2);
    var k = 0, i, j;
    for (j = 0; j <= SEG_Z; j++) {
      for (i = 0; i <= SEG_X; i++) {
        verts[k++] = (i / SEG_X - 0.5) * ANCHO;
        verts[k++] = 10 - (j / SEG_Z) * LARGO;   // de cerca (10) a lejos (-72)
      }
    }
    // Indices como TRIANGULOS (2 por celda): la superficie rellena
    var idx = [];
    for (j = 0; j < SEG_Z; j++) {
      for (i = 0; i < SEG_X; i++) {
        var a = j * (SEG_X + 1) + i, b = a + 1;
        var c = a + SEG_X + 1, d = c + 1;
        idx.push(a, c, b, b, c, d);
      }
    }
    totalTriangulos = idx.length;

    bufTerreno = gl.createBuffer();
    gl.bindBuffer(gl.ARRAY_BUFFER, bufTerreno);
    gl.bufferData(gl.ARRAY_BUFFER, verts, gl.STATIC_DRAW);

    bufTriangulos = gl.createBuffer();
    gl.bindBuffer(gl.ELEMENT_ARRAY_BUFFER, bufTriangulos);
    gl.bufferData(gl.ELEMENT_ARRAY_BUFFER, new Uint16Array(idx), gl.STATIC_DRAW);

    // Luciernagas: x al azar, z de salida, fase unica
    var N_LUCES = 96;
    var pts = new Float32Array(N_LUCES * 3);
    for (i = 0; i < N_LUCES; i++) {
      pts[i * 3] = (Math.random() - 0.5) * ANCHO * 0.9;
      pts[i * 3 + 1] = Math.random() * 78;
      pts[i * 3 + 2] = Math.random();
    }
    totalLuces = N_LUCES;
    bufLuces = gl.createBuffer();
    gl.bindBuffer(gl.ARRAY_BUFFER, bufLuces);
    gl.bufferData(gl.ARRAY_BUFFER, pts, gl.STATIC_DRAW);
  } catch (e) {
    usarRespaldo();
    return;
  }

  /* ------------------------- Estado e interaccion ------------------------ */
  var raton = { x: 0, y: 0 };        // objetivo normalizado -1..1
  var camara = { x: 0, y: 0 };       // valor suavizado
  var desplazamiento = 0;            // scroll suavizado
  var reducirMovimiento = window.matchMedia
    && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  var visible = true;
  var dpr = Math.min(window.devicePixelRatio || 1, 1.25);
  var esLogin = document.body.classList.contains('cuerpo-login');

  window.addEventListener('pointermove', function (ev) {
    raton.x = (ev.clientX / window.innerWidth) * 2 - 1;
    raton.y = (ev.clientY / window.innerHeight) * 2 - 1;
  }, { passive: true });

  document.addEventListener('visibilitychange', function () {
    visible = !document.hidden;
  });

  function redimensionar() {
    dpr = Math.min(window.devicePixelRatio || 1, 1.25);
    var w = Math.floor(canvas.clientWidth * dpr);
    var h = Math.floor(canvas.clientHeight * dpr);
    if (canvas.width !== w || canvas.height !== h) {
      canvas.width = w;
      canvas.height = h;
      gl.viewport(0, 0, w, h);
    }
  }
  window.addEventListener('resize', redimensionar);

  /* ------------------------------ Render ------------------------------- */
  var inicio = performance.now();

  function pintar(t) {
    var login = esLogin;
    var claro = esClaro() ? 1.0 : 0.0;
    var ojo = [
      camara.x * (login ? 9.0 : 7.0),
      (login ? 7.4 : 6.8) - camara.y * 1.8 + desplazamiento * 2.4 + Math.sin(t * 0.12) * (login ? 0.28 : 0.18),
      login ? 11.0 : 13.5
    ];
    var centro = [camara.x * 2.4, 2.1 - desplazamiento * 1.6 + Math.sin(t * 0.08) * (login ? 0.15 : 0), -26];
    var P = perspectiva(login ? Math.PI / 2.55 : Math.PI / 3.1, canvas.width / Math.max(canvas.height, 1), 0.5, 130);
    var V = mirarA(ojo, centro, [0, 1, 0]);

    if (claro > 0.5) gl.clearColor(0.58, 0.78, 0.92, 1);
    else gl.clearColor(0.004, 0.004, 0.006, 1);
    gl.clear(gl.COLOR_BUFFER_BIT | gl.DEPTH_BUFFER_BIT);

    // 1) Cielo (sin profundidad: es el telon de fondo)
    gl.disable(gl.DEPTH_TEST);
    gl.depthMask(false);
    gl.useProgram(progC);
    gl.uniform1f(uC.tiempo, t);
    gl.uniform1f(uC.claro, claro);
    gl.bindBuffer(gl.ARRAY_BUFFER, bufCielo);
    gl.enableVertexAttribArray(aClipLoc);
    gl.vertexAttribPointer(aClipLoc, 2, gl.FLOAT, false, 0, 0);
    gl.drawArrays(gl.TRIANGLES, 0, 3);

    // 2) Terreno relleno
    gl.enable(gl.DEPTH_TEST);
    gl.depthMask(true);
    gl.useProgram(progT);
    gl.uniformMatrix4fv(uT.P, false, P);
    gl.uniformMatrix4fv(uT.V, false, V);
    gl.uniform1f(uT.tiempo, t);
    gl.uniform1f(uT.claro, claro);
    gl.bindBuffer(gl.ARRAY_BUFFER, bufTerreno);
    gl.enableVertexAttribArray(aPosLoc);
    gl.vertexAttribPointer(aPosLoc, 2, gl.FLOAT, false, 0, 0);
    gl.bindBuffer(gl.ELEMENT_ARRAY_BUFFER, bufTriangulos);
    gl.drawElements(gl.TRIANGLES, totalTriangulos, gl.UNSIGNED_SHORT, 0);

    // 3) Luciernagas / motas de sol (aditivas, sin escribir profundidad)
    gl.enable(gl.BLEND);
    gl.blendFunc(gl.SRC_ALPHA, gl.ONE);
    gl.depthMask(false);
    gl.useProgram(progL);
    gl.uniformMatrix4fv(uL.P, false, P);
    gl.uniformMatrix4fv(uL.V, false, V);
    gl.uniform1f(uL.tiempo, t);
    gl.uniform1f(uL.dpr, dpr);
    gl.uniform1f(uL.claro, claro);
    gl.bindBuffer(gl.ARRAY_BUFFER, bufLuces);
    gl.enableVertexAttribArray(aBaseLoc);
    gl.vertexAttribPointer(aBaseLoc, 3, gl.FLOAT, false, 0, 0);
    gl.drawArrays(gl.POINTS, 0, totalLuces);
    gl.depthMask(true);
    gl.disable(gl.BLEND);
  }

  function cuadro(ahora) {
    requestAnimationFrame(cuadro);
    if (!visible) return;

    // Suavizado de la camara hacia el raton y el scroll
    var objetivoScroll = Math.min(window.scrollY || 0, 900) / 900;
    camara.x += (raton.x - camara.x) * 0.045;
    camara.y += (raton.y - camara.y) * 0.045;
    desplazamiento += (objetivoScroll - desplazamiento) * 0.06;

    pintar((ahora - inicio) / 1000);
  }

  canvas.classList.add('listo');
  redimensionar();
  document.documentElement.addEventListener('granados:tema', function () {
    canvas.classList.add('listo');
  });
  if (reducirMovimiento) {
    // Movimiento reducido: algunos cuadros iniciales para que el compositor
    // tome el lienzo, y se detiene. Se repinta si cambia el tamano.
    var cuadros = 0;
    var estatico = function () {
      redimensionar();
      pintar(0);
      if (++cuadros < 30) requestAnimationFrame(estatico);
    };
    requestAnimationFrame(estatico);
    window.addEventListener('resize', function () {
      if (cuadros >= 30) {
        requestAnimationFrame(function () { redimensionar(); pintar(0); });
      }
    });
    return;
  }
  requestAnimationFrame(cuadro);
})();
