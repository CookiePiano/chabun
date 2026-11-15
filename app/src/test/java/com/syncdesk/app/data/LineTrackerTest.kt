package com.syncdesk.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LineTrackerTest {

    private var now = 0L
    private val tracker = LineTracker(clock = { now })

    @Test
    fun aLineEndsWhenItHasPlayed() {
        var finished = 0
        tracker.begin { finished++ }
        now = 50
        tracker.started()
        now = 2_400
        assertTrue(tracker.ended())
        assertEquals(1, finished)
    }

    @Test
    fun theDuplicateEndOfTheLastLineDoesNotEndTheNextOne() {
        var first = 0
        var second = 0
        tracker.begin { first++ }
        tracker.started()
        now = 2_400
        assertTrue(tracker.ended())
        // The next line is asked for, then the engine's second end for the first line arrives.
        tracker.begin { second++ }
        assertFalse(tracker.ended())
        assertEquals(0, second)
        // Only an end after this line has started counts.
        now = 2_450
        tracker.started()
        now = 4_600
        assertTrue(tracker.ended())
        assertEquals(1, first)
        assertEquals(1, second)
    }

    @Test
    fun anEndRightAfterAskingIsTooEarlyToBeReal() {
        var finished = 0
        tracker.begin { finished++ }
        tracker.started()
        now = 100
        assertFalse(tracker.ended())
        now = 300
        assertTrue(tracker.ended())
        assertEquals(1, finished)
    }

    @Test
    fun anErrorEndsTheLineWhateverItsState() {
        var finished = 0
        tracker.begin { finished++ }
        tracker.failed()
        tracker.failed()
        assertEquals(1, finished)
    }

    @Test
    fun eventsForAnAbandonedLineAreIgnored() {
        var finished = 0
        tracker.begin { finished++ }
        tracker.started()
        tracker.abandon()
        now = 5_000
        assertFalse(tracker.ended())
        tracker.failed()
        assertEquals(0, finished)
    }
}
