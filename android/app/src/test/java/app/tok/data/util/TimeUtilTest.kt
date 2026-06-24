package app.tok.data.util

import org.junit.Assert.assertEquals
import org.junit.Test

class TimeUtilTest {

    @Test
    fun `millis naar iso en terug is verliesvrij`() {
        val ms = 1_750_000_000_123L
        assertEquals(ms, isoToMillis(millisToIso(ms)))
    }

    @Test
    fun `parst postgres timestamptz met offset`() {
        // 2021-01-01T00:00:00Z == 1609459200000
        assertEquals(1_609_459_200_000L, isoToMillis("2021-01-01T00:00:00+00:00"))
    }

    @Test
    fun `parst iso met Z-suffix`() {
        assertEquals(1_609_459_200_000L, isoToMillis("2021-01-01T00:00:00Z"))
    }

    @Test
    fun `lege of onparseerbare invoer geeft 0`() {
        assertEquals(0L, isoToMillis(null))
        assertEquals(0L, isoToMillis(""))
        assertEquals(0L, isoToMillis("geen-datum"))
    }
}
