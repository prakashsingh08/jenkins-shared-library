// PHASE 14 — splitting work into a fixed number of parallel branches.
//
// Why this exists: one branch per module sounds obvious and is usually wrong. Forty
// modules on a two-CPU agent means forty lots of container start-up and no extra
// speed. You want a small number of branches, each doing several modules.
//
// Pure computation — no steps, no Jenkins — so it is trivially testable
// (test/phase14/ChunkerTest.groovy) and safe under CPS: only plain Lists come back,
// and nothing exotic is ever alive across a step boundary.

package com.learning.phase14

class Chunker implements Serializable {

    /**
     * Splits `items` into at most `count` groups, as evenly as it can.
     *
     *   split(['a','b','c','d','e'], 2)  →  [['a','c','e'], ['b','d']]
     *
     * Round-robin rather than consecutive slices, because the items at the end of a
     * list are often the slow ones (the big module, the long test suite) and dealing
     * them out keeps the branches closer in duration.
     */
    static List<List> split(List items, int count) {
        if (!items) {
            return []
        }
        if (count < 1) {
            count = 1
        }

        int groups = Math.min(count, items.size())
        List<List> result = []

        for (int g = 0; g < groups; g++) {
            result << []
        }

        for (int i = 0; i < items.size(); i++) {
            result[i % groups] << items[i]
        }

        return result
    }

    /**
     * A branch name that stays readable in the stage view.
     * Jenkins shows these as the parallel branch labels, so 'tests-1 (a, b)' is far
     * more useful at 2am than 'branch3'.
     */
    static String label(String prefix, int index, List items) {
        return "${prefix}-${index + 1} (${items.join(', ')})"
    }
}
