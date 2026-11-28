package com.plateengine.sample

import com.plateengine.sample.watcher.NameRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class NameRulesTest {
    private val t = Calendar.getInstance().apply { set(2026, Calendar.NOVEMBER, 23, 8, 37, 0) }.timeInMillis

    @Test fun newName() {
        assertEquals("20261123083700_67너7570.jpg", NameRules.newName(t, "67너7570", true, "IMG_001.JPG"))
        assertEquals("2026112308370067너7570.png", NameRules.newName(t, "67너7570", false, "a.png"))
    }

    @Test fun unique() {
        assertEquals("a.jpg", NameRules.unique("a.jpg", setOf("b.jpg")))
        assertEquals("a_2.jpg", NameRules.unique("a.jpg", setOf("a.jpg")))
        assertEquals("a_3.jpg", NameRules.unique("a.jpg", setOf("a.jpg", "a_2.jpg")))
    }

    @Test fun exif() {
        assertEquals(t / 1000, NameRules.parseExif("2026:11:23 08:37:00")!! / 1000)
        assertNull(NameRules.parseExif(""))
        assertNull(NameRules.parseExif("garbage"))
    }

    @Test fun images() {
        assertTrue(NameRules.isImage("IMG_1.JPG", "image/jpeg"))
        assertTrue(NameRules.isImage("x.webp", ""))
        assertFalse(NameRules.isImage("처리기록.csv", "text/csv"))
        assertFalse(NameRules.isImage(".pending-123.jpg", "image/jpeg"))
    }

    @Test fun csv() {
        assertEquals("a,\"b,c\",\"d\"\"e\"\r\n", NameRules.csvRow(listOf("a", "b,c", "d\"e")))
    }
}
