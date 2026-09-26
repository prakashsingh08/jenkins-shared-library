# Phase 2b — Questions and clarifications

> **Supporting doc for [Phase 2](02-first-step-hello.md) and
> [02a](02a-how-hello-works-explained.md).** A running list of the questions that came up while
> working through the phase, with the answers worked out in full. Add to it as you go — a question
> you had to chase down once is worth writing down, because it is usually the same question that
> trips up the next person.

---

## Q1. If `Greeter` has several methods, how does `greeter()` decide which one runs?

**Short answer:** it doesn't decide. `()` on an object always means `call()` — nothing else is ever
a candidate.

02a said `x()` is shorthand for `x.call()`. Take that literally: **`greeter()` means
`greeter.call()`**, not "pick a reasonable-looking method."

### 1. A class with several methods

```groovy
class Greeter {

    def call() {
        return "I am call()"
    }

    def hello() {
        return "I am hello()"
    }

    def goodbye() {
        return "I am goodbye()"
    }
}

def greeter = new Greeter()
```

Now:

```groovy
println greeter()
```

```text
greeter()
   ↓
greeter.call()
   ↓
"I am call()"
```

Output:

```text
I am call()
```

`hello()` and `goodbye()` are never even considered. They are not in the running.

### 2. Reaching the other methods

Name them explicitly:

```groovy
println greeter.hello()      // "I am hello()"
println greeter.goodbye()    // "I am goodbye()"
```

```text
greeter.hello()        greeter()
      ↓                     ↓
   hello()                call()
```

No ambiguity in either direction. `()` directly on the object targets `call`; anything else needs its
name.

### 3. The interesting case — *several* `call()` methods

You are allowed more than one `call`, as long as the parameters differ:

```groovy
class Greeter {

    def call() {
        return "Hello"
    }

    def call(String name) {
        return "Hello ${name}"
    }
}

def greeter = new Greeter()
```

Then:

```groovy
println greeter()            // → call()        → "Hello"
println greeter("Prakash")   // → call(String)  → "Hello Prakash"
```

### 4. So how does Groovy choose between them?

By **method name + the arguments you passed**:

```text
                 greeter(...)
                     │
                     ↓
                  call(...)
                     │
              ┌──────┴──────┐
              ↓             ↓
          call()       call(String)
              │             │
        0 arguments    1 String argument
```

This is ordinary **method overloading** — the same rule Java uses, except Groovy decides at
**runtime**, based on the actual type of the value you passed, rather than at compile time.

### 5. The same thing in a shared library

Everything above applies unchanged to `vars/` files, because a `vars/` file becomes an object too:

```groovy
// vars/buildApp.groovy
def call(String appName) { echo "Building ${appName}" }
def cleanup()            { echo "Cleaning up" }
```

```text
buildApp('catalog')     →  buildApp.call('catalog')   →  def call(String)
buildApp.cleanup()      →  cleanup()                  →  named explicitly
```

Which is exactly why Phase 3 has you write `buildApp.cleanup()` and not `cleanup()`: `cleanup` is not
`call`, so it does not get the parenthesis shortcut. Bare `cleanup()` in a Jenkinsfile fails with
`No such DSL method 'cleanup'`, because Jenkins only ever exposed the *file name* — `buildApp` — as a
global.

And the progression across phases is now just "same mechanism, more arguments":

```text
Phase 2                 Phase 3                      Phase 4
hello()                 greet('Prakash')             buildApp(name: 'catalog')
  ↓                       ↓                            ↓
call()                  call('Prakash')              call([name: 'catalog'])
```

### 6. Where overloading actually bites in a real library

Worth knowing before you meet it in an error log:

* **`null` is ambiguous.** With both `call(String)` and `call(Map)` defined, `buildApp(null)` matches
  neither preferentially and Groovy raises an ambiguity error. Overloads that differ only by object
  type are a trap; overloads that differ by *arity* (0 args vs 1) are safe.
* **A Map argument looks like named parameters.** `buildApp(name: 'catalog')` does not pass two
  things — it passes one `Map`. That is why Phase 4's step is declared `def call(Map config)`.
* **In practice, most production library steps define exactly one `call(Map config = [:])`** and
  handle variation inside the map rather than through overloads. Fewer signatures, clearer errors,
  and the call site documents itself.

### The one sentence to keep

> **`object()` means `object.call()` — and if there are several `call()` methods, Groovy picks the
> one whose parameters match the arguments you passed.**

It never chooses between `call()`, `hello()` and `goodbye()`. `()` targets `call`, full stop.

---

See also [02c — the step registry, and what happens when a step is *not* found](02c-step-registry-and-missing-steps.md).

*Next question goes here.*
