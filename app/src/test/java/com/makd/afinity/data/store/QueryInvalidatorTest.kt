package com.makd.afinity.data.store

import app.cash.turbine.test
import com.makd.afinity.testutil.TestMediaFixtures.testUuid
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class QueryInvalidatorTest {

    @Test
    fun invalidateEmitsNonEmptyKeySetsAndIgnoresEmptySets() = runTest {
        val invalidator = QueryInvalidator()
        val id1 = testUuid(1)
        val id2 = testUuid(2)
        val id3 = testUuid(3)

        invalidator.invalidations.test {
            invalidator.invalidate(emptySet())
            expectNoEvents()

            val batch1 =
                setOf(
                    QueryKey.BoxSetChildren(id1),
                    QueryKey.ContainingBoxSets(id2),
                )
            invalidator.invalidate(batch1)
            assertEquals(batch1, awaitItem())

            val batch2 = setOf(QueryKey.PlaylistEntries(id3))
            invalidator.invalidate(batch2)
            assertEquals(batch2, awaitItem())
        }
    }
}
