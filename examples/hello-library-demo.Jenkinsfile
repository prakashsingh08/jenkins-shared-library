// Paste this into the Jenkins job `hello-library-demo`
// (job → Configure → Pipeline section → "Pipeline script").
//
// @Library('shared-lib')  is the NAME registered in Manage Jenkins → System →
// Global Trusted Pipeline Libraries — not the repo name and not the URL.
// The trailing `_` gives the annotation something to attach to, because
// nothing is being imported.

@Library('shared-lib') _

pipeline {
    agent any

    stages {
        // Phase 2 — a step with no arguments. The () is required.
        stage('Say hello') {
            steps {
                hello()
            }
        }

        // Phase 3 — a step with one argument.
        stage('Greet') {
            steps {
                greet('Prakash')
            }
        }

        // Phase 3 — an argument, then a second method on the same step.
        stage('Build') {
            steps {
                buildApp('catalog')
            }
        }

        stage('Cleanup') {
            steps {
                buildApp.cleanup()
            }
        }
    }
}
