package io.asv.mtgocr.ocrreader
import android.graphics.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class TripleSnapshotOcrTest {
 @Test fun sameSnapshotReadsJapaneseKoreanChineseAndLatin() {
  val ocr=TripleSnapshotOcr()
  try {
   for((code,lines) in listOf(
    "ja" to listOf("クリーチャーを対象とする", "あなたはカードを１枚引く"),
    "ko" to listOf("생물 하나를 목표로 정한다", "카드 한 장을 뽑는다"),
    "zh" to listOf("你控制的生物获得", "抓一张牌，然后弃一张牌"),
    "pt" to listOf("Adicione uma mana de qualquer cor.", "Compre um card e depois descarte um card."),
    "es" to listOf("Agrega un maná de cualquier color.", "Roba una carta y luego descarta una carta."))) {
    val bitmap=Bitmap.createBitmap(1260,360,Bitmap.Config.ARGB_8888)
    try {
     val canvas=Canvas(bitmap);canvas.drawColor(Color.WHITE)
     val p=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.BLACK;textSize=48f }
     lines.forEachIndexed { i,t->canvas.drawText(t,30f,90f+i*100,p) }
     val result=Tasks.await(ocr.process(InputImage.fromBitmap(bitmap,0)),60,TimeUnit.SECONDS).text
     val diagnostics=java.util.Collections.synchronizedList(mutableListOf<String>())
     ocr.diagnostics={ diagnostics.add(it) }
     val fixedCode=if(code=="zh") "zhs" else code
     val fixed=Tasks.await(ocr.process(InputImage.fromBitmap(bitmap,0),fixedCode),60,TimeUnit.SECONDS).text
     assertTrue("Fixed $fixedCode must read text",fixed.isNotBlank())
     assertEquals(diagnostics.toString(),1,diagnostics.size)
     assertTrue(diagnostics.single().contains("model=${TripleLanguageScope.model(fixedCode)} "))
     ocr.diagnostics={}
     if(code=="ja" || code=="ko") assertEquals(result,code,TripleLanguageEvidence.scriptLanguage(result))
     else if(code=="zh") assertTrue(result,TripleLanguageEvidence.scriptScore(result)>=4)
     else {
      assertTrue(result,result.contains(if(code=="pt") "Adicione" else "Agrega",true))
      val detector=CardTextLanguageDetector(useShortRules=false)
      val latch=java.util.concurrent.CountDownLatch(1)
      var identified:CardTextLanguageResult?=null
      try {
       detector.detectText(result,"und") { identified=it;latch.countDown() }
       assertTrue(latch.await(10,TimeUnit.SECONDS))
       assertEquals(result,code,identified?.languageCode)
       assertTrue(identified.toString(),(identified?.confidence ?: 0f)>=.8f)
      } finally { detector.close() }
     }
    } finally { bitmap.recycle() }
   }
  } finally { ocr.close() }
 }
}
