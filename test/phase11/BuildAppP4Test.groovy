// PHASE 11, step 3 — testing a vars/ STEP, which needs a fake Jenkins.
//
// Tests: vars/buildAppP4.groovy  (Phase 4)
//
// JenkinsPipelineUnit provides echo / sh / error / env and records every call.
// Nothing runs for real: `sh 'mvn deploy'` does not run Maven, it records that sh was
// called with that string — which is usually exactly what you want to assert.
//
// The most valuable test here is the injection one. You ran that demo by hand once in
// Phase 3; now it runs on every change, forever.

import com.lesfurets.jenkins.unit.BasePipelineTest
import org.junit.Before
import org.junit.Test

import static org.junit.Assert.assertTrue
import static org.junit.Assert.assertFalse
import static org.junit.Assert.fail

class BuildAppP4Test extends BasePipelineTest {

    /** Every sh command the step issued, so we can assert on what was built. */
    List<String> shCommands = []

    /** Every echo the step produced. */
    List<String> echoed = []

    @Override
    @Before
    void setUp() throws Exception {
        // loadScript() resolves paths against these roots; '.' means the repo root.
        scriptRoots = ['.'] as String[]

        super.setUp()

        shCommands = []
        echoed = []

        // Teach the fake Jenkins about the steps this code calls.
        helper.registerAllowedMethod('sh', [String], { String cmd -> shCommands << cmd })
        helper.registerAllowedMethod('echo', [String], { String msg -> echoed << msg })

        // `error` must THROW, the way the real step does — otherwise a test asserting
        // that validation stops the build would pass for the wrong reason.
        helper.registerAllowedMethod('error', [String], { String msg ->
            throw new RuntimeException(msg)
        })

        binding.setVariable('env', [BUILD_NUMBER: '1', JOB_NAME: 'test-job'])
    }

    private Object loadStep() {
        return loadScript('vars/buildAppP4.groovy')
    }

    // ---- validation -----------------------------------------------------------

    @Test
    void failsWhenNameIsMissing() {
        try {
            loadStep().call([:])
            fail('expected buildAppP4 to reject a missing name')
        } catch (Exception e) {
            assertTrue(e.message.contains('buildAppP4'))   // the step names itself
            assertTrue(e.message.contains('name'))
        }
        assertTrue('no shell command should run before validation', shCommands.isEmpty())
    }

    @Test
    void failsWhenNameIsEmpty() {
        try {
            loadStep().call(name: '')
            fail('expected buildAppP4 to reject an empty name')
        } catch (Exception e) {
            assertTrue(e.message.contains('required'))
        }
    }

    @Test
    void failsWhenNameHasShellPunctuation() {
        // Phase 3's injection demo, now a permanent guard.
        try {
            loadStep().call(name: 'catalog; echo INJECTED')
            fail('expected buildAppP4 to reject shell punctuation')
        } catch (Exception e) {
            assertTrue(e.message.contains('letters, digits'))
        }
        assertTrue('nothing should reach the shell', shCommands.isEmpty())
    }

    @Test
    void acceptsAPlainName() {
        loadStep().call(name: 'catalog')

        assertJobStatusSuccess()
        assertTrue(shCommands.any { it.contains('catalog') })
    }

    // ---- defaults -------------------------------------------------------------

    @Test
    void appliesTheDefaultImage() {
        loadStep().call(name: 'catalog')

        assertTrue(echoed.any { it.contains('maven:3.9-eclipse-temurin-17') })
    }

    @Test
    void anExplicitFalseForSkipTestsSurvives() {
        // THE IMPORTANT ONE. `config.skipTests ?: true` would silently turn this into
        // true, because false is not truthy in Groovy. This test makes that bug
        // impossible to reintroduce unnoticed.
        loadStep().call(name: 'catalog', skipTests: false)

        assertTrue(echoed.any { it.contains('skipTests=false') })
        assertTrue('tests should run', shCommands.any { it.contains('mvn test') })
    }

    @Test
    void skipTestsTrueSkipsTheTestCommand() {
        loadStep().call(name: 'catalog', skipTests: true)

        assertFalse('tests should not run', shCommands.any { it.contains('mvn test') })
        assertTrue(echoed.any { it.toLowerCase().contains('skipping') })
    }
}
