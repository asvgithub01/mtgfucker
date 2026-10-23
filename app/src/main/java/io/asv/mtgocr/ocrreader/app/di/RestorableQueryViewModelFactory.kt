package io.asv.mtgocr.ocrreader.app.di

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras

/** Forwards the owner's original extras; never substitutes an empty SavedStateHandle. */
internal class RestorableQueryViewModelFactory<T : ViewModel>(
    private val type: Class<T>,
    private val createModel: (CreationExtras) -> T
) : ViewModelProvider.Factory {
    override fun <V : ViewModel> create(modelClass: Class<V>, extras: CreationExtras): V {
        require(modelClass == type) { "Unsupported query ViewModel: ${modelClass.name}" }
        return modelClass.cast(createModel(extras))!!
    }
}
