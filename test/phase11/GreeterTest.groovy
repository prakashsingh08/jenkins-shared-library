// PHASE 11, step 2 — the same idea as GreeterManualTest, now as a JUnit test so it
// runs with `mvn test` alongside everything else.
//
// Tests: src/com/learning/phase05/Greeter.groovy  (Phase 5)
//
// Still no JenkinsPipelineUnit here. A src/ class needs no fake Jenkins — only an
// object with the methods it actually calls. That is the whole argument for putting
// logic in src/ rather than vars/.

import com.learning.phase05.Greeter
import org.junit.Test

import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertTrue
import static org.junit.Assert.assertFalse

class GreeterTest {

    /** The smallest possible stand-in for a pipeline: something with an echo method. */
    static class FakeScript {
        List<String> echoed = []
        void echo(String message) { echoed << message }
    }

    @Test
    void greetsWithTheDefaultGreeting() {
        def fake = new FakeScript()

        new Greeter(fake).greet('Prakash')

        assertEquals(1, fake.echoed.size())
        assertTrue(fake.echoed[0].contains('Hello'))
        assertTrue(fake.echoed[0].contains('Prakash'))
    }

    @Test
    void usesACustomGreeting() {
        def fake = new FakeScript()

        new Greeter(fake, 'Namaste').greet('Asha')

        assertTrue(fake.echoed[0].contains('Namaste'))
        assertFalse(fake.echoed[0].contains('Hello'))
    }

    @Test
    void greetsEveryoneInOrder() {
        def fake = new FakeScript()

        new Greeter(fake, 'Hi').greetAll(['Prakash', 'Asha', 'Ravi'])

        assertEquals(3, fake.echoed.size())
        assertTrue(fake.echoed[0].contains('Prakash'))
        assertTrue(fake.echoed[1].contains('Asha'))
        assertTrue(fake.echoed[2].contains('Ravi'))
    }

    @Test
    void greetsNobodyForAnEmptyList() {
        def fake = new FakeScript()

        new Greeter(fake).greetAll([])

        assertEquals(0, fake.echoed.size())
    }
}
