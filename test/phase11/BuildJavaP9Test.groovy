// PHASE 11, step 4 — asserting on the COMMAND a step builds.
//
// Tests: vars/buildJavaP9.groovy  (Phase 9) — the validation part only.
//
// WHAT THIS TEST CAN AND CANNOT DO, honestly:
//
//   ✓ the validation above the pipeline { } block runs, so bad input is caught here
//   ✗ the pipeline { } block itself is barely testable by JenkinsPipelineUnit
//
// That gap is why Phase 8's canary job still matters: unit tests will never catch a
// Declarative parse error, a wrong agent, or a missing image. Three layers:
//
//   unit tests   → logic and validation      (seconds)
//   canary job   → Declarative and agents    (minutes)
//   pinned tags  → blast radius when both miss

import com.lesfurets.jenkins.unit.BasePipelineTest
import org.junit.Before
import org.junit.Test

import static org.junit.Assert.assertTrue
import static org.junit.Assert.fail

class BuildJavaP9Test extends BasePipelineTest {

    @Override
    @Before
    void setUp() throws Exception {
        scriptRoots = ['.'] as String[]
        super.setUp()

        helper.registerAllowedMethod('error', [String], { String msg ->
            throw new RuntimeException(msg)
        })

        binding.setVariable('env', [BUILD_NUMBER: '7', JOB_NAME: 'test-job'])
    }

    @Test
    void failsWhenNameIsMissing() {
        try {
            loadScript('vars/buildJavaP9.groovy').call([:])
            fail('expected buildJavaP9 to reject a missing name')
        } catch (Exception e) {
            assertTrue(e.message.contains('buildJavaP9'))
            assertTrue(e.message.contains('name'))
        }
    }

    @Test
    void failsWhenNameHasShellPunctuation() {
        try {
            loadScript('vars/buildJavaP9.groovy').call(name: 'proj; rm -rf /')
            fail('expected buildJavaP9 to reject shell punctuation')
        } catch (Exception e) {
            assertTrue(e.message.contains('letters, digits'))
        }
    }

    // EXERCISE (Phase 11, step 4 in the spec):
    // Registering `pipeline`, `agent`, `stages` and friends as allowed methods lets you
    // get further into a Declarative step and assert that `sh` was called with -B and
    // -Drevision=. Try it, and notice how much scaffolding it takes — that cost is
    // exactly why libraries keep the real logic in plain methods and src/ classes,
    // with pipeline { } as a thin shell.
}
