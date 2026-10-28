# Detector de esquinas propio: piloto Unity aislado

Código completo del piloto: research/card-corners. Sin dependencia de Odin ni de la app.
Objetivo inicial: **una carta completa** → presencia+cuatro esquinas ordenadas. Más
adelante multitarjeta requiere otra salida/arquitectura; este piloto NO la implementa.

Preparado: proyecto Unity Built-in, generador editor con semilla, textura por carta,
rotación XYZ, posición/distancia, luz, fondo procedural, funda y foil aproximados,
10%negativos. Export PNG+JSON con esquinas exactas proyectadas y hashes, config,
grupo/split/versión. Validador y overlays; baseline CNN pequeña y corrida acotada CUDA.
Sin teachers para sintéticos: la geometría del render es GT. No se copia código/pesos
Cornelius/FastWeb. Teachers reales solo proponen etiquetas, con revisión independiente.

**Estado real:** Unity no localizado en rutas estándar de este equipo. Código generado,
no compilado/renderizado en Unity todavía; no entrenamiento de detector arrancado ni
resultado de precisión. Pruebas Python no equivalen a validar Unity/realismo visual.

Limitaciones deliberadas: una superficie plana rectangular (sin redondeo/curvatura),
fundas sin refracción física, foil no espectral, fondos procedurales, pulgar elipsoidal
aproximado (no mano realista) y sin motion blur. No vender como dataset fotorealista. Primero validar UV/orientación
y esquinas con overlays; después ampliar materiales/fondos reales con permiso, sombras,
manos/oclusiones más realistas, bordes redondeados, ruido/blur/exposición,
distorsión de lente y álbum. Cartas tapadas requieren etiqueta visible/occluded, no
inventar etiquetas de esquinas ocultas en el test humano: usar una captura emparejada
sin dedo en la misma pose o anotación con incertidumbre.

## Esquinas ocultas: requisito añadido
El detector debe intentar estimar el cuadrilátero completo aunque un pulgar tape una
esquina. Unity conserva el GT geométrico de las cuatro y anota visibilidad por esquina
(schema2, raycast contra malla del pulgar). Nunca sustituir esquina por borde del dedo.
El trainer supervisa también coordenadas ocultas. Siguiente calibración: mezclar
sin oclusión/una esquina/borde/dos esquinas, tamaños y poses de manos, y medir error
de ocultas separado del de visibles. No asumir que una tarjeta casi tapada es recuperable;
confianza/rechazo quedan pendientes, no hay red entrenada ni mecanismo validado aún.
No confundir esquina dentro de imagen pero tapada con esquina fuera del encuadre:
este piloto mantiene toda la geometría dentro de imagen. El segundo caso es otra prueba.

Todos los artefactos de un dataset deben estar juntos: sources/ (texturas+procedencia),
configs/, renders/<run>/{PNG,JSON,config,COMPLETE}, reviews/, splits/, runs/, reports/.
Datos en C:/asv/datasets/mtg-corners-v1, fuera de Git; no borrar ni mover Odin.
Copiar texturas autorizadas al área sources para portabilidad (no modificar originales).
Separar arte/oracle/duplicados y fondos entre particiones antes de renderizar. Un cambio
de seed no hace independiente una validación que comparte la misma textura.

Para retomar: instalar/seleccionar Unity2022.3 y ejecutar README del piloto (no -nographics);
revisar24overlays, validar120renders de prueba, fijar primer conjunto real reservado.
Solo entonces renderizar piloto mayor y entrenar300pasos. Comparar esquina normalizada,
IoU contra GT humana, rechazo, identificación Odin como métrica secundaria y latencia
Sony real. Export ONNX/paridad/Android son fases posteriores, sin promoción automática.
