package app.tok.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BulletDto(
    val id: String,
    val text: String = "",
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("sort_order") val sortOrder: Double = 0.0,
    @SerialName("is_archived") val isArchived: Boolean = false,
)

@Serializable
data class LabelDto(
    val id: String,
    val name: String,
    val color: String = "#39FF14",
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class BulletLabelDto(
    @SerialName("bullet_id") val bulletId: String,
    @SerialName("label_id") val labelId: String,
)

@Serializable
data class DeviceDto(
    val id: String,
    @SerialName("device_name") val deviceName: String? = null,
    @SerialName("is_owner") val isOwner: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("expires_at") val expiresAt: String? = null,
)

// ---- RPC-payloads ----
@Serializable
data class RpcRedeem(
    @SerialName("p_code") val code: String,
    @SerialName("p_device_name") val deviceName: String? = null,
)

@Serializable
data class RpcCreateCode(
    @SerialName("p_device_name") val deviceName: String? = null,
)

@Serializable
data class RpcRevoke(
    @SerialName("p_id") val id: String,
)
