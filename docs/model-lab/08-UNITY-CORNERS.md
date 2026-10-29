# Esquinitas: detector de esquinas propio, piloto Unity aislado

Código completo del piloto: research/card-corners. Sin dependencia de Odin ni de la app.
Estado instalación29-10-2026: Unity Hub3.22.5 instalado. Copia ejecutable creada en
`C:/asv/proyectos/2026/mtgfucker/mtgfucker/trainning models/unity`, con scripts y datos
de calibración dentro. Editor2022.3.62f2 descargado de Unity y firma Authenticode válida.
Windows canceló el inicio del instalador («El usuario ha cancelado la operación»);
NO hay editor instalado ni renders ejecutados todavía. Usuario avisado para completar
instalación/activación. No reintentar ni sortear la cancelación automáticamente.
Pin actualizado f1→f2 por parche CVE-2025-59489; ver README del piloto y lanzador.
Nombre elegido por el usuario29-10-2026. Mantener Odin intacto. Integrar en Android
solo desde la rama de refactor, cuyo nombre exacto se confirmará al llegar a esa fase.
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

## Inicio y teachers — 29-10-2026
Solicitado comenzar entrenamiento. Bloqueo inicial (ver instalación actual arriba): Unity Editor no localizado en PATH,
rutas estándar, registro de instaladores ni inventario habitual de Unity Hub. No hay
renders validados; NO iniciar un entrenamiento con las cuatro texturas de calibración
como si fuera un dataset MTG útil. No hay proceso de entrenamiento de Esquinitas activo.

Secuencia de primer experimento:
1. Editor disponible → calibración120renders → overlays/UV, visibilidad pulgar y hashes.
2. Texturas MTG revisadas, agrupadas sin fuga, fondos/materiales variados; conjunto real
   reservado y anotado independientemente. Congelar config/split antes de entrenar.
3. Baseline Esquinitas con GT Unity, corrida300pasos/600segundos para verificar pipeline;
   no atribuir a esa corrida calidad de producción ni prometer igualdad con teachers.
4. Cornelius/FastWeb sobre mismos renders/fotos: guardar SHA de imagen/modelo,
   preprocesado, coordenadas/orientación, presencia y tiempos por teacher. En sintéticos
   medir cada teacher contra GT Unity, incluidas esquinas ocultas. No imitar sus errores.
5. En reales de entrenamiento, pseudoetiquetas candidatas con acuerdo geométrico y
   revisión humana; discrepancias/oclusiones no pasan automáticamente. Preservar fuente
   de etiqueta y condiciones de uso de teachers/pesos. Acuerdo no demuestra exactitud.
6. Experimento separado GT solo vs GT+reales revisadas/teachers, mismo presupuesto,
   mismos grupos y test reservado. No usar las fotos reservadas para pseudoentrenar.

El trainer actual solo consume GT sintético; adapters de teachers, métricas por esquina
visible/oculta y calibración de rechazo siguen pendientes. No es distillation implementada.
