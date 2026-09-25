# Comparación local: DocAligner frente al detector OpenCV v18

## Conclusión

**DocAligner, con los modelos y parámetros oficiales probados, no mejora globalmente nuestro detector en este lote. No conviene sustituir OpenCV por él sin adaptación.** La variante predeterminada mejora visualmente el contorno en las dos capturas con una carta en la mano, pero se abstiene en las restantes. La variante de puntos detecta más, aunque recorta la carta en varios casos.

## Datos y metodología

- 30 originales privados del Sony: 28 capturas automáticas y 2 con modo fijo. No son 30 escenas independientes: predominan tomas correlacionadas de cartas dentro de un soporte blanco; hay otras cartas y bordes fuertes alrededor.
- Referencia OpenCV: cuadrilátero realmente utilizado por la app v18 y guardado en cada JSON. Incluye selección/consenso temporal. No se ha sustituido por una aproximación Python del detector.
- Las dos capturas fijas se separan de la comparación automática: no son resultados automáticos de OpenCV.
- Cuatro pesos oficiales DocsaidLab, sin entrenamiento adicional ni cambios de umbral. Imagen completa, sin recorte previo. Los originales son grises del plano Y replicados a tres canales.
- Adaptador ONNXRuntime CPU: preprocesado bilineal256×256, BGR/NCHW/255; heatmaps: umbral0,3, reescalado al original, Otsu, centroide del contorno externo de mayor área. Puntos: presencia>0,5. Adaptado desde el código upstream para evitar dependencias de Capybara; no se presenta como ejecución del paquete completo.
- Control positivo: imagen oficial run_test_card.jpg en color y convertida a gris. Los cuatro modelos devuelven cuatro esquinas en ambas versiones (8/8 controles). Eso comprueba ejecución básica, no precisión en Magic.
- Inspección visual de las tres hojas con30superposiciones. No se ha anotado ground truth de cuatro esquinas: por tanto no hay IoU ni porcentaje de bordes correctos. Devolver cuatro esquinas NO equivale a acertar.

## Resultados

| Variante | Cuadriláteros devueltos /30 | En las28 fotos automáticas | Mediana PC | p95 PC | Peso |
|---|---:|---:|---:|---:|---:|
| lcnet100 | 1/30 | 1/28 | 21.48ms | 22.46ms | 4.77MB |
| fastvit_t8 | 0/30 | 0/28 | 15.90ms | 16.23ms | 13.17MB |
| fastvit_sa24 | 2/30 | 2/28 | 51.77ms | 55.62ms | 83.08MB |
| lcnet050_point | 6/30 | 6/28 | 2.26ms | 2.38ms | 4.91MB |

OpenCV produjo un cuadrilátero en las28lecturas automáticas seleccionadas, **por construcción del corpus**: solo guardamos lecturas aceptadas. Esto NO significa100% de aciertos ni mide las abstenciones de OpenCV antes de capturar. En muchos originales se comprueba que seleccionó parte del soporte y cortó el footer.

Tiempos: PC Windows, ONNXRuntime1.30.0, CPU un hilo, tres calentamientos por modelo; incluyen preparación, inferencia y postprocesado, excluyen carga del modelo y lecturaJPEG. Una medición por foto; orientativos. No hay comparación justa de velocidad con Android/OpenCV ni garantía de estos tiempos en Sony.

## Qué ocurre visualmente

- Feral1790341502047: OpenCV incorpora soporte por encima y corta el footer. Ninguno de los cuatro modelos devuelve cuatro esquinas en esa foto: DocAligner no la rescata.
- En otras Feral, lcnet050_point devuelve cuadriláteros que dejan fuera parte del texto/footer. No contar sus6detecciones como6aciertos.
- Titanic1790341616029 y1790341622363, sostenidas en la mano: fastvit_sa24 aproxima mejor el borde físico que el recorte interior de OpenCV. Son los únicos dos resultados completos de ese modelo.
- La ausencia de color no explica por sí sola todo el fallo: los controles en gris funcionan. No hemos aislado causalmente fondo, posición, tamaño y dominio Magic; el soporte/contexto es una hipótesis, no prueba.

## Decisión siguiente

1. No integrar estos modelos tal cual ni rebajar umbrales solo para producir cuadriláteros.
2. Reparar el modo fijo como control geométrico y registrar zoom/rotación/ID/motivo de invalidación.
3. Para automático: etiquetar esquinas físicas, ampliar escenas y evaluar adaptación específica a cartas o una región de búsqueda explícita del soporte. Un detector genérico de documentos no ha demostrado sustitución directa.
4. Reservar escenas y cartas fuera del ajuste. Medir borde perdido/fondo incluido, rechazo y tiempo; después considerar integración Android.

## Reproducibilidad

Script: scripts/evaluate_docaligner.py
Entorno aislado: build/docaligner-venv
Fuentes: build/docaligner-source (commit3275b0f07f8e99d8c01cb0774dea2549be1416b6)
Modelo/código de referencia: https://github.com/DocsaidLab/DocAligner
Resultados privados: build/docaligner-evaluation-all/results.json ycontrol.json
Superposiciones privadas: sheet-0.jpg, sheet-1.jpg, sheet-2.jpg; rojoOpenCV, verdeLCNet100, cianFastViT-T8, magentaSA24, amarilloLCNet050.
Resumen visual: comparison.jpg; rojoOpenCV, verdeSA24.

```powershell
./build/docaligner-venv/Scripts/python.exe scripts/evaluate_docaligner.py --source build/boundary-v18-live-review --models build/docaligner-models/lcnet100.onnx build/docaligner-models/fastvit_t8.onnx build/docaligner-models/fastvit_sa24.onnx build/docaligner-models/lcnet050_point.onnx --output build/docaligner-evaluation-all
```

### SHA256 de pesos

- lcnet100: `f4117b786e3a18470f3865c93f3c2bd69d9b998edd60f385574a5c665e79594e`
- fastvit_t8: `950070fd4a46e25d46db7c35d0ed77612d9063f70d4543c7c504f0da6a943a89`
- fastvit_sa24: `7f9f5a8935b2eb22b3ee0245d34996063f54562df390d34714af2d76928695bc`
- lcnet050_point: `32d186080ce16442674d4c0eaaaaac878eea289b56a8d1284f05fff1ff42e220`

Licencia del código upstream Apache2.0; pesos descargados desdeIDs declarados por upstream. No se redistribuyen modelos ni se incorporan aAPK; revisar licencias de pesos/dependencias antes de distribución. Fotos no subidas a servicios externos. No códigoAndroid cambiado ni APK nueva.
