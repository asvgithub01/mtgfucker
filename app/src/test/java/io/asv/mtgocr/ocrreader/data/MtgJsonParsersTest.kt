package io.asv.mtgocr.ocrreader.data

import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MtgJsonParsersTest {
    @Test
    fun parsesMoxOpalPrintingFromMtgJsonSet() {
        val json = """
            {
              "meta":{"date":"2026-08-16"},
              "data":{
                "cards":[
                  {"uuid":"other","name":"Other Card","number":"1","rarity":"common","finishes":["nonfoil"],"availability":["paper"],"identifiers":{},"type":"Artifact"},
                  {"uuid":"38428eaf-113e-5a41-b79f-5f738c9599ec","name":"Mox Opal","number":"223","rarity":"mythic","finishes":["foil","nonfoil"],"availability":["mtgo","paper"],"identifiers":{"scryfallId":"6b3a20ac-1860-4513-bb73-35d23b088b04"},"text":"Metalcraft — {T}: Add one mana of any color.","type":"Legendary Artifact"}
                ],
                "code":"MM2",
                "name":"Modern Masters 2015",
                "releaseDate":"2015-05-22"
              }
            }
        """.trimIndent()

        val set = MtgJsonParsers.readSet(Buffer().writeUtf8(json), "mox opal")

        assertEquals("MM2", set.code)
        assertEquals("Modern Masters 2015", set.name)
        assertEquals(1, set.cards.size)
        assertEquals(listOf("foil", "nonfoil"), set.cards.single().finishes)
        assertEquals("6b3a20ac-1860-4513-bb73-35d23b088b04", set.cards.single().scryfallId)
    }

    @Test
    fun parsesRefreshableCardmarketFoilAndNormalPricesForMoxOpal() {
        val uuid = "38428eaf-113e-5a41-b79f-5f738c9599ec"
        val json = """
            {"meta":{"date":"2026-08-16"},"data":{"$uuid":{"paper":{
              "cardmarket":{"buylist":{},"retail":{"foil":{"2026-08-16":215.58},"normal":{"2026-08-16":165.61}},"currency":"EUR"},
              "tcgplayer":{"buylist":{},"retail":{"normal":{"2026-08-16":236.39}},"currency":"USD"}
            }}}}
        """.trimIndent()

        val prices = MtgJsonParsers.readPrices(Buffer().writeUtf8(json), setOf(uuid))

        assertEquals(2, prices.size)
        assertTrue(prices.all { it.provider == "cardmarket" && it.currency == "EUR" })
        assertEquals(215.58, prices.single { it.finish == "foil" }.amount, 0.001)
        assertEquals(165.61, prices.single { it.finish == "normal" }.amount, 0.001)
    }

    @Test
    fun resolvesSpanishNameAndToleratesOcrMistake() {
        val json = """
            {"meta":{"date":"2026-08-16"},"data":{
              "Creeping Corrosion":[{
                "name":"Creeping Corrosion",
                "foreignData":[
                  {"language":"French","name":"Corrosion rampante"},
                  {"language":"Spanish","name":"Corrosión reptante"}
                ]
              }],
              "Mox Opal":[{"name":"Mox Opal","foreignData":[]}]
            }}
        """.trimIndent()

        val resolved = MtgJsonParsers.readBestCardName(
            Buffer().writeUtf8(json),
            "corrupción reptante"
        )

        assertEquals("Creeping Corrosion", resolved?.canonicalName)
        assertEquals("Corrosión reptante", resolved?.displayName)
        assertEquals("Spanish", resolved?.language)
        assertTrue((resolved?.distance ?: 99) <= 3)
    }

    @Test
    fun parsesCompleteSetWhenNoCardNameFilterIsProvided() {
        val json = """
            {"meta":{},"data":{"cards":[
              {"uuid":"one","name":"First Card","number":"1","rarity":"common","finishes":["nonfoil"],"availability":["paper"],"identifiers":{},"type":"Artifact"},
              {"uuid":"two","name":"Second Card","number":"2","rarity":"rare","finishes":["foil","nonfoil"],"availability":["paper"],"identifiers":{},"type":"Creature"}
            ],"code":"TST","name":"Test Set","releaseDate":"2026-01-01"}}
        """.trimIndent()

        val set = MtgJsonParsers.readSet(Buffer().writeUtf8(json))

        assertEquals(2, set.cards.size)
        assertEquals(listOf("First Card", "Second Card"), set.cards.map { it.name })
    }

    @Test
    fun localOcrMatchTreatsDotAndCommaAsEquivalent() {
        val json = """
            {"meta":{},"data":{
              "Asmoranomardicadaistinaculdacar":[{"name":"Asmoranomardicadaistinaculdacar","foreignData":[]}],
              "Kari Zev, Skyship Raider":[{"name":"Kari Zev, Skyship Raider","foreignData":[]}]
            }}
        """.trimIndent()

        val resolved = MtgJsonParsers.readBestLocalOcrCardName(
            Buffer().writeUtf8(json),
            listOf("Kari Zev. Skyship Raider", "texto que no es un nombre")
        )

        assertEquals("Kari Zev, Skyship Raider", resolved?.canonicalName)
        assertEquals(0, resolved?.distance)
    }

    @Test
    fun streamsEnglishAndLocalizedNamesForPredictionIndex() {
        val json = """
            {"meta":{},"data":{
              "Creeping Corrosion":[{
                "name":"Creeping Corrosion",
                "foreignData":[
                  {"language":"Spanish","name":"Corrupci\u00f3n reptante"},
                  {"language":"French","name":"Corrosion rampante"}
                ]
              }],
              "Mox Opal":[{"name":"Mox Opal","foreignData":[]}]
            }}
        """.trimIndent()
        val indexed = mutableListOf<MtgJsonParsers.IndexedCardName>()

        MtgJsonParsers.streamCardNames(Buffer().writeUtf8(json), batchSize = 2) {
            indexed += it
        }

        assertEquals(4, indexed.size)
        assertTrue(indexed.any {
            it.canonicalName == "Creeping Corrosion" &&
                it.displayName == "Corrupci\u00f3n reptante" &&
                it.language == "Spanish"
        })
        assertTrue(indexed.any { it.displayName == "Mox Opal" && it.language == "English" })
    }
}
