package com.arjunren.netsurvey.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineeringTest {
    @Test fun `frequency maps to channel and band`() {
        assertEquals(1, WifiChannels.channel(2412))
        assertEquals(14, WifiChannels.channel(2484))
        assertEquals(36, WifiChannels.channel(5180))
        assertEquals(5, WifiChannels.channel(5975))
        assertEquals(WifiBand.GHZ_6, WifiChannels.band(5975))
        assertEquals(null, WifiChannels.channel(1234))
    }

    @Test fun `RSSI thresholds classify boundaries`() {
        val thresholds = RssiThresholds()
        assertEquals(SignalQuality.EXCELLENT, thresholds.classify(-55))
        assertEquals(SignalQuality.GOOD, thresholds.classify(-56))
        assertEquals(SignalQuality.ACCEPTABLE, thresholds.classify(-68))
        assertEquals(SignalQuality.WEAK, thresholds.classify(-73))
        assertEquals(SignalQuality.POOR, thresholds.classify(-81))
    }

    @Test fun `rolling statistics are correct`() {
        val result = requireNotNull(rollingStatistics(listOf(-50, -60, -70)))
        assertNotNull(result)
        assertEquals(-60.0, result.average, 0.001)
        assertEquals(-70, result.minimum)
        assertEquals(-50, result.maximum)
        assertEquals(3, result.count)
    }

    @Test fun `calibration preserves raw through offset operation`() {
        assertEquals(-58, applyCalibration(-60, 2))
        assertEquals(-65, applyCalibration(-60, -5))
    }

    @Test fun `IDW returns exact value at measured coordinate`() {
        val cells = IdwInterpolator.interpolate(listOf(Measurement(0.0, 0.0, -42.0), Measurement(1.0, 1.0, -82.0)), 3, 3)
        assertEquals(-42.0, cells.first().rssi, 0.001)
        assertEquals(-82.0, cells.last().rssi, 0.001)
        assertTrue(cells[4].rssi in -63.0..-61.0)
    }

    @Test fun `wall intersection includes crossing and excludes separated segments`() {
        assertTrue(segmentsIntersect(Point2d(0.0, 0.0), Point2d(1.0, 1.0), Point2d(0.0, 1.0), Point2d(1.0, 0.0)))
        assertFalse(segmentsIntersect(Point2d(0.0, 0.0), Point2d(.2, .2), Point2d(.8, .8), Point2d(1.0, 1.0)))
    }

    @Test fun `propagation loses signal with distance and walls`() {
        val near = predictedRssi(20.0, 5180, 2.0)
        val far = predictedRssi(20.0, 5180, 20.0)
        val walled = predictedRssi(20.0, 5180, 2.0, wallLossDb = 12.0)
        assertTrue(near > far)
        assertEquals(near - 12, walled, .001)
    }

    @Test fun `candidate scoring rewards coverage and penalizes walls`() {
        val weak = listOf(Point2d(.5, .5), Point2d(.6, .5))
        val clear = scoreCandidate(Point2d(.55, .5), weak, emptyList())
        val blocked = scoreCandidate(Point2d(.55, .5), weak, listOf(WallSegment(Point2d(.57, 0.0), Point2d(.57, 1.0), 10.0)))
        assertEquals(2, clear.weakPointsCovered)
        assertTrue(clear.score > blocked.score)
    }

    @Test fun `CSV escaping handles commas quotes and newlines`() {
        assertEquals("simple", csvEscape("simple"))
        assertEquals("\"a,b\"", csvEscape("a,b"))
        assertEquals("\"a\"\"b\"", csvEscape("a\"b"))
        assertEquals("\"a\nb\"", csvEscape("a\nb"))
    }

    @Test fun `ZIP validation rejects traversal and absolute paths`() {
        assertTrue(isSafeZipEntry("floorplans/plan-1.bin"))
        assertFalse(isSafeZipEntry("../database.db"))
        assertFalse(isSafeZipEntry("C:/secrets.txt"))
        assertFalse(isSafeZipEntry("/root/file"))
        assertFalse(isSafeZipEntry("safe/../../bad"))
    }

    @Test fun `share MIME is specific`() {
        assertEquals("application/pdf", shareMime("pdf"))
        assertEquals("image/png", shareMime(".PNG"))
        assertEquals("text/csv", shareMime("csv"))
        assertEquals("application/zip", shareMime("netsurvey"))
    }
}
