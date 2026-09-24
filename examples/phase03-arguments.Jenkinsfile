// PHASE 3 — steps that take arguments
//
// Jenkins job:  shared-lib-phase03
//   New Item → Pipeline → Pipeline section → Definition: "Pipeline script" → paste this.
//
// What this phase demonstrates:
//   * a positional argument           greetP3('Prakash')
//   * a second method on a step       buildAppP3.cleanup()
//   * why that second one needs       script { }

@Library('shared-lib') _

pipeline {
    agent any

    stages {
        // One argument, passed positionally.
        // Groovy also allows  greetP3 'Prakash'  with no parentheses — try it.
        stage('Greet') {
            steps {
                greetP3('Prakash')
            }
        }

        // The step's call(String) method.
        // NOTE: this version does NOT validate its input. Phase 4 fixes that.
        stage('Build') {
            steps {
                buildAppP3('catalog')
            }
        }

        // buildAppP3.cleanup() is a method call ON an object, not a step call, so
        // Declarative rejects it directly inside steps { }:
        //     Method calls on objects not allowed outside "script" blocks
        // script { } is the escape hatch — "treat this as ordinary Groovy".
        stage('Cleanup') {
            steps {
                script {
                    buildAppP3.cleanup()
                }
            }
        }
    }
}
