package com.maxrave.data.db

import com.eclipsesource.v8.V8
import org.junit.Assert.assertEquals
import org.junit.Test

/** Executes JNI on the device ABI; static ELF inspection alone cannot prove runtime loading. */
class NativeEngineTest {
    @Test fun retainedV8RunsPlayerStyleJavaScript() {
        val engine = V8.createV8Runtime()
        try {
            assertEquals("cba", engine.executeStringScript("'abc'.split('').reverse().join('')"))
            assertEquals(42, engine.executeIntegerScript("[6,7].reduce((a,b) => a*b)"))
        } finally { engine.release() }
    }
}
