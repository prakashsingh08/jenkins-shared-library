// Phase 10 — buildJavaP9 plus a Publish stage that needs a password.
//
// What is new compared with buildJavaP9:
//   * a Publish stage, guarded so it only runs on the publish branch
//   * credentials borrowed for the length of one block (withCloudsmithP10)
//   * a settings.xml generated from resources/, used, then DELETED in a finally
//   * error handling that says which step failed, and rethrows

def call(Map config = [:]) {

    // ---- read + default -----------------------------------------------------
    def appName       = config.name
    def image         = config.image         ?: 'maven:3.9-eclipse-temurin-17'
    def m2Volume      = config.m2Volume      ?: 'jenkins-maven-repo'
    def credentialsId = config.credentialsId ?: 'cloudsmith-creds'
    def serverId      = config.serverId      ?: 'cloudsmith'
    def publishBranch = config.publishBranch ?: 'main'
    def skipTests     = config.get('skipTests', false)
    def jarGlob       = config.artifacts     ?: 'target/*.jar'
    def testGlob      = config.testResults   ?: 'target/surefire-reports/*.xml'

    def dockerArgs = "-v ${m2Volume}:/root/.m2"

    // ---- validate -----------------------------------------------------------
    if (!appName) {
        error "buildJavaP10: 'name' is required, e.g. buildJavaP10(name: 'java-maven-proj01')"
    }
    if (!(appName ==~ /^[A-Za-z0-9._-]+$/)) {
        error "buildJavaP10: 'name' may only contain letters, digits, dot, dash and underscore. Got: ${appName}"
    }
    // The config Map must never contain the secret itself — only the ID of a
    // credential held in Jenkins. If a password ever arrives here, something is
    // wrong upstream of this step.

    pipeline {

        agent {
            docker {
                image image
                args dockerArgs
            }
        }

        environment {
            APP_VERSION = "1.0.${BUILD_NUMBER}"
        }

        options {
            timestamps()
        }

        stages {

            stage('Build') {
                steps {
                    echo "Building ${appName} version ${APP_VERSION}"
                    sh 'mvn -B compile -Drevision=${APP_VERSION}'
                }
            }

            stage('Test') {
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

            stage('Publish') {
                // `when { branch publishBranch }` would hit Phase 7's Rule 3: that slot
                // wants a literal. expression { } accepts a variable, so use it instead.
                //
                // env.BRANCH_NAME only exists for Multibranch / SCM-backed jobs. In a
                // plain job it is null, so this stage is skipped — which is the safe
                // way round for a stage that publishes.
                when {
                    expression { env.BRANCH_NAME == publishBranch }
                }
                steps {
                    withCloudsmithP10(credentialsId) {
                        script {
                            try {
                                // The template is written with the credentials as
                                // ${env.CS_USER} / ${env.CS_PASS}, which MAVEN resolves
                                // from its own environment. The password is therefore
                                // never written to disk and never reaches the log.
                                def settings = libraryResource('com/learning/phase10/settings.xml')
                                writeFile file: 'settings-cloudsmith.xml',
                                          text: settings.replace('@SERVER_ID@', serverId)

                                // Single quotes: nothing here is interpolated by Groovy.
                                sh 'mvn -B deploy -DskipTests -Drevision=${APP_VERSION} -s settings-cloudsmith.xml'

                            } catch (e) {
                                // Add context, then RETHROW. A catch that swallows the
                                // exception turns a failed publish into a green build —
                                // worse than the failure, because nobody finds out.
                                error "buildJavaP10: publish failed for ${appName} ${APP_VERSION}. " +
                                      "Check the credential '${credentialsId}' and the server id '${serverId}'. " +
                                      "Cause: ${e.message}"

                            } finally {
                                // Runs whether or not the deploy worked. A credentials
                                // file must be removed even on failure — especially then.
                                sh 'rm -f settings-cloudsmith.xml'
                            }
                        }
                    }
                }
            }
        }

        post {
            always {
                junit allowEmptyResults: true, testResults: testGlob
            }
            success {
                echo "SUCCESS — ${appName} ${APP_VERSION}"
            }
            unstable {
                echo "UNSTABLE — ${appName} built, but tests failed."
            }
            failure {
                echo "FAILURE — ${appName} did not build. Read the FIRST error."
            }
        }
    }
}
