package io.asv.mtgocr.ocrreader
import android.content.Context
import android.widget.Toast
import io.asv.mtgocr.ocrreader.data.LegacyCollectionStore
/** The same saved-card action in library, detail and gallery; no URL-only phantom selection. */
object CardBackgroundAction {
    @JvmStatic fun pin(context:Context,itemId:String) {
        val card=LegacyCollectionStore.cards(context).firstOrNull { it.collectionItemId==itemId } ?: return
        val message=when {
            !PremiumAccess.isEnabled(context)->R.string.premium_required
            card.imgPath.isNullOrBlank()->R.string.card_has_no_background_image
            else->{ LibraryCatalog.pinBackground(context,itemId);R.string.launch_background_pinned }
        }
        Toast.makeText(context,message,Toast.LENGTH_SHORT).show()
    }
}
