package org.odk.collect.testshared

import org.odk.collect.shared.locks.ChangeLock
import java.util.function.Function

class BooleanChangeLock : ChangeLock {
    private var locked = false

    override fun <T> withLock(function: Function<Boolean, T>): T {
        val acquired = tryLock()

        return try {
            function.apply(acquired)
        } finally {
            unlock()
        }
    }

    override fun tryLock(): Boolean {
        if (locked) {
            return false
        } else {
            locked = true
            return true
        }
    }

    override fun unlock() {
        locked = false
    }
}
