package io.asv.mtgocr.ocrreader.domain.launcher

data class LauncherLibrary(val id: String, val name: String)
data class LauncherData(
    val libraries: List<LauncherLibrary>,
    val selectedId: String,
    val backgroundUrl: String?,
    val readable: Boolean = true
)

/** Selection updates only the established preference; never writes collection contents. */
interface LauncherRepository {
    suspend fun load(): LauncherData
    suspend fun select(libraryId: String): LauncherData
}
