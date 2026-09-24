// PHASE 2 — your first library step
//
// Jenkins job:  shared-lib-phase02
//   New Item → Pipeline → Pipeline section → Definition: "Pipeline script" → paste this.
//
// @Library('shared-lib')  is the NAME registered in Manage Jenkins → System →
// Global Trusted Pipeline Libraries — not the repo name and not the URL.
// The trailing `_` gives the annotation something to attach to, because nothing
// is being imported.
//
// helloP2() needs its parentheses. A bare `helloP2` is just a reference to the
// object: nothing runs, and the build still goes green.

@Library('shared-lib') _

pipeline {
    agent any

    stages {
        stage('Say hello') {
            steps {
                helloP2()
            }
        }
    }
}
