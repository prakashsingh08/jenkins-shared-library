// PHASE 12 — CPS demonstrations you can actually run.
//
//   cpsDemoP12(demo: 'broken-matcher')   deliberately fails: NotSerializableException
//   cpsDemoP12(demo: 'fixed-matcher')    the same logic, done safely
//   cpsDemoP12(demo: 'noncps')           the @NonCPS way
//   cpsDemoP12(demo: 'each')             iteration with .each   (unpredictable)
//   cpsDemoP12(demo: 'for')              iteration with for     (always safe)
//
// Background: Jenkins rewrites pipeline Groovy into a state machine so a build can be
// paused, written to disk and resumed after a restart. Everything below is a
// consequence of that one design decision.

// @NonCPS lives in a Jenkins-only package, so this import works inside Jenkins and
// nowhere else. That is why the src/ class in this phase avoids it — it would stop
// `mvn test` compiling.
import com.cloudbees.groovy.cps.NonCPS

def call(Map config = [:]) {

    def demo = config.demo ?: 'fixed-matcher'
    def branch = config.branch ?: 'feature/PROJ-123-add-login'
    def names = config.names ?: ['alpha', 'beta', 'gamma']

    switch (demo) {

        // -------------------------------------------------------------------
        // 1. THE CLASSIC FAILURE
        //
        // `matcher` is a java.util.regex.Matcher and it is still alive when the
        // echo step runs. echo is a step, so Jenkins may save the build's state
        // there — and a Matcher cannot be written to disk.
        //
        // Expect: java.io.NotSerializableException: java.util.regex.Matcher
        //
        // Nothing about the regex or the logic is wrong. The only problem is WHEN
        // the object was alive.
        // -------------------------------------------------------------------
        case 'broken-matcher':
            echo 'About to fail on purpose. Read the exception class name carefully.'
            def matcher = (branch =~ /([A-Z][A-Z0-9]*-\d+)/)
            echo 'This echo is a step — state may be saved right here.'
            echo "Ticket: ${matcher ? matcher[0][1] : 'none'}"
            break

        // -------------------------------------------------------------------
        // 2. FIX A — finish with the exotic object immediately.
        // Only a String survives to the next step, and Strings serialize fine.
        // -------------------------------------------------------------------
        case 'fixed-matcher':
            def ticket = extractTicketInline(branch)
            echo "Ticket: ${ticket}   (only a String crossed a step boundary)"
            break

        // -------------------------------------------------------------------
        // 3. FIX B — @NonCPS. The method runs as ordinary Groovy, in one go, with
        // no save points inside it at all. Note what is NOT in there: no echo,
        // no sh. Computation inside, steps outside.
        // -------------------------------------------------------------------
        case 'noncps':
            def shouted = shoutNames(names)      // @NonCPS: closures are safe in here
            echo "Names: ${shouted}"
            break

        // -------------------------------------------------------------------
        // 4. .each with a STEP in the body.
        //
        // `each` is a Groovy/JDK method and is NOT CPS-transformed; the closure IS.
        // So a non-CPS method is being asked to call CPS code. It sometimes works,
        // sometimes fails strangely, and sometimes runs zero times.
        //
        // Try it. Whatever happens on your Jenkins, the lesson is that it is
        // UNPREDICTABLE — which is why the rule is simply "don't".
        // -------------------------------------------------------------------
        case 'each':
            echo 'Iterating with .each (do not do this when the body calls a step):'
            names.each { name ->
                echo "  hello ${name}"
            }
            break

        // -------------------------------------------------------------------
        // 5. A plain for loop is CPS-transformed all the way through. Always safe.
        // This is why Greeter.greetAll (Phase 5) was written this way.
        // -------------------------------------------------------------------
        case 'for':
            echo 'Iterating with for (safe):'
            for (String name : names) {
                echo "  hello ${name}"
            }
            break

        default:
            error "cpsDemoP12: unknown demo '${demo}'. " +
                  "Use one of: broken-matcher, fixed-matcher, noncps, each, for"
    }
}

// A plain helper. The Matcher is created, read and thrown away without any step in
// between, so it never has to survive a save.
def extractTicketInline(String branchName) {
    def matcher = (branchName =~ /([A-Z][A-Z0-9]*-\d+)/)
    return matcher ? matcher[0][1] : 'none'
}

// @NonCPS: run this as ordinary Groovy, start to finish.
//
// Rules, and they are strict:
//   1. no pipeline steps inside — sh, echo, checkout, withCredentials. This is the
//      rule people break first, and the symptoms are bizarre rather than obvious.
//   2. it must finish quickly; there is no pause point inside.
//   3. return something serializable — here, a String.
//   4. keep it small: a calculation, not a workflow.
//
// In exchange, closures work normally, which is why .collect is fine here and risky
// in the 'each' case above.
@NonCPS
String shoutNames(List<String> names) {
    return names.collect { it.toUpperCase() }.join(', ')
}
