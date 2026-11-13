package com.v2ray.ang.handler

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class MirrorVersionTest {

    @Test
    fun parsesTagWithOrWithoutPrefix() {
        assertEquals("1.3.3", parseMirrorVersion("v1.3.3\n"))
        assertEquals("1.3.3", parseMirrorVersion(" 1.3.3 "))
        assertEquals("2", parseMirrorVersion("v2"))
    }

    @Test
    fun rejectsAnythingElse() {
        assertNull(parseMirrorVersion(null))
        assertNull(parseMirrorVersion(""))
        assertNull(parseMirrorVersion("<html>502 Bad Gateway</html>"))
        assertNull(parseMirrorVersion("v1.0.1-test"))
        assertNull(parseMirrorVersion("v1..3"))
    }
}
