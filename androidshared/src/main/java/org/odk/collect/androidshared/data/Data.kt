package org.odk.collect.androidshared.data

import kotlinx.coroutines.flow.StateFlow

class Data<T>(private val appState: AppState, private val key: String, private val default: T) {
    fun get(qualifier: String? = null): StateFlow<T> {
        return appState.getFlow("$qualifier:$key", default)
    }

    fun set(qualifier: String?, value: T) {
        appState.setFlow("$qualifier:$key", value)
    }

    fun set(value: T) {
        set(null, value)
    }
}

fun <T> AppState.getData(key: String, default: T): Data<T> {
    return Data(this, key, default)
}
