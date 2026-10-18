package io.asv.mtgocr.ocrreader
import org.junit.Assert.*
import org.junit.Test
class CardLanguageResolverTest {
    private val empty=TitleLanguageIndex.Evidence()
    @Test fun missingTitleDoesNotVetoRulesAndKnownTitleCannotOverrideConflict() {
        assertEquals("pt",CardLanguageResolver.resolve("Adicione mana",null,empty).code)
        assertEquals("de",CardLanguageResolver.resolve("", "de",empty).code)
        val t=TitleLanguageIndex.Evidence(listOf(TitleLanguageIndex.Match("x","x","x","es")))
        assertTrue(CardLanguageResolver.resolve("Adicione mana",null,t).conflict)
    }
    @Test fun unknownChineseVariantAndAmbiguousStatisticalChunksStayUnknown() {
        assertNull(CardLanguageResolver.resolve("中文文字", "zhs",empty).code)
        assertNull(CardLanguageResolver.statisticalConsensus(listOf("es","pt",null)))
        assertEquals("pt",CardLanguageResolver.statisticalConsensus(listOf("pt",null,"pt")))
        assertNull(CardLanguageResolver.resolve("",null,empty).code)
    }
    @Test fun japaneseKoreanAndLatinModelSelection() {
        assertEquals("ja",CardLanguageResolver.resolve("クリーチャー",null,empty).code)
        assertEquals("ko",CardLanguageResolver.resolve("생물 미이라",null,empty).code)
        val latin=CardLanguageResolver.Reading("latin","Adicione mana",emptyList())
        val korean=CardLanguageResolver.Reading("korean","생물 미이라 생물 미이라",emptyList())
        assertEquals(korean,CardLanguageResolver.select(listOf(latin,korean)))
        assertEquals(latin,CardLanguageResolver.select(listOf(latin,latin.copy(model="chinese"))))
    }
    @Test fun sonyChineseMummyNeedsGrammarAndVariantEvidenceNotJustHan() {
        val rules="如果木乃伊須被送入墳墓場，將其移出遊戲。拔出一隻眼睛，以觀看未來的儀式。"
        assertEquals("zht",CardLanguageResolver.resolve(rules,null,empty).code)
        assertEquals("zhs",CardLanguageResolver.resolve("如果木乃伊须被送入坟墓场，将其移出游戏。拔出一只眼睛，以观看未来的仪式。",null,empty).code)
        assertNull(CardLanguageResolver.chineseRules("黄眼木乃供 不是人")) // blurred Sony photo49
        assertNull(CardLanguageResolver.chineseRules(rules+"将其"))
        assertNull(CardLanguageResolver.chineseRules(rules+"クリーチャー"))
    }
}
