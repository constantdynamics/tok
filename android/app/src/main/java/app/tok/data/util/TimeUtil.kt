package app.tok.data.util

import java.time.Instant
import java.time.OffsetDateTime

/** Epoch-millis (UTC) -> ISO-8601 met 'Z' (wat PostgREST voor timestamptz accepteert). */
fun millisToIso(ms: Long): String = Instant.ofEpochMilli(ms).toString()

/** ISO-8601 van Postgres (bv. "2026-06-23T20:59:57.123456+00:00") -> epoch-millis. */
fun isoToMillis(iso: String?): Long {
    if (iso.isNullOrBlank()) return 0L
    return try {
        OffsetDateTime.parse(iso).toInstant().toEpochMilli()
    } catch (_: Exception) {
        try {
            Instant.parse(iso).toEpochMilli()
        } catch (_: Exception) {
            0L
        }
    }
}
