package io.asv.mtgocr.ocrreader

import org.json.JSONObject
import java.io.File

/** Read-only diagnostics, scoped by durable owned-row ID AND library. Never starts workers. */
internal object PostprocessEvidenceStore {
    data class Document(val name:String,val text:String)
    fun read(directory:File,library:String,item:String):List<Document> {
        if(library.isBlank() || item.isBlank() || !directory.isDirectory) return emptyList()
        val base=directory.canonicalFile
        val files=directory.listFiles().orEmpty().filter { it.isFile && it.canonicalFile.parentFile==base }
        fun json(file:File):JSONObject? = if(file.length()>262144) null else
            runCatching { JSONObject(file.readText()) }.getOrNull()
        val keys=files.filter { it.name.endsWith(".link.json") }.mapNotNull { file ->
            if(Thread.currentThread().isInterrupted) return emptyList()
            val link=json(file) ?: return@mapNotNull null
            if(link.optString("library")==library && link.optString("collectionItemId")==item)
                file.name.removeSuffix(".link.json") else null
        }.toSet()
        return files.filter { f -> f.extension=="json" && keys.any { f.name=="$it.json" || f.name.startsWith("$it.") } }
            .sortedByDescending { it.lastModified() }.take(64).mapNotNull { file ->
                if(Thread.currentThread().isInterrupted) return emptyList()
                json(file)?.let { Document(file.name,it.toString(2)) }
            }
    }
}
