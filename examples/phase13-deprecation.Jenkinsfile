// PHASE 13 — changing a config key without breaking anyone
//
// Jenkins job:  shared-lib-phase13
//   New Item → Pipeline → Pipeline section → Definition: "Pipeline script" → paste this.
//
// The scenario: `buildAppP4` took a key called `name`. It is being renamed to
// `appName`. Twenty repos call it the old way. This is what step 1 of the three-step
// deprecation looks like from a CONSUMER's side — which is to say, it looks like
// nothing at all, except a warning in the log.

@Library('shared-lib@main') _

pipeline {
    agent any

    stages {

        // An existing consumer who has not migrated yet. Their pipeline still works.
        // They get a warning that names the old key, the new key, and the deadline.
        stage('Old key (deprecated, still works)') {
            steps {
                buildAppP13(name: 'catalog')
            }
        }

        // A consumer who has migrated. Same behaviour, no warning.
        stage('New key (preferred)') {
            steps {
                buildAppP13(appName: 'catalog')
            }
        }

        // Both keys, same value: allowed, still warns about the old one.
        stage('Both keys, agreeing') {
            steps {
                buildAppP13(name: 'catalog', appName: 'catalog')
            }
        }

        // Both keys DISAGREEING is an error, not a guess. Uncomment to see it fail:
        //
        // stage('Both keys, disagreeing') {
        //     steps {
        //         buildAppP13(name: 'catalog', appName: 'payments')
        //     }
        // }
    }
}

// ---------------------------------------------------------------------------
// WHAT TO NOTICE
//
// 1. The warning appears in the log of the PERSON WHO MUST ACT. That is why this
//    beats an email to everyone — the message finds its audience by itself.
//
// 2. Nothing broke. Step 1 ships as a MINOR release precisely because no existing
//    call stops working.
//
// 3. The code is uglier for a whole version: two keys, a warning, a conflict check.
//    That ugliness is the deprecation. It is what buys other teams time to move.
//
// 4. Removing `name` later is a MAJOR release — even though the diff is one line.
//    The size of the change is not what makes it major; breaking a caller is.
// ---------------------------------------------------------------------------

// ---------------------------------------------------------------------------
// THE REST OF PHASE 13 IS NOT CODE
//
//   GOVERNANCE.md   who pins what, who may merge and tag, what builds the library
//   CHANGELOG.md    what changed, written for consumers rather than for you
//   README.md       the step catalogue — "is there a step for X?" answered in seconds
//
// And one exercise that is pure Jenkins/GitHub, no Groovy:
//
//   * Turn on branch protection for main on GitHub: require a pull request, disallow
//     direct pushes. Then notice that YOU can no longer push to main — inconvenient
//     exactly as intended. Keeping it or turning it off are both defensible for a
//     solo learning repo; being able to explain the trade is the exercise.
//
//   * Register a library at FOLDER level instead of globally, and run a job inside
//     that folder. Note that it is untrusted by default, and what that changes.
// ---------------------------------------------------------------------------
