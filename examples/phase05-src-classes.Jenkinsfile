// PHASE 5 — logic moved into a class in src/
//
// Jenkins job:  shared-lib-phase05
//   New Item → Pipeline → Pipeline section → Definition: "Pipeline script" → paste this.
//
// The point of this phase is invisible from here: greetP3 and greetP5 both just
// print a greeting. The difference is where the work happens.
//
//   greetP3  → all the logic sits in vars/greetP3.groovy
//   greetP5  → vars/greetP5.groovy only reads config and delegates to
//              src/com/learning/phase05/Greeter.groovy

@Library('shared-lib') _

pipeline {
    agent any

    stages {
        // The Phase 3 version, for comparison.
        stage('Greet (Phase 3 style)') {
            steps {
                greetP3('Prakash')
            }
        }

        // The same output, produced inside a src/ class.
        stage('Greet (via src class)') {
            steps {
                greetP5(name: 'Prakash')
            }
        }

        // A custom greeting — passed to the class's constructor.
        stage('Custom greeting') {
            steps {
                greetP5(name: 'Prakash', greeting: 'Namaste')
            }
        }

        // Behaviour the class added, without changing the step's existing contract.
        stage('Greet several people') {
            steps {
                greetP5(names: ['Prakash', 'Asha', 'Ravi'], greeting: 'Hi')
            }
        }

        // Validation still happens in the vars/ file, before the object is created.
        // Uncomment to see it fail with our own message:
        //
        // stage('No name') {
        //     steps {
        //         greetP5()
        //     }
        // }
    }
}

// ---------------------------------------------------------------------------
// BREAK IT ON PURPOSE (edit the library, push, re-run — or use Replay)
//
//  1. In Greeter.greet, change  script.echo  to a bare  echo
//     →  No such property: echo for class: com.learning.phase05.Greeter
//        The class is not connected to the pipeline. This is the phase in one error.
//
//  2. Move Greeter.groovy up one folder, leaving  package com.learning.phase05
//     →  unable to resolve class ...   (path and package must agree)
//
//  3. Rename the class inside the file to Greeting, keep the filename Greeter.groovy
//     →  which name does Groovy actually care about?
//
//  4. In greetP5.groovy, call  new Greeter()  with no arguments
//     →  what a missing constructor argument looks like
// ---------------------------------------------------------------------------
