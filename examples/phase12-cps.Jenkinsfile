// PHASE 12 — CPS, @NonCPS, and why pipeline Groovy is strange
//
// Jenkins job:  shared-lib-phase12
//   New Item → Pipeline → Pipeline section → Definition: "Pipeline script" → paste this.
//
// Every stage here is an experiment. Run them, read the output, and note what
// surprised you — the notes go in specs/troubleshooting.md.

@Library('shared-lib@main') _

pipeline {
    agent any

    stages {

        // The shape to copy: a class does the computation, the step does the echoing.
        stage('Version info (the good pattern)') {
            steps {
                versionInfoP12(
                    branch: 'feature/PROJ-123-add-login',
                    from: 'v1.2.0',
                    to: 'v2.0.0'
                )
            }
        }

        // Fix A — extract the String immediately, so nothing exotic crosses a step.
        stage('Matcher done safely') {
            steps {
                cpsDemoP12(demo: 'fixed-matcher')
            }
        }

        // Fix B — @NonCPS: ordinary Groovy, closures allowed, no steps inside.
        stage('@NonCPS') {
            steps {
                cpsDemoP12(demo: 'noncps')
            }
        }

        // Always safe: a for loop is CPS-transformed all the way through.
        stage('Iteration with for') {
            steps {
                cpsDemoP12(demo: 'for')
            }
        }

        // Unpredictable: .each is not CPS-transformed, the closure is.
        // It may work on your Jenkins. That is the point — it is not reliable.
        stage('Iteration with .each') {
            steps {
                cpsDemoP12(demo: 'each')
            }
        }
    }
}

// ---------------------------------------------------------------------------
// THE DELIBERATE FAILURE — run this one on its own
//
// Add this stage (or swap it into the pipeline above) and watch it fail:
//
//     stage('Broken on purpose') {
//         steps {
//             cpsDemoP12(demo: 'broken-matcher')
//         }
//     }
//
// Expect:  java.io.NotSerializableException: java.util.regex.Matcher
//
// Read the class name in the exception. It always names the object that could not be
// written to disk — that name is the entire diagnosis. The fix is either to stop
// holding it across a step, or to move the work into a @NonCPS method.
// ---------------------------------------------------------------------------

// ---------------------------------------------------------------------------
// WATCH A BUILD SURVIVE A RESTART  (the reason all of this exists)
//
//   1. Run a job with a stage containing:   sh 'sleep 120'
//   2. While it runs:   cd ../java-maven-proj01 && docker compose restart jenkins
//   3. Wait for Jenkins to come back, then open the build.
//
// It carries on from where it was. The rewriting, the serialization and every
// restriction in this phase exist to buy that one behaviour. Decide for yourself
// whether the trade was worth it — both answers are defensible, and it makes a good
// interview conversation.
// ---------------------------------------------------------------------------

// ---------------------------------------------------------------------------
// BREAK THE @NonCPS RULE ON PURPOSE
//
// In vars/cpsDemoP12.groovy, add an  echo  inside the @NonCPS method shoutNames().
// Run the 'noncps' demo again (Replay is quickest).
//
// Whatever happens — it works oddly, prints nothing, or fails strangely — write down
// exactly what you saw. This failure mode is very hard to recognise later if you have
// never met it once.
// ---------------------------------------------------------------------------
