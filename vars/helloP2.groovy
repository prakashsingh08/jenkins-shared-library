// Phase 2 — the first shared library step.
//
// The file name is the step name: vars/helloP2.groovy becomes  helloP2()
// call() is the method Groovy runs when an object is used like a function,
// so  helloP2()  in a Jenkinsfile means  helloP2.call().
//
// `echo` needs no import here: a vars/ file is a Groovy script that Jenkins
// binds to the running pipeline, so pipeline steps resolve directly.
// (Traced in full in specs/02a-how-hello-works-explained.md.)
//
// The P2 suffix is this repo's convention: each phase keeps its own version of a
// step, so you can run and compare them side by side. See README.md.

def call() {
    echo 'Hello from the shared library'
}
