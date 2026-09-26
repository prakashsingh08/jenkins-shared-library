// PHASE 14 — tests for Chunker, in the Phase 11 style.
//
// Tests: src/com/learning/phase14/Chunker.groovy
//
// Pure computation again, so no fake Jenkins is needed. Note how much easier this is
// to test than the parallel step that uses it — which is the argument for keeping the
// arithmetic in a class and the steps in vars/.

import com.learning.phase14.Chunker
import org.junit.Test

import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertTrue

class ChunkerTest {

    @Test
    void splitsEvenlyRoundRobin() {
        def groups = Chunker.split(['a', 'b', 'c', 'd', 'e'], 2)

        assertEquals(2, groups.size())
        assertEquals(['a', 'c', 'e'], groups[0])
        assertEquals(['b', 'd'], groups[1])
    }

    @Test
    void keepsEveryItemExactlyOnce() {
        def items = (1..10).collect { "m${it}" }

        def flattened = Chunker.split(items, 3).flatten()

        assertEquals(items.size(), flattened.size())
        assertTrue(flattened.containsAll(items))
    }

    @Test
    void neverMakesMoreGroupsThanItems() {
        // Asking for 10 branches for 3 modules would otherwise produce empty branches,
        // which show up in the stage view as parallel branches that do nothing.
        assertEquals(3, Chunker.split(['a', 'b', 'c'], 10).size())
    }

    @Test
    void handlesTheAwkwardInputs() {
        assertEquals([], Chunker.split([], 4))
        assertEquals([], Chunker.split(null, 4))
        assertEquals(1, Chunker.split(['a', 'b'], 0).size())   // count < 1 means one group
    }

    @Test
    void buildsAReadableBranchLabel() {
        assertEquals('tests-1 (a, b)', Chunker.label('tests', 0, ['a', 'b']))
    }
}
