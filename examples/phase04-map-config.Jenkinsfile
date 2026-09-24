// PHASE 4 — configuration as a Map
//
// Jenkins job:  shared-lib-phase04
//   New Item → Pipeline → Pipeline section → Definition: "Pipeline script" → paste this.
//
// Every stage below is a test of one behaviour. Predict the log line each will
// print BEFORE you build — the gap between your guess and the output is the lesson.

@Library('shared-lib') _

pipeline {
    agent any

    stages {
        // Only `name` is given, so `image` and `skipTests` fall back to the step's
        // own defaults.
        // Expect:  image=maven:3.9-eclipse-temurin-17, skipTests=false
        stage('Defaults') {
            steps {
                buildAppP4(name: 'catalog')
            }
        }

        // Expect:  skipTests=true, and the test command replaced by a "skipping" message.
        stage('skipTests true') {
            steps {
                buildAppP4(name: 'catalog', skipTests: true)
            }
        }

        // THE IMPORTANT ONE. An explicit `false` must survive.
        // If the log says skipTests=true here, the step used Elvis (?:) on a boolean
        // instead of get(key, default) — see Phase 4, Part 3.
        stage('skipTests false is respected') {
            steps {
                buildAppP4(name: 'catalog', skipTests: false)
            }
        }

        // Overriding a String default.
        // Expect:  image=maven:3.8
        stage('Custom image') {
            steps {
                buildAppP4(name: 'payments', image: 'maven:3.8')
            }
        }

        // Unknown keys are silently ignored today: this runs the tests anyway.
        // Phase 4's exercise 4 asks whether your step SHOULD reject them.
        stage('Unknown key is ignored') {
            steps {
                buildAppP4(name: 'catalog', skpTests: true)
            }
        }

        stage('Cleanup') {
            steps {
                script {
                    buildAppP4.cleanup()
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// VALIDATION TESTS — each of these should FAIL. Uncomment one at a time, in a
// stage of its own, and read the message. A build that fails with your own clear
// error is a success for this exercise.
//
//   buildAppP4()                                 → "'name' is required"
//   buildAppP4(nmae: 'catalog')                  → same: the typo is not `name`
//   buildAppP4(name: '')                         → same: empty string is not truthy
//   buildAppP4(name: 'catalog; echo INJECTED')   → rejected by the allow-list,
//                                                  and INJECTED never runs
//
// That last one is the hole buildAppP3 leaves open on purpose. Run it against
// buildAppP3 first to watch the injection succeed, then against buildAppP4 to
// watch it get stopped.
// ---------------------------------------------------------------------------
