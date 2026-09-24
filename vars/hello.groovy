// Phase 2 — the first shared library step.
//
// The file name is the step name: vars/hello.groovy becomes  hello()
// call() is the method Groovy runs when an object is used like a function,
// so  hello()  in a Jenkinsfile means  hello.call().
//
// `echo` needs no import here: a vars/ file is a Groovy script that Jenkins
// binds to the running pipeline, so pipeline steps resolve directly.
// (Traced in full in specs/02a-how-hello-works-explained.md.)

def call() {
    echo 'Hello from the shared library'
}
