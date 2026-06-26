package app.tok.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * Dunne Supabase REST-client (PostgREST) op basis van OkHttp. Alle datacalls
 * sturen de anon key + het `x-pairing-token` mee; RLS bepaalt de toegang.
 */
class SupabaseRest(
    private val client: OkHttpClient,
    private val json: Json,
) {
    private val jsonMedia = "application/json".toMediaType()

    private fun builder(url: String, token: String?): Request.Builder {
        val b = Request.Builder().url(url)
            .header("apikey", SupabaseConfig.ANON_KEY)
            .header("Authorization", "Bearer ${SupabaseConfig.ANON_KEY}")
        if (!token.isNullOrEmpty()) b.header("x-pairing-token", token)
        return b
    }

    private suspend fun execute(req: Request): String = withContext(Dispatchers.IO) {
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw IOException("HTTP ${resp.code} bij ${req.url}: ${body.take(300)}")
            }
            body
        }
    }

    // ---------------------------------------------------------------- pairing
    suspend fun redeemPairingCode(code: String, deviceName: String?): String {
        val payload = json.encodeToString(RpcRedeem.serializer(), RpcRedeem(code, deviceName))
        val req = builder("${SupabaseConfig.REST_URL}/rpc/tok_redeem_pairing_code", null)
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .post(payload.toRequestBody(jsonMedia))
            .build()
        return decodeScalarString(execute(req))
    }

    suspend fun createPairingCode(token: String, deviceName: String?): String {
        val payload = json.encodeToString(RpcCreateCode.serializer(), RpcCreateCode(deviceName))
        val req = builder("${SupabaseConfig.REST_URL}/rpc/tok_create_pairing_code", token)
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .post(payload.toRequestBody(jsonMedia))
            .build()
        return decodeScalarString(execute(req))
    }

    suspend fun listDevices(token: String): List<DeviceDto> {
        val req = builder("${SupabaseConfig.REST_URL}/rpc/tok_list_devices", token)
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .post("{}".toRequestBody(jsonMedia))
            .build()
        return json.decodeFromString(ListSerializer(DeviceDto.serializer()), execute(req))
    }

    suspend fun revokeDevice(token: String, id: String) {
        val payload = json.encodeToString(RpcRevoke.serializer(), RpcRevoke(id))
        val req = builder("${SupabaseConfig.REST_URL}/rpc/tok_revoke_device", token)
            .header("Content-Type", "application/json")
            .post(payload.toRequestBody(jsonMedia))
            .build()
        execute(req)
    }

    // ------------------------------------------------------------------- pull
    suspend fun fetchBullets(token: String): List<BulletDto> =
        json.decodeFromString(
            ListSerializer(BulletDto.serializer()),
            execute(builder("${SupabaseConfig.REST_URL}/tok_bullets?select=*", token).get().build()),
        )

    suspend fun fetchLabels(token: String): List<LabelDto> =
        json.decodeFromString(
            ListSerializer(LabelDto.serializer()),
            execute(builder("${SupabaseConfig.REST_URL}/tok_labels?select=*", token).get().build()),
        )

    suspend fun fetchBulletLabels(token: String): List<BulletLabelDto> =
        json.decodeFromString(
            ListSerializer(BulletLabelDto.serializer()),
            execute(builder("${SupabaseConfig.REST_URL}/tok_bullet_labels?select=*", token).get().build()),
        )

    // ------------------------------------------------------------------- push
    suspend fun upsertBullets(token: String, bullets: List<BulletDto>) {
        if (bullets.isEmpty()) return
        val body = json.encodeToString(ListSerializer(BulletDto.serializer()), bullets)
        execute(
            builder("${SupabaseConfig.REST_URL}/tok_bullets", token)
                .header("Content-Type", "application/json")
                .header("Prefer", "resolution=merge-duplicates,return=minimal")
                .post(body.toRequestBody(jsonMedia))
                .build(),
        )
    }

    suspend fun deleteBullet(token: String, id: String) {
        execute(builder("${SupabaseConfig.REST_URL}/tok_bullets?id=eq.$id", token).delete().build())
    }

    suspend fun upsertLabels(token: String, labels: List<LabelDto>) {
        if (labels.isEmpty()) return
        val body = json.encodeToString(ListSerializer(LabelDto.serializer()), labels)
        execute(
            builder("${SupabaseConfig.REST_URL}/tok_labels", token)
                .header("Content-Type", "application/json")
                .header("Prefer", "resolution=merge-duplicates,return=minimal")
                .post(body.toRequestBody(jsonMedia))
                .build(),
        )
    }

    suspend fun deleteLabel(token: String, id: String) {
        execute(builder("${SupabaseConfig.REST_URL}/tok_labels?id=eq.$id", token).delete().build())
    }

    /** Vervang de labelkoppelingen van één bullet op de server (delete + insert). */
    suspend fun setBulletLabels(token: String, bulletId: String, labelIds: List<String>) {
        execute(
            builder("${SupabaseConfig.REST_URL}/tok_bullet_labels?bullet_id=eq.$bulletId", token)
                .delete().build(),
        )
        if (labelIds.isEmpty()) return
        val rows = labelIds.map { BulletLabelDto(bulletId, it) }
        val body = json.encodeToString(ListSerializer(BulletLabelDto.serializer()), rows)
        execute(
            builder("${SupabaseConfig.REST_URL}/tok_bullet_labels", token)
                .header("Content-Type", "application/json")
                .header("Prefer", "resolution=merge-duplicates,return=minimal")
                .post(body.toRequestBody(jsonMedia))
                .build(),
        )
    }

    /** PostgREST geeft een scalar-functie terug als JSON-string ("..."); strip die. */
    private fun decodeScalarString(body: String): String {
        val trimmed = body.trim()
        return try {
            json.decodeFromString(String.serializer(), trimmed)
        } catch (_: Exception) {
            trimmed.trim('"')
        }
    }
}
