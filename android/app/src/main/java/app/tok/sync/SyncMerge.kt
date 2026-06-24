package app.tok.sync

/**
 * Pure beslisregels voor het reconciliëren van lokale (Room) en server (Supabase)
 * staat. Geen Android-/IO-afhankelijkheden, zodat dit met gewone JVM-unittests
 * te testen is (zie src/test/.../SyncMergeTest.kt).
 */
object SyncMerge {

    /**
     * Mag de serverversie van een bullet de lokale overschrijven? Last-write-wins op
     * updatedAt, maar nooit als er lokaal nog niet-gepushte wijzigingen of een
     * pending verwijdering zijn.
     */
    fun bulletServerWins(
        localExists: Boolean,
        localDirty: Boolean,
        localDeleted: Boolean,
        localUpdatedAt: Long,
        serverUpdatedAt: Long,
    ): Boolean =
        !localExists || (!localDirty && !localDeleted && serverUpdatedAt >= localUpdatedAt)

    /**
     * Mag de serverversie van een label de lokale overschrijven? Labels hebben geen
     * updatedAt; we nemen de server tenzij er lokaal pending werk is.
     */
    fun labelServerWins(
        localExists: Boolean,
        localDirty: Boolean,
        localDeleted: Boolean,
    ): Boolean =
        !localExists || (!localDirty && !localDeleted)

    /**
     * Moet een lokale rij die niet (meer) op de server staat lokaal opgeruimd worden?
     * Alleen als er geen pending lokale wijziging/verwijdering op rust.
     */
    fun shouldDeleteLocal(
        existsOnServer: Boolean,
        localDirty: Boolean,
        localDeleted: Boolean,
    ): Boolean =
        !existsOnServer && !localDirty && !localDeleted
}
