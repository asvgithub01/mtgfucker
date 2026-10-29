# Uso experimental privado

- Diego: https://github.com/diegovazquez/mtg_train_card_identification_embeddings
  Pesos FP32 locales con procedencia fijada en build/dhvazquez-models/provenance.json.
  No presuponer licencia comercial ni publicar pesos/APK sin revisar condiciones.
- OpenCV Android: https://opencv.org/ (Apache-2.0).
- ONNX Runtime: https://github.com/microsoft/onnxruntime (MIT).
- Milo y Cornelius se reutilizan de archivos locales de CollectorVision, sin redistribuirlos en
  Git. Consultar `../../collectorvision-native/NOTICE.md` y las condiciones de
  modelos/datos de upstream antes de cualquier distribución. No se presume
  licencia comercial ni que aislar una APK elimine obligaciones de terceros.
- Las imágenes de cartas proceden del corpus existente y Scryfall. Arte y cartas
  pertenecen a sus titulares (Wizards of the Coast y artistas); esto no concede
  derechos de redistribución. Fotos del usuario: material privado.
- `Preprocess.java` implementa el contrato matemático separable RGB de Pillow
  (filtro cúbico a=-0.5, antialias, coeficientes22bits y redondeo entre ejes).
  Referencia técnica: https://github.com/python-pillow/Pillow/blob/12.3.0/src/libImaging/Resample.c
  Se conserva licencia de Pillow en `PILLOW-LICENSE.txt`.

No publicar el APK ni subir su carpeta de assets/resultados a Git o a un hosting.
