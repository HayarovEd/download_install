package com.edurda77.download_install

import com.edurda77.download_install.utils.isNewVersionAvailable
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionComparatorTest {

    @Test
    fun `newer minor version returns true`() {
        assertTrue(isNewVersionAvailable("1.011", "1.012"))
        assertTrue(isNewVersionAvailable("1.9", "1.10"))
        assertTrue(isNewVersionAvailable("v1.011", "v1.012"))
    }

    @Test
    fun `newer major or patch version returns true`() {
        assertTrue(isNewVersionAvailable("1.9.9", "2.0.0"))
        assertTrue(isNewVersionAvailable("1.2", "1.2.1"))
    }

    @Test
    fun `same or older version returns false`() {
        assertFalse(isNewVersionAvailable("1.011", "1.011"))
        assertFalse(isNewVersionAvailable("1.012", "1.011"))
        assertFalse(isNewVersionAvailable("2.0", "1.9.9"))
    }

    @Test
    fun `blank or invalid version returns false`() {
        assertFalse(isNewVersionAvailable("", "1.012"))
        assertFalse(isNewVersionAvailable("1.011", ""))
        assertFalse(isNewVersionAvailable("abc", "1.012"))
    }
}
