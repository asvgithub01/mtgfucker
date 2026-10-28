# Discriminar edición / impresión

Hipótesis preferida: Odin recupera identidad/arte; segundo estadio compara únicamente
impresiones plausibles usando pie, símbolo, collector number y marco a alta resolución.
No un clasificador cerrado con una clase por edición: se quedaría anticuado con cada set.
Probar reranking de referencias/ROI y OCR selectivo como baseline. Idioma separado.

Tres modelos pequeños no garantizan menos latencia que uno compartido: comparar
cascada bajo demanda vs backbone compartido multihead en Sony. La cascada facilita
datasets/entrenamiento separados y no sacrifica invariancia de Odin al idioma, pero
cuesta memoria, conversiones y errores acumulados. No ejecutar todos en cada frame.

Mismo arte puede repetirse con diferencias ilegibles a distancia. Devolver impresión
desconocida/provisional y pedir otro encuadre; nunca convertir una similitud de arte
en edición confirmada. Evaluar por impresión exacta y grupos de reimpresiones confusas,
incluyendo promos, listas, bordes blanco/negro y símbolos ausentes.

Pendiente: ground truth manual printingUUID, ROI/crop originales, baseline OCR y
prueba offline. Nada entrenado ni integrado de este módulo todavía.
