// PHASE 14 — parallel branches, stash, and build speed
//
// TWO JOBS for this phase.
//
// -- JOB 1: shared-lib-phase14-demo ------------------------------------------
//    New Item → Pipeline → "Pipeline script" → paste the pipeline below.
//    Needs no Maven and no source: it is the mechanics on their own.
//
// -- JOB 2: shared-lib-phase14 -----------------------------------------------
//    The real build, which needs source checked out (Phase 9's rule):
//    Pipeline → "Pipeline script from SCM" → java-maven-proj01 → your branch,
//    with a Jenkinsfile containing just:
//
//        @Library('shared-lib@main') _
//        buildJavaP14(name: 'java-maven-proj01')

@Library('shared-lib@main') _

pipeline {
    agent any

    stages {

        // THE BUG, on purpose. Every branch should print a different module.
        // Watch them all print 'gamma' instead — and note that nothing fails.
        stage('Dynamic branches — the wrong way') {
            steps {
                parallelDemoP14(mode: 'broken')
            }
        }

        // The same loop with one extra line: a fresh variable inside the loop.
        stage('Dynamic branches — fixed') {
            steps {
                parallelDemoP14(mode: 'fixed')
            }
        }

        // Splitting many modules into a few branches, rather than one branch each.
        stage('Chunked branches') {
            steps {
                parallelDemoP14(
                    mode: 'chunked',
                    modules: ['auth', 'catalog', 'payments', 'search', 'shipping', 'users'],
                    chunks: 3
                )
            }
        }

        // The Declarative form, for comparison: fixed at parse time, which is why a
        // library that builds branches from caller input needs the `parallel` STEP
        // instead of this block.
        stage('Declarative parallel') {
            parallel {
                stage('Fast check')   { steps { sh 'echo pretend: lint' } }
                stage('Slower check') { steps { sh 'sleep 5; echo pretend: scan' } }
            }
        }
    }

    post {
        always {
            echo "Build #${currentBuild.number} took ${currentBuild.durationString}"
        }
    }
}

// ---------------------------------------------------------------------------
// MEASURE SOMETHING REAL  (job 2, against java-maven-proj01)
//
// The .m2 cache is worth more than parallelism on a single agent. Prove it:
//
//   1. Run buildJavaP14 twice. Note the second build's duration.
//   2. In vars/buildJavaP14.groovy, delete the  args dockerArgs  line. Push.
//   3. Run twice more. Note the duration.
//   4. Put it back.
//
// You now have a number for what one line of configuration is worth. That is the
// kind of fact that settles an argument in a planning meeting.
// ---------------------------------------------------------------------------

// ---------------------------------------------------------------------------
// SEE THE WORKSPACE BOUNDARY
//
// buildJavaP14 stashes the jar in Package and unstashes it in a stage running on a
// DIFFERENT agent. Delete the  unstash 'jar'  line and run it again:
//
//   ls: target/: No such file or directory
//
// Different agent, different workspace. That error is the clearest definition of a
// workspace you will ever get.
// ---------------------------------------------------------------------------

// ---------------------------------------------------------------------------
// failFast
//
// Make one parallel branch fail (sh 'exit 1'), then run buildJavaP14 with
// failFast: true and with failFast: false. Compare what the stage view tells you.
//
// Which would you want as the DEFAULT for a library other teams use? Write down your
// answer — it is the same "the common case is not everyone" question as Phase 7.
// ---------------------------------------------------------------------------
