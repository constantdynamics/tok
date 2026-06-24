package app.tok.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncMergeTest {

    @Test
    fun `nieuwe server-bullet wordt altijd toegepast`() {
        assertTrue(
            SyncMerge.bulletServerWins(
                localExists = false, localDirty = false, localDeleted = false,
                localUpdatedAt = 0L, serverUpdatedAt = 100L,
            ),
        )
    }

    @Test
    fun `nieuwere server-bullet overschrijft schone lokale`() {
        assertTrue(
            SyncMerge.bulletServerWins(
                localExists = true, localDirty = false, localDeleted = false,
                localUpdatedAt = 100L, serverUpdatedAt = 200L,
            ),
        )
    }

    @Test
    fun `gelijke tijd geeft server voorrang (laatste pull wint)`() {
        assertTrue(
            SyncMerge.bulletServerWins(
                localExists = true, localDirty = false, localDeleted = false,
                localUpdatedAt = 100L, serverUpdatedAt = 100L,
            ),
        )
    }

    @Test
    fun `oudere server-bullet overschrijft niet`() {
        assertFalse(
            SyncMerge.bulletServerWins(
                localExists = true, localDirty = false, localDeleted = false,
                localUpdatedAt = 200L, serverUpdatedAt = 100L,
            ),
        )
    }

    @Test
    fun `lokale dirty wijziging wordt nooit overschreven`() {
        assertFalse(
            SyncMerge.bulletServerWins(
                localExists = true, localDirty = true, localDeleted = false,
                localUpdatedAt = 0L, serverUpdatedAt = 999L,
            ),
        )
    }

    @Test
    fun `lokale pending delete wordt nooit overschreven`() {
        assertFalse(
            SyncMerge.bulletServerWins(
                localExists = true, localDirty = false, localDeleted = true,
                localUpdatedAt = 0L, serverUpdatedAt = 999L,
            ),
        )
    }

    @Test
    fun `label server wint tenzij lokaal pending werk`() {
        assertTrue(SyncMerge.labelServerWins(localExists = false, localDirty = false, localDeleted = false))
        assertTrue(SyncMerge.labelServerWins(localExists = true, localDirty = false, localDeleted = false))
        assertFalse(SyncMerge.labelServerWins(localExists = true, localDirty = true, localDeleted = false))
        assertFalse(SyncMerge.labelServerWins(localExists = true, localDirty = false, localDeleted = true))
    }

    @Test
    fun `lokale rij die van server verdween wordt opgeruimd tenzij pending`() {
        assertTrue(SyncMerge.shouldDeleteLocal(existsOnServer = false, localDirty = false, localDeleted = false))
        assertFalse(SyncMerge.shouldDeleteLocal(existsOnServer = true, localDirty = false, localDeleted = false))
        assertFalse(SyncMerge.shouldDeleteLocal(existsOnServer = false, localDirty = true, localDeleted = false))
        assertFalse(SyncMerge.shouldDeleteLocal(existsOnServer = false, localDirty = false, localDeleted = true))
    }
}
