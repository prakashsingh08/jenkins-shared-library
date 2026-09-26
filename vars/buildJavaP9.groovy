// Phase 9 — a real Maven build, not a pretend one.
//
// Three things appear here that the pretend builds never needed:
//   1. AN AGENT WITH MAVEN ON IT. `sh 'mvn ...'` only works where mvn exists, and
//      the Jenkins container has no Maven — so the stages run inside a maven image.
//   2. SOURCE CODE. The library has no idea what repo it is building. The job must be
//      "Pipeline script from SCM" (or Multibranch), which checks the repo out before
//      the first stage. An inline "Pipeline script" job has an EMPTY workspace and
//      Maven will report that there is no POM in this directory.
//   3. RESULTS. Test reports and jars have to be collected, or the build tells you
//      nothing: junit for the reports, archiveArtifacts for the jar.

def call(Map config = [:]) {

    // ---- read + default -----------------------------------------------------
    def appName   = config.name
    def image     = config.image     ?: 'maven:3.9-eclipse-temurin-17'
    def m2Volume  = config.m2Volume  ?: 'jenkins-maven-repo'
    def skipTests = config.get('skipTests', false)
    def jarGlob   = config.artifacts ?: 'target/*.jar'
    def testGlob  = config.testResults ?: 'target/surefire-reports/*.xml'

    // Declarative reads the agent section before your code runs, so build the whole
    // args string here as a plain local variable rather than interpolating inside the
    // agent block. (Phase 7, Rule 1: compute above, use below.)
    //
    // The volume is what stops every build re-downloading Maven's entire dependency
    // tree: ~/.m2 inside a fresh container is empty, and a named volume persists it.
    def dockerArgs = "-v ${m2Volume}:/root/.m2"

    // ---- validate -----------------------------------------------------------
    if (!appName) {
        error "buildJavaP9: 'name' is required, e.g. buildJavaP9(name: 'java-maven-proj01')"
    }
    if (!(appName ==~ /^[A-Za-z0-9._-]+$/)) {
        error "buildJavaP9: 'name' may only contain letters, digits, dot, dash and underscore. Got: ${appName}"
    }

    // ---- the pipeline -------------------------------------------------------
    pipeline {

        agent {
            docker {
                image image
                args dockerArgs
            }
        }

        environment {
            // Every build gets a unique version. java-maven-proj01's pom reads it via
            // -Drevision. Proper release versioning is a topic of its own.
            APP_VERSION = "1.0.${BUILD_NUMBER}"
        }

        options {
            timestamps()
        }

        stages {

            stage('Build') {
                steps {
                    echo "Building ${appName} version ${APP_VERSION}"
                    // -B is batch mode: without it Maven writes thousands of download
                    // progress lines into your console log.
                    sh 'mvn -B compile -Drevision=${APP_VERSION}'
                }
            }

            stage('Test') {
                // A variable is fine inside expression { }, unlike `when { branch x }`
                // which wants a literal (Phase 7, Rule 3).
                when {
                    expression { !skipTests }
                }
                steps {
                    sh 'mvn -B test -Drevision=${APP_VERSION}'
                }
            }

            stage('Package') {
                steps {
                    sh 'mvn -B package -DskipTests -Drevision=${APP_VERSION}'
                }
            }

            stage('Archive') {
                steps {
                    archiveArtifacts artifacts: jarGlob, fingerprint: true
                }
            }
        }

        post {
            always {
                // In post { always }, not in the Test stage: if tests FAIL the stage
                // fails, and that is exactly when you most want the report.
                // allowEmptyResults stops a skipTests build failing for having none.
                junit allowEmptyResults: true, testResults: testGlob
            }
            success {
                echo "SUCCESS — ${appName} ${APP_VERSION}"
            }
            unstable {
                // Failing tests make a build UNSTABLE (yellow), not FAILED (red).
                echo "UNSTABLE — ${appName} built, but tests failed. Check the Test Result page."
            }
            failure {
                echo "FAILURE — ${appName} did not build. Read the FIRST error."
            }
        }
    }
}
