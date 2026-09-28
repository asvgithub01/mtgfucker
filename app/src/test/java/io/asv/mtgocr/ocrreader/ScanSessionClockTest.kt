package io.asv.mtgocr.ocrreader
import org.junit.Assert.*
import org.junit.Test
class ScanSessionClockTest {
    @Test fun countsOnlyActiveIntervalsAndResumeIsIdempotent() {
        val clock=ScanSessionClock()
        assertEquals(0L,clock.elapsed(10000))
        clock.resume(10000);clock.resume(12000)
        assertEquals(5000L,clock.elapsed(15000))
        clock.pause(15000);clock.pause(20000)
        assertEquals(5000L,clock.elapsed(30000))
        clock.resume(30000)
        assertEquals(8000L,clock.elapsed(33000))
    }
    @Test fun restoresAccumulatedTimeWithoutCountingTimeAway() {
        val clock=ScanSessionClock(300000)
        assertEquals("5:00",ScanSessionClock.format(clock.elapsed(900000)))
        clock.resume(900000)
        assertEquals("5:02",ScanSessionClock.format(clock.elapsed(902000)))
    }
    @Test fun formatsMinutesWithoutHourWrapping() {
        assertEquals("0:00",ScanSessionClock.format(-1))
        assertEquals("1:01",ScanSessionClock.format(61000))
        assertEquals("120:00",ScanSessionClock.format(7200000))
    }
}
