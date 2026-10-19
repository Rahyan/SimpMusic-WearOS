package com.maxrave.common

import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

/** Propagates across dispatcher switches so imported changes cannot overwrite local intent. */
object ImportedLibraryMutation : AbstractCoroutineContextElement(Key) {
    object Key : CoroutineContext.Key<ImportedLibraryMutation>
}

object LocalLibraryEdits {
    @Volatile var listener: ((category: String, id: String, member: Boolean) -> Unit)? = null
    suspend fun changed(category: String, id: String, member: Boolean) {
        if (coroutineContext[ImportedLibraryMutation.Key] == null) listener?.invoke(category, id, member)
    }
}
