// Phase 2 — paste this into the Jenkins job `hello-library-demo`
// (New Item → Pipeline → Pipeline section → "Pipeline script").
//
// @Library('shared-lib')  is the NAME registered in Manage Jenkins → System →
// Global Pipeline Libraries — not the repo name and not the URL.
// The trailing `_` gives the annotation something to attach to, because
// nothing is being imported.

@Library('shared-lib') _

pipeline {
    agent any

    stages {
        stage('Say hello') {
            steps {
                hello()
            }
        }
    }
}
