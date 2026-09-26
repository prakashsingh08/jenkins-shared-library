// PHASE 11, step 1 — a test with NO framework at all.
//
// Run it with nothing installed but Docker:
//
//     docker run --rm -v "$PWD":/app -w /app groovy:jdk17 \
//       groovy -cp src test/phase11/GreeterManualTest.groovy
//
// The point of this file: com.learning.phase05.Greeter (Phase 5) is ORDINARY GROOVY.
// It only ever calls script.echo, so a "pipeline" for test purposes is any object
// with an echo method. No Jenkins, no plugins, no waiting.
//
// This is the payoff for Phase 5's `script` handle: the class RECEIVES its dependency
// instead of reaching out for it, so a test can pass in something that is not Jenkins.

import com.learning.phase05.Greeter

// ---- the fake pipeline --------------------------------------------------------
class FakeScript {
    List<String> echoed = []

    void echo(String message) {
        echoed << message
    }
}

// ---- test 1: one greeting, with the default text ------------------------------
def fake = new FakeScript()
new Greeter(fake).greet('Prakash')

assert fake.echoed.size() == 1
assert fake.echoed[0].contains('Hello')
assert fake.echoed[0].contains('Prakash')

// ---- test 2: a custom greeting reaches the output -----------------------------
fake = new FakeScript()
new Greeter(fake, 'Namaste').greet('Asha')

assert fake.echoed[0].contains('Namaste')
assert !fake.echoed[0].contains('Hello')

// ---- test 3: greetAll greets everyone, in order -------------------------------
fake = new FakeScript()
new Greeter(fake, 'Hi').greetAll(['Prakash', 'Asha', 'Ravi'])

assert fake.echoed.size() == 3
assert fake.echoed[0].contains('Prakash')
assert fake.echoed[2].contains('Ravi')

println 'All manual tests passed.'

// EXERCISE: make one of these fail on purpose (assert the wrong greeting) so you have
// seen both outcomes. A test you have never watched fail is not evidence of anything —
// it might be asserting nothing at all.
