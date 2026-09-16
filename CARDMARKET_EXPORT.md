# Exportación de venta a Cardmarket

La Biblio puede crear CSV compatibles con la extensión abierta
[Cardmarket Bulk Import](https://github.com/PedroPerpetua/cardmarket-bulk-import).

## Flujo seguro

1. En **Biblio**, aplica búsqueda o filtros y pulsa **Exportar para vender en MKM**.
2. Para validar el proceso, elige primero **PRUEBA · 1 copia**. Después se pueden guardar
   lotes agrupados por edición y de un máximo de 100 filas.
3. En Cardmarket abre **Vender → Insertar cartas por edición**, elige la misma edición e
   importa el CSV con la extensión.
4. Revisa nombre, idioma, estado, foil, cantidad y precio. La app no pulsa **Poner en venta**
   ni publica artículos automáticamente.

Los precios se convierten siempre a EUR y se ajustan al estado guardado en la app. Por
seguridad se omiten las filas sin nombre, edición, precio o idioma compatible. Un lote limita
las filas de producto; una fila puede representar varias copias de la misma impresión.

La integración no usa credenciales ni el API privado de Cardmarket. El CSV se guarda mediante
el selector de archivos de Android para que el usuario decida dónde conservarlo o sincronizarlo.
