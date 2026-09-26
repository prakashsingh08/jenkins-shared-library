// PHASE 14 — buildJavaP9, made faster (or at least, measurably different).
//
// What is new compared with buildJavaP9:
//   * a Checks stage whose branches run in parallel
//   * failFast, configurable, because the right answer depends on the caller
//   * stash / unstash, to move the jar to a stage on a different agent
//   * timeout and disableConcurrentBuilds, so a hung build stops costing an agent
//
// Read Part 5 of the spec before using this in anger: measure first. Parallel is not
// a synonym for faster, and on a single small agent it can easily be slower.

def call(Map config = [:]) {

    // ---- read + default -----------------------------------------------------
    def appName   = config.name
    def image     = config.image     ?: 'maven:3.9-eclipse-temurin-17'
    def m2Volume  = config.m2Volume  ?: 'jenkins-maven-repo'
    def skipTests = config.get('skipTests', false)
    def failFast  = config.get('failFast', false)
    def timeoutM  = config.timeoutMinutes ?: 30
    def jarGlob   = config.artifacts   ?: 'target/*.jar'
    def testGlob  = config.testResults ?: 'target/surefire-reports/*.xml'

    def dockerArgs = "-v ${m2Volume}:/root/.m2"

    // ---- validate -----------------------------------------------------------
    if (!appName) {
        error "buildJavaP14: 'name' is required, e.g. buildJavaP14(name: 'java-maven-proj01')"
    }
    if (!(appName ==~ /^[A-Za-z0-9._-]+$/)) {
        error "buildJavaP14: 'name' may only contain letters, digits, dot, dash and underscore. Got: ${appName}"
    }

    pipeline {

        agent {
            docker {
                image image
                args dockerArgs          // the .m2 cache — the single biggest lever
            }
        }

        environment {
            APP_VERSION = "1.0.${BUILD_NUMBER}"
        }

        options {
            timestamps()                        // so you can see WHERE the time went
            timeout(time: timeoutM, unit: 'MINUTES')   // a hung build stops holding an agent
            disableConcurrentBuilds()           // two builds fighting over one workspace
        }

        stages {

            stage('Build') {
                steps {
                    echo "Building ${appName} version ${APP_VERSION}"
                    sh 'mvn -B compile -Drevision=${APP_VERSION}'
                }
            }

            // Branches that do not depend on each other. On a busy multi-agent Jenkins
            // this is the real win; on one small local agent, measure before believing.
            stage('Checks') {
                when {
                    expression { !skipTests }
                }
                // failFast stops the other branches as soon as one fails.
                // Good when the branches are gates; bad when you want the full picture
                // of everything that is broken. Hence a config key rather than a rule.
                failFast failFast
                parallel {

                    stage('Unit tests') {
                        steps {
                            sh 'mvn -B test -Drevision=${APP_VERSION}'
                        }
                        post {
                            always {
                                junit allowEmptyResults: true, testResults: testGlob
                            }
                        }
                    }

                    stage('Lint') {
                        steps {
                            sh 'echo pretend: mvn -B checkstyle:check'
                        }
                    }

                    stage('Security scan') {
                        steps {
                            sh 'echo pretend: dependency vulnerability scan'
                        }
                    }
                }
            }

            stage('Package') {
                steps {
                    sh 'mvn -B package -DskipTests -Drevision=${APP_VERSION}'

                    // Each agent has its OWN workspace. The jar built here does not
                    // exist on the agent used by the next stage, so hand it over.
                    // Stashes go through the controller and live for this build only:
                    // fine for a jar or a report, wrong for a container image.
                    stash name: 'jar', includes: jarGlob
                }
            }

            // A different agent on purpose, to make the workspace boundary visible.
            stage('Inspect artifact elsewhere') {
                agent any
                steps {
                    // Remove this unstash and the ls fails: clearest possible
                    // demonstration of what a workspace is.
                    unstash 'jar'
                    sh "ls -la ${jarGlob.tokenize('/')[0]}/"
                }
            }

            stage('Archive') {
                steps {
                    archiveArtifacts artifacts: jarGlob, fingerprint: true
                }
            }
        }

        post {
            success {
                echo "SUCCESS — ${appName} ${APP_VERSION} in ${currentBuild.durationString}"
            }
            unstable {
                echo "UNSTABLE — ${appName} built, but tests failed."
            }
            failure {
                echo "FAILURE — ${appName} did not build. Read the FIRST error."
            }
            always {
                // Write the number down. "It feels faster" is not a result.
                echo "Build #${currentBuild.number} took ${currentBuild.durationString}"
            }
        }
    }
}
