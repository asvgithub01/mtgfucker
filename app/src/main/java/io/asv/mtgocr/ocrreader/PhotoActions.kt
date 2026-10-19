package io.asv.mtgocr.ocrreader

import android.app.Activity
import android.content.Intent
import androidx.appcompat.app.AlertDialog

object PhotoActions {
    @JvmStatic fun choose(host:Activity,group:String?=null) {
        val options=mutableListOf(host.getString(R.string.photo_take),host.getString(R.string.photo_import_multiple))
        AlertDialog.Builder(host).setItems(options.toTypedArray()) { _,which ->
            host.startActivity(Intent(host,PhotoImportActivity::class.java).putExtra("camera",which==0).putExtra("group",group))
            if(host is PhotoScanDetailActivity) host.finish()
        }.show()
    }
}
