package com.edurda77.download_install

import com.edurda77.download_install.utils.isNewVersionAvailable
import com.edurda77.download_install.utils.shouldNotifyUpdate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionComparatorTest {

    @Test
    fun `newer minor version returns true`() {
        assertTrue(isNewVersionAvailable("1.011", "1.012"))
        assertTrue(isNewVersionAvailable("1.9", "1.10"))
        assertTrue(isNewVersionAvailable("v1.011", "v1.012"))
        assertTrue(isNewVersionAvailable("1.001", "1.01"))
        assertTrue(isNewVersionAvailable("1.005", "1.01"))
        assertTrue(isNewVersionAvailable("1.01", "1.011"))
        assertTrue(isNewVersionAvailable("1.01", "1.1"))
        assertTrue(isNewVersionAvailable("1.011", "1.1"))
        assertTrue(isNewVersionAvailable("1.1", "2.1"))
        assertTrue(isNewVersionAvailable("2.01", "2.1"))
        assertTrue(isNewVersionAvailable("1.001", "2.1"))
    }

    @Test
    fun `newer major or patch version returns true`() {
        assertTrue(isNewVersionAvailable("1.9.9", "2.0.0"))
        assertTrue(isNewVersionAvailable("1.2", "1.2.1"))
        assertTrue(isNewVersionAvailable("1.0.1", "1.01"))
    }

    @Test
    fun `same or older version returns false`() {
        assertFalse(isNewVersionAvailable("1.011", "1.011"))
        assertFalse(isNewVersionAvailable("1.01", "1.010"))
        assertFalse(isNewVersionAvailable("1.01", "1.005"))
        assertFalse(isNewVersionAvailable("1.01", "1.001"))
        assertFalse(isNewVersionAvailable("1.1", "1.011"))
        assertFalse(isNewVersionAvailable("2.1", "2.01"))
        assertFalse(isNewVersionAvailable("1.012", "1.011"))
        assertFalse(isNewVersionAvailable("2.0", "1.9.9"))
    }

    @Test
    fun `blank or invalid version returns false`() {
        assertFalse(isNewVersionAvailable("", "1.012"))
        assertFalse(isNewVersionAvailable("1.011", ""))
        assertFalse(isNewVersionAvailable("abc", "1.012"))
    }

    @Test
    fun `notification is skipped when already notified for same or higher version`() {
        assertTrue(shouldNotifyUpdate("1.011", "1.012", null))
        assertFalse(shouldNotifyUpdate("1.011", "1.012", "1.012"))
        assertFalse(shouldNotifyUpdate("1.011", "v1.012", "1.012"))
        assertFalse(shouldNotifyUpdate("1.011", "1.011.5", "1.012"))
        assertFalse(shouldNotifyUpdate("1.001", "1.005", "1.01"))
        assertFalse(shouldNotifyUpdate("1.001", "1.011", "1.1"))
    }

    @Test
    fun `notification is shown only when server version exceeds last notified version`() {
        assertTrue(shouldNotifyUpdate("1.011", "1.013", "1.012"))
        assertTrue(shouldNotifyUpdate("1.001", "1.011", "1.01"))
        assertTrue(shouldNotifyUpdate("1.001", "2.1", "1.01"))
        assertTrue(shouldNotifyUpdate("1.011", "1.012", "1.012", forceNotify = true))
    }
}
