package io.asv.mtgocr.ocrreader.app.di

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

/** For query ViewModels without SavedStateHandle. No global graph or eager model creation. */
internal class QueryViewModelFactory<T : ViewModel>(
    private val type: Class<T>,
    private val createModel: () -> T
) : ViewModelProvider.Factory {
    override fun <V : ViewModel> create(modelClass: Class<V>): V {
        require(modelClass == type) { "Unsupported query ViewModel: ${modelClass.name}" }
        return modelClass.cast(createModel())!!
    }
}
