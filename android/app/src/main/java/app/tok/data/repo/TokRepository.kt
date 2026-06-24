package app.tok.data.repo

import app.tok.data.local.BulletDao
import app.tok.data.local.BulletEntity
import app.tok.data.local.BulletWithLabels
import app.tok.data.local.CrossRefDao
import app.tok.data.local.LabelDao
import app.tok.data.local.LabelEntity
import app.tok.data.prefs.TokPrefs
import app.tok.data.remote.BulletDto
import app.tok.data.remote.LabelDto
import app.tok.data.remote.SupabaseRest
import app.tok.data.util.isoToMillis
import app.tok.data.util.millisToIso
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import java.util.UUID

/**
 * Offline-first bron van waarheid. De UI leest uitsluitend uit Room; elke mutatie
 * wordt lokaal toegepast (met `dirty`/`deleted`-vlaggen) en daarna gesynct naar
 * Supabase. Sync = eerst lokale wijzigingen pushen, daarna de server volledig
 * pullen en reconciliëren (last-write-wins op `updatedAt`).
 */
class TokRepository(
    private val bulletDao: BulletDao,
    private val labelDao: LabelDao,
    private val crossRefDao: CrossRefDao,
    private val rest: SupabaseRest,
    private val prefs: TokPrefs,
    private val scope: CoroutineScope,
) {
    val bullets: Flow<List<BulletWithLabels>> = bulletDao.observeWithLabels()
    val labels: Flow<List<LabelEntity>> = labelDao.observe()
    val isPaired: Flow<Boolean> = prefs.token.map { !it.isNullOrEmpty() }

    val syncError = MutableStateFlow<String?>(null)
    val syncing = MutableStateFlow(false)

    private val syncMutex = Mutex()
    private fun now() = System.currentTimeMillis()

    // ------------------------------------------------------------- pairing
    suspend fun pair(code: String): Result<Unit> = runCatching {
        val token = rest.redeemPairingCode(code.trim(), prefs.deviceNameOnce())
        prefs.setToken(token)
        syncNow()
    }

    suspend fun unpair() = prefs.setToken(null)

    suspend fun createPairingCode(): Result<String> = runCatching {
        val token = prefs.tokenOnce() ?: error("Niet gekoppeld")
        rest.createPairingCode(token, null)
    }

    // ------------------------------------------------------------- bullets
    suspend fun addBullet(text: String): String {
        val t = now()
        val minOrder = bulletDao.getAllActive().minOfOrNull { it.sortOrder } ?: 0.0
        val bullet = BulletEntity(
            id = UUID.randomUUID().toString(),
            text = text.trim(),
            createdAt = t,
            updatedAt = t,
            sortOrder = minOrder - 1.0, // nieuwste bovenaan
            isArchived = false,
            dirty = true,
        )
        bulletDao.upsert(bullet)
        triggerSync()
        return bullet.id
    }

    suspend fun updateBulletText(id: String, text: String) {
        val b = bulletDao.getById(id) ?: return
        bulletDao.upsert(b.copy(text = text, updatedAt = now(), dirty = true))
        triggerSync()
    }

    suspend fun setArchived(ids: Collection<String>, archived: Boolean) {
        for (id in ids) {
            val b = bulletDao.getById(id) ?: continue
            bulletDao.upsert(b.copy(isArchived = archived, updatedAt = now(), dirty = true))
        }
        triggerSync()
    }

    suspend fun reorderBullet(id: String, newSortOrder: Double) {
        val b = bulletDao.getById(id) ?: return
        bulletDao.upsert(b.copy(sortOrder = newSortOrder, updatedAt = now(), dirty = true))
        triggerSync()
    }

    suspend fun deleteBullets(ids: Collection<String>) {
        for (id in ids) {
            val b = bulletDao.getById(id) ?: continue
            bulletDao.upsert(b.copy(deleted = true, dirty = true, updatedAt = now()))
        }
        triggerSync()
    }

    suspend fun setBulletLabels(bulletId: String, labelIds: List<String>) {
        crossRefDao.setForBullet(bulletId, labelIds)
        val b = bulletDao.getById(bulletId) ?: return
        bulletDao.upsert(b.copy(updatedAt = now(), dirty = true))
        triggerSync()
    }

    // ------------------------------------------------------------- labels
    suspend fun addLabel(name: String, color: String): String {
        val label = LabelEntity(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            color = color,
            createdAt = now(),
            dirty = true,
        )
        labelDao.upsert(label)
        triggerSync()
        return label.id
    }

    suspend fun updateLabel(id: String, name: String, color: String) {
        val l = labelDao.getAll().firstOrNull { it.id == id } ?: return
        labelDao.upsert(l.copy(name = name.trim(), color = color, dirty = true))
        triggerSync()
    }

    suspend fun deleteLabel(id: String) {
        val l = labelDao.getAll().firstOrNull { it.id == id } ?: return
        crossRefDao.clearForLabel(id)
        labelDao.upsert(l.copy(deleted = true, dirty = true))
        triggerSync()
    }

    // ------------------------------------------------------------- sync
    fun triggerSync() {
        scope.launch { syncNow() }
    }

    suspend fun syncNow() {
        val token = prefs.tokenOnce()
        if (token.isNullOrEmpty()) return
        if (!syncMutex.tryLock()) return // al een sync bezig
        try {
            syncing.value = true
            pushLocal(token)
            pullRemote(token)
            prefs.setLastSync(now())
            syncError.value = null
        } catch (e: Exception) {
            syncError.value = e.message
        } finally {
            syncing.value = false
            syncMutex.unlock()
        }
    }

    private suspend fun pushLocal(token: String) {
        // 1) labels
        val dirtyLabels = labelDao.getDirty()
        if (dirtyLabels.isNotEmpty()) {
            rest.upsertLabels(token, dirtyLabels.map { LabelDto(it.id, it.name, it.color, millisToIso(it.createdAt)) })
            dirtyLabels.forEach { labelDao.clearDirty(it.id) }
        }
        // 2) bullets + hun labelkoppelingen
        val dirtyBullets = bulletDao.getDirty()
        if (dirtyBullets.isNotEmpty()) {
            rest.upsertBullets(token, dirtyBullets.map { it.toDto() })
            for (b in dirtyBullets) {
                rest.setBulletLabels(token, b.id, crossRefDao.forBullet(b.id).map { it.labelId })
                bulletDao.clearDirty(b.id)
            }
        }
        // 3) verwijderingen (server cascade ruimt koppelingen op)
        for (b in bulletDao.getDeleted()) {
            rest.deleteBullet(token, b.id)
            crossRefDao.clearForBullet(b.id)
            bulletDao.hardDelete(b.id)
        }
        for (l in labelDao.getDeleted()) {
            rest.deleteLabel(token, l.id)
            crossRefDao.clearForLabel(l.id)
            labelDao.hardDelete(l.id)
        }
    }

    private suspend fun pullRemote(token: String) {
        val serverLabels = rest.fetchLabels(token)
        val serverBullets = rest.fetchBullets(token)
        val serverRefs = rest.fetchBulletLabels(token)

        // labels
        val localLabels = labelDao.getAll().associateBy { it.id }
        val serverLabelIds = serverLabels.map { it.id }.toSet()
        for (s in serverLabels) {
            val local = localLabels[s.id]
            if (local == null || (!local.dirty && !local.deleted)) {
                labelDao.upsert(LabelEntity(s.id, s.name, s.color, isoToMillis(s.createdAt)))
            }
        }
        for (l in localLabels.values) {
            if (l.id !in serverLabelIds && !l.dirty && !l.deleted) {
                crossRefDao.clearForLabel(l.id)
                labelDao.hardDelete(l.id)
            }
        }

        // bullets (last-write-wins op updatedAt)
        val localBullets = bulletDao.getAllActive().associateBy { it.id }
        val serverBulletIds = serverBullets.map { it.id }.toSet()
        for (s in serverBullets) {
            val local = localBullets[s.id]
            if (local == null || (!local.dirty && !local.deleted && isoToMillis(s.updatedAt) >= local.updatedAt)) {
                bulletDao.upsert(s.toEntity())
            }
        }
        for (b in localBullets.values) {
            if (b.id !in serverBulletIds && !b.dirty && !b.deleted) {
                crossRefDao.clearForBullet(b.id)
                bulletDao.hardDelete(b.id)
            }
        }

        // labelkoppelingen (alleen voor niet-pending bullets)
        val pending = bulletDao.getDirty().map { it.id }.toSet() + bulletDao.getDeleted().map { it.id }.toSet()
        val refsByBullet = serverRefs.groupBy { it.bulletId }
        for (s in serverBullets) {
            if (s.id in pending) continue
            crossRefDao.setForBullet(s.id, refsByBullet[s.id]?.map { it.labelId } ?: emptyList())
        }
    }
}

private fun BulletEntity.toDto() = BulletDto(
    id = id,
    text = text,
    createdAt = millisToIso(createdAt),
    updatedAt = millisToIso(updatedAt),
    sortOrder = sortOrder,
    isArchived = isArchived,
)

private fun BulletDto.toEntity() = BulletEntity(
    id = id,
    text = text,
    createdAt = isoToMillis(createdAt),
    updatedAt = isoToMillis(updatedAt),
    sortOrder = sortOrder,
    isArchived = isArchived,
    dirty = false,
    deleted = false,
)
