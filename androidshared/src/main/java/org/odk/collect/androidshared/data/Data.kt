package org.odk.collect.androidshared.data

import kotlinx.coroutines.flow.StateFlow
import kotlin.reflect.KProperty

class Data<T>(private val appState: AppState, private val key: String, private val default: T) {
    fun flow(qualifier: String? = null): StateFlow<T> {
        return appState.getFlow("$qualifier:$key", default)
    }

    fun set(qualifier: String?, value: T) {
        appState.setFlow("$qualifier:$key", value)
    }

    fun set(value: T) {
        set(null, value)
    }
}

class DataUpdater<T>(private val data: Data<T>, private val updater: (String?) -> T) {
    fun update(qualifier: String? = null) {
        data.set(qualifier, updater(qualifier))
    }
}

abstract class DataService(private val appState: AppState, private val onUpdate: (() -> Unit)? = null) {

    private val dataUpdaters = mutableListOf<DataUpdater<*>>()

    fun update(qualifier: String? = null) {
        dataUpdaters.forEach { it.update(qualifier) }
        onUpdate?.invoke()
    }

    protected fun <T> data(key: String, default: T): DataDelegate<T> {
        val data = Data(appState, key, default)
        return DataDelegate(data)
    }

    protected fun <T> data(key: String, default: T, updater: () -> T): DataDelegate<T> {
        val data = attachData(key, default) { updater() }
        return DataDelegate(data)
    }

    protected fun <T> qualifiedData(key: String, default: T, updater: (String) -> T): DataDelegate<T> {
        val data = attachData(key, default) { updater(it!!) }
        return DataDelegate(data)
    }

    private fun <T> attachData(key: String, default: T, updater: (String?) -> T): Data<T> {
        val data = Data(appState, key, default)
        dataUpdaters.add(DataUpdater(data, updater))
        return data
    }

    class DataDelegate<T>(private val data: Data<T>) {
        operator fun getValue(thisRef: Any?, property: KProperty<*>): Data<T> {
            return data
        }
    }
}
