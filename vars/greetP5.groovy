// Phase 5 — the same greeting, now produced by a class in src/.
//
// Compare with vars/greetP3.groovy: from a Jenkinsfile's point of view nothing has
// changed except the config style. The logic simply moved.
//
// This is the shape to aim for: a vars/ file reads like a table of contents —
// take config, validate, create the object, call it. The thinking lives in src/.

import com.learning.phase05.Greeter

def call(Map config = [:]) {

    // read + default
    def names    = config.names ?: (config.name ? [config.name] : [])
    def greeting = config.greeting ?: 'Hello'

    // validate — before creating anything
    if (!names) {
        error "greetP5: pass 'name' or 'names', e.g. greetP5(name: 'Prakash') or greetP5(names: ['A', 'B'])"
    }

    // `this` is the running pipeline script, which is exactly what the class cannot
    // find on its own. Handing it over is the whole point of Phase 5.
    def greeter = new Greeter(this, greeting)

    greeter.greetAll(names)
}
