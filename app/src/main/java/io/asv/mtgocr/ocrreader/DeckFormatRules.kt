package io.asv.mtgocr.ocrreader

data class DeckFormatRule(
    val id: String,
    val label: String,
    val summary: String,
    val minimumMain: Int,
    val maximumSideboard: Int,
    val maximumCopies: Int
)

object DeckFormatRules {
    val all: List<DeckFormatRule> = listOf(
        DeckFormatRule("standard", "Estándar", "60 cartas mínimo · banquillo de hasta 15 · máximo 4 copias salvo tierras básicas · solo colecciones actualmente legales en Estándar.", 60, 15, 4),
        DeckFormatRule("modern", "Modern", "60 cartas mínimo · banquillo de hasta 15 · máximo 4 copias salvo tierras básicas · usa la reserva legal de Modern y su lista de prohibidas.", 60, 15, 4),
        DeckFormatRule("pioneer", "Pioneer", "60 cartas mínimo · banquillo de hasta 15 · máximo 4 copias salvo tierras básicas · cartas legales desde Regreso a Rávnica y productos admitidos.", 60, 15, 4),
        DeckFormatRule("pauper", "Pauper", "60 cartas mínimo · banquillo de hasta 15 · máximo 4 copias salvo tierras básicas · cada carta debe haber sido publicada como común en papel o MTGO.", 60, 15, 4),
        DeckFormatRule("legacy", "Legacy", "60 cartas mínimo · banquillo de hasta 15 · máximo 4 copias salvo tierras básicas · admite casi toda la historia de Magic salvo su lista de prohibidas.", 60, 15, 4),
        DeckFormatRule("vintage", "Vintage", "60 cartas mínimo · banquillo de hasta 15 · máximo 4 copias salvo tierras básicas · las cartas restringidas solo permiten una copia entre mazo y banquillo.", 60, 15, 4),
        DeckFormatRule("commander", "Commander", "100 cartas exactas: 99 + comandante · una copia por nombre salvo tierras básicas · identidad de color del comandante · sin banquillo reglamentario.", 100, 0, 1),
        DeckFormatRule("free", "Libre / casual", "Sin validación de formato. Organiza el mazo principal y el banquillo como prefieras.", 0, Int.MAX_VALUE, Int.MAX_VALUE)
    )

    @JvmStatic fun byId(id: String?): DeckFormatRule = all.firstOrNull { it.id == id } ?: all.last()
}
