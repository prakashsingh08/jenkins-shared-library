// Phase 3 — a step that takes an argument.
//
//   greet('Prakash')  →  greet.call('Prakash')  →  call(String name)
//
// Note the DOUBLE quotes on the echo line. "${name}" is interpolated by Groovy.
// With single quotes it would print the literal text ${name} instead.

def call(String name) {
    echo "Hello ${name}, welcome to the shared library"
}
