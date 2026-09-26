// PHASE 12 — tests for the Phase 12 class, written in the Phase 11 style.
//
// Tests: src/com/learning/phase12/VersionParser.groovy
//
// No fake Jenkins at all. Pure-computation methods are the easiest thing in any
// library to test, which is itself an argument for keeping them separate from steps.

import com.learning.phase12.VersionParser
import org.junit.Test

import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertFalse
import static org.junit.Assert.assertTrue

class VersionParserTest {

    // ---- ticketFrom -----------------------------------------------------------

    @Test
    void findsATicketInABranchName() {
        assertEquals('PROJ-123', VersionParser.ticketFrom('feature/PROJ-123-add-login'))
    }

    @Test
    void returnsNoneWhenThereIsNoTicket() {
        assertEquals('none', VersionParser.ticketFrom('feature/tidy-up'))
        assertEquals('none', VersionParser.ticketFrom(''))
        assertEquals('none', VersionParser.ticketFrom(null))
    }

    // ---- parse ----------------------------------------------------------------

    @Test
    void parsesASemVerString() {
        def v = VersionParser.parse('v1.2.3')

        assertTrue(v.valid)
        assertEquals(1, v.major)
        assertEquals(2, v.minor)
        assertEquals(3, v.patch)
        assertEquals('1.2.3', v.text)
    }

    @Test
    void parsesWithoutTheVPrefix() {
        assertTrue(VersionParser.parse('2.0.0').valid)
    }

    @Test
    void reportsInvalidVersionsRatherThanThrowing() {
        def v = VersionParser.parse('not-a-version')

        assertFalse(v.valid)
        assertEquals(0, v.major)
    }

    // ---- bumpKind -------------------------------------------------------------

    @Test
    void classifiesTheBump() {
        assertEquals('patch', VersionParser.bumpKind('v1.0.0', 'v1.0.1'))
        assertEquals('minor', VersionParser.bumpKind('v1.0.1', 'v1.1.0'))
        assertEquals('major', VersionParser.bumpKind('v1.1.0', 'v2.0.0'))
        assertEquals('none',  VersionParser.bumpKind('v1.1.0', 'v1.1.0'))
        assertEquals('unknown', VersionParser.bumpKind('v1.1.0', 'rubbish'))
    }

    // ---- the CPS point, as a test ---------------------------------------------

    @Test
    void returnsOnlySerializableValues() {
        // A Matcher is created inside these methods and discarded there. What comes
        // back is a String and a Map of simple types — things Jenkins can write to
        // disk when it saves a paused build. Holding a Matcher instead is what
        // produces NotSerializableException.
        assertTrue(VersionParser.ticketFrom('feature/PROJ-1-x') instanceof String)

        def parsed = VersionParser.parse('v1.2.3')
        assertTrue(parsed instanceof Map)
        assertTrue(parsed.major instanceof Integer)
        assertTrue(parsed.text instanceof String)
    }
}
