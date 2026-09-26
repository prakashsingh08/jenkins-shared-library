// Phase 7 — a step that IS the whole pipeline.
//
// No new mechanism: this is still vars/<name>.groovy with a call() method, exactly
// like helloP2. The only difference is what is inside call() — a pipeline { } block.
//
//   Jenkinsfile:  helloPipelineP7(name: 'catalog')
//        ↓
//   helloPipelineP7.call([name: 'catalog'])
//        ↓
//   the pipeline { } block below — this IS the build
//
// FOUR RULES for Declarative inside a library:
//   1. Compute everything ABOVE the pipeline block. Local variables are then usable
//      inside the stages — that is what makes it parameterisable at all.
//   2. Nothing AFTER the closing brace of pipeline { }. Use post { } instead.
//   3. A few directives insist on a literal, not a variable — notably
//      credentials(x) and when { branch x }. If one throws a parse error, that is
//      this rule, not a bug in your logic.
//   4. Steps only go inside steps { }. Anything else Groovy-ish needs script { }.

def call(Map config = [:]) {

    // ---- 1. read + default (Phase 4's beats) --------------------------------
    def appName   = config.name
    def greeting  = config.greeting ?: 'Hello'
    def skipTests = config.get('skipTests', false)

    // ---- 2. validate, before the pipeline even starts ----------------------
    if (!appName) {
        error "helloPipelineP7: 'name' is required, e.g. helloPipelineP7(name: 'catalog')"
    }

    // ---- 3. the pipeline ---------------------------------------------------
    pipeline {

        agent any

        stages {

            // Phase 5 — logic in a src/ class
            stage('Greet') {
                steps {
                    // ---- Phase 8 marker -------------------------------------
                    // This line exists on `main` but NOT in the v1.0.0 tag.
                    // Run the same job pinned to @main and to @v1.0.0: one prints
                    // this, the other does not. That difference IS the versioning
                    // lesson — a branch moves, a tag does not.
                    echo 'Running the main branch version of helloPipelineP7'
                    // ---------------------------------------------------------

                    greetP5(name: appName, greeting: greeting)
                }
            }

            // Phase 4 — Map config, defaults, validation
            stage('Build') {
                steps {
                    buildAppP4(name: appName, skipTests: skipTests)
                }
            }

            // Phase 6 — a file from resources/
            stage('Banner') {
                steps {
                    bannerP6(name: appName)
                }
            }

            // Phase 6 — a template filled in and written to the workspace
            stage('Build info') {
                steps {
                    buildInfoP6(name: appName)
                    sh 'cat build-info.txt'
                    archiveArtifacts artifacts: 'build-info.txt', fingerprint: true
                }
            }
        }

        // post runs after the stages, whatever happened. This is where a real library
        // earns its keep: write the Slack message, the cleanup, the status notification
        // ONCE here, and every consuming repo gets it.
        post {
            success {
                echo "SUCCESS — ${appName} built by the shared library"
            }
            failure {
                echo "FAILURE — ${appName} did not build. Read the FIRST error, not the last."
            }
            always {
                echo "Build #${currentBuild.number} finished with result: ${currentBuild.currentResult}"
            }
        }
    }
    // Rule 2: nothing goes here.
}
