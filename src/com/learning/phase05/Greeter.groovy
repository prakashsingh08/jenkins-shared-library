// Phase 5 — a class in src/.
//
// The folder path must match the package name exactly:
//     src/com/learning/phase05/Greeter.groovy   ←→   package com.learning.phase05
// and the file name must match the class name. Get either wrong and Groovy says
// "unable to resolve class", which means "I looked where you told me and found nothing".
//
// The phase05 package is this repo's retention convention: each phase keeps its own
// copy so you can compare them. A real library would have one com.learning package.

package com.learning.phase05

// implements Serializable: a Jenkins build can be paused and resumed (a controller
// restart should not lose a running build), so Jenkins saves the pipeline's state to
// disk. Objects taking part in that state must be saveable. Costs nothing, and avoids
// a confusing NotSerializableException later.
class Greeter implements Serializable {

    // A place to keep the pipeline. `script` is not a keyword — it is just the name
    // every shared library uses for this field, so keep it.
    def script

    String greeting

    // The pipeline is handed in when the object is created: new Greeter(this, 'Hello')
    Greeter(script, String greeting = 'Hello') {
        this.script = script
        this.greeting = greeting
    }

    void greet(String name) {
        // A class in src/ is NOT connected to the pipeline, so a bare `echo` here would
        // fail with "No such property: echo". Everything a Jenkinsfile can call is
        // reached through the handle instead: script.echo, script.sh, script.env ...
        script.echo "${greeting} ${name} — from a class in src/"
    }

    void greetAll(List<String> names) {
        // A plain for loop, not names.each { ... }.
        // Closures inside a src/ class run into how Jenkins rewrites pipeline Groovy for
        // pause/resume, and can fail in ways that are hard to read. A for loop always works.
        for (String name : names) {
            greet(name)
        }
    }
}
