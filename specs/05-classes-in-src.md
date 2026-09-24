# Phase 5 — Classes in `src/`

> Correlates with: `groovy-learning/specs/09-src-and-pipeline-context.md`, and the `MavenBuilder`
> example in [02a §11](02a-how-hello-works-explained.md).

## Why this phase exists

So far everything lives in `vars/`. That is fine for small steps.

But `vars/` files have limits. Three of them:

**1. They get long.** A `buildApp` that reads config, validates it, builds, runs tests, and publishes
is 200 lines in one file. Nobody can find anything.

**2. Logic cannot be shared.** If `buildApp` and `deployApp` both need "work out the version
number", you copy that code into both files. Now there are two copies to keep in step.

**3. They are awkward to think about.** A `vars/` file is a script bound to a running build. A
class is just a class — you can reason about it, and later test it, on its own.

So real libraries keep `vars/` thin and put the thinking in `src/`.

```text
vars/buildApp.groovy      the front door: read config, validate, delegate       (small)
src/.../AppBuilder.groovy the actual work                                       (as big as it needs)
```

This phase is also where the convenience you got for free in Phase 2 **stops working**, and you have
to do by hand what Jenkins was doing for you. That is the real lesson here.

---

## Part 1 — What `src/` is

`src/` is a normal Groovy source folder. Classes live in **packages**, and the folder path must match
the package name exactly — the same rule as Java.

```text
src/com/learning/Greeter.groovy
    └─┬─┘ └──┬──┘ └───┬────┘
      │      │        └── the class name
      │      └── part of the package
      └── part of the package
```

and inside the file:

```groovy
package com.learning

class Greeter {
    ...
}
```

```text
package com.learning     must match     src/com/learning/
class Greeter            must match     Greeter.groovy
```

Get either wrong and the class will not be found. The error usually says `unable to resolve class`,
which is Groovy's way of saying "I looked where you told me and there was nothing there."

Pick a package name once and stick to it. `com.learning` is fine for this course. Companies use
their domain backwards — `com.mycompany.ci`.

---

## Part 2 — A class does not get `echo` for free

Write the obvious thing and it fails:

```groovy
package com.learning

class Greeter {
    def greet(String name) {
        echo "Hello ${name}"        // ✗ this does not work
    }
}
```

The build fails with something like `No such property: echo`.

### Why

Phase 2 explained where `echo` comes from in a `vars/` file. Short version:

```text
vars/greet.groovy
      │
      │ Groovy compiles it as a SCRIPT
      │ Jenkins gives that script the pipeline's binding
      ▼
unknown name "echo"  →  handed to the pipeline  →  found as a step  →  runs
```

A class in `src/` has none of that:

```text
src/com/learning/Greeter.groovy
      │
      │ Groovy compiles it as an ordinary CLASS
      │ no binding, no pipeline connection
      ▼
unknown name "echo"  →  nothing to hand it to  →  error
```

The class is not "inside" the build. It is just a class. It has never heard of Jenkins.

> **The one sentence:** `vars/` files are connected to the pipeline. `src/` classes are not.

---

## Part 3 — So hand it the pipeline yourself

If the class cannot find the pipeline on its own, give it one.

```groovy
package com.learning

class Greeter {

    def script                       // somewhere to keep the pipeline

    Greeter(script) {                // the constructor takes it
        this.script = script
    }

    def greet(String name) {
        script.echo "Hello ${name}"  // now echo is found — on script
    }
}
```

Three small parts:

```text
def script                  a field: a place to store the pipeline
Greeter(script) { ... }     a constructor: receives it when the object is created
script.echo "..."           using it: echo is looked up ON the pipeline object
```

`script` is not a keyword. It is just the name everyone uses for this field. You could call it
`pipeline` or `ctx`; don't, because `script` is what every other library calls it and other people
have to read your code.

### What you can reach through it

Anything a Jenkinsfile can:

```groovy
script.echo 'hello'
script.sh 'ls -la'
script.error 'stop'
script.env.BUILD_NUMBER
script.currentBuild.result
```

The rule is simple: **whatever you would write in a Jenkinsfile, write `script.` in front of it.**

---

## Part 4 — Wiring it up from `vars/`

Now the `vars/` file becomes small. Its job is: take the config, create the object, call it.

```groovy
// vars/greet.groovy
import com.learning.Greeter

def call(Map config = [:]) {
    def greeter = new Greeter(this)      // ← this is the pipeline
    greeter.greet(config.name)
}
```

### The important line

```groovy
new Greeter(this)
```

`this`, inside a `vars/` file, **is the running pipeline script** (Phase 2). So you are handing the
class the very thing it could not find by itself.

```text
Jenkinsfile
     │ greet(name: 'Prakash')
     ▼
vars/greet.groovy            ← connected to the pipeline;  this == the pipeline
     │ new Greeter(this)
     ▼
Greeter object               ← now holds the pipeline in its `script` field
     │ script.echo "..."
     ▼
the echo step runs
```

That diagram is the whole phase. If it makes sense, you have it.

### Does the `import` line need `@Library`?

In your Jenkinsfile you already load the library:

```groovy
@Library('shared-lib') _
```

That puts both `vars/` **and** `src/` on the classpath. So inside a `vars/` file you can just
`import com.learning.Greeter`. You only need the longer form —
`@Library('shared-lib') import com.learning.Greeter` — when you want to use a `src/` class
**directly in a Jenkinsfile**, which is rare and usually a sign the logic wants to be a step instead.

---

## Part 5 — `implements Serializable`, briefly

You will see this on library classes:

```groovy
class Greeter implements Serializable {
```

Here is the reason, in plain terms.

A Jenkins build can be **paused and resumed** — if the controller restarts mid-build, the build
should carry on afterwards, not vanish. To make that possible, Jenkins saves the state of the
running pipeline to disk and reloads it later.

Anything held in that state must be **saveable**. In Java and Groovy, "saveable" means the class
implements `Serializable`.

```text
build running
     │
     │  controller restarts
     ▼
Jenkins saves pipeline state to disk   ← your objects go along for the ride
     │
     ▼
build resumes, state loaded back
```

So: **add `implements Serializable` to library classes that live across steps.** It costs nothing
and avoids a confusing `NotSerializableException` later.

Do not go further into this yet. There is a whole mechanism underneath (Jenkins rewrites pipeline
Groovy so it can be paused — "CPS"), and it explains several odd behaviours. It is worth a phase of
its own, later. For now, treat `implements Serializable` as a habit, not a mystery to solve.

---

## Part 6 — What goes where

A decision table you can actually use:

| Put it in | When |
|---|---|
| `vars/` | It is something a Jenkinsfile calls: `buildApp(...)`, `deployApp(...)` |
| `vars/` | It is short — reading config, validating, calling one or two steps |
| `src/` | The logic is long enough that the `vars/` file is hard to read |
| `src/` | Two or more steps need the same logic |
| `src/` | It is a *thing* with state — a builder, a client, a version calculator |

The shape to aim for:

```text
vars/       thin, few lines, pipeline-facing, one file per step
src/        the actual logic, reusable, no Jenkins magic except `script`
```

Two habits that keep this clean:

* **`vars/` files should read like a table of contents.** Config in, delegate out.
* **Pass `script` once, in the constructor.** Not into every method. One object, one pipeline.

---

## What to do

### 1. Create the class

Create `src/com/learning/Greeter.groovy`:

* `package com.learning` at the top
* `class Greeter implements Serializable`
* a `def script` field and a constructor that takes it
* a `greet(String name)` method that echoes a greeting **through `script`**

### 2. Make `vars/greet.groovy` use it

Rewrite `vars/greet.groovy` so it does three things only:

1. take a `Map config`
2. create a `Greeter`, passing `this`
3. call `greeter.greet(...)`

Keep the validation habit from Phase 4: if `name` is missing, `error` before creating anything.

Push, run, confirm the greeting still appears. The Jenkinsfile does not change at all — from the
outside, nothing happened. That is the point: you reorganised the inside without touching the API.

### 3. Break it on purpose

One at a time. Predict the error first, then read the real one.

| # | Break it | Watch for |
|---|---|---|
| 1 | In `Greeter.greet`, change `script.echo` to plain `echo` | the "no such property" error — Part 2 |
| 2 | Move the file to `src/com/Greeter.groovy`, leave `package com.learning` | the path/package mismatch error |
| 3 | Rename the class inside the file to `Greeting`, keep the filename `Greeter.groovy` | which name Groovy actually cares about |
| 4 | Call `new Greeter()` with no argument | what a missing constructor argument looks like |

Write down the first line of each. #1 is the one you will meet again for real.

### 4. Add a second method — and see the reuse

Add `greetAll(List names)` to `Greeter`, echoing a greeting for each name.

Then expose it from `vars/greet.groovy`, your choice how — a `greet.all([...])` method, or a
`names:` key in the config Map. Both are defensible; pick one and be able to say why.

The thing to notice: you added behaviour **without touching the pipeline-facing contract** of
`greet(name: '...')`. Existing callers carry on working.

### 5. Answer this from the code

`vars/greet.groovy` creates a new `Greeter` on every call.

Is that wasteful? Should it be created once and kept in a field on the `vars/` script instead?

Think about what Phase 2 said: a `vars/` script is created **once per build**, and two concurrent
builds get two separate instances. Write down your answer.

(Creating it per call is the safe default. Objects are cheap, and shared mutable state across a
build is the kind of bug that only appears when two things run in parallel.)

---

## Done when

- [ ] `greet(name: 'Prakash')` works, with the greeting produced inside `src/com/learning/Greeter.groovy`.
- [ ] You can explain, in one sentence, why `echo` works in `vars/` but not in `src/`.
- [ ] You can explain what `new Greeter(this)` hands over, and what `this` is at that moment.
- [ ] All four deliberate breaks were triggered and their messages noted.
- [ ] You can say what `implements Serializable` is for, without going near CPS.
- [ ] Your `vars/greet.groovy` is short enough to read in one glance.

---

## Troubleshooting

| What you see | What it usually means |
|---|---|
| `No such property: echo for class: com.learning.Greeter` | A bare `echo` inside a `src/` class. Use `script.echo` — Part 2 |
| `unable to resolve class com.learning.Greeter` | Folder path and `package` disagree, or the file was not pushed |
| `No signature of method: … Greeter()` | The constructor needs `script` and got nothing — `new Greeter(this)` |
| `NotSerializableException` | A library class holding state across steps without `implements Serializable` — Part 5 |
| Works in `vars/` but fails once moved to `src/` | Exactly the Part 2 difference. The class has no pipeline connection |
| `MissingPropertyException: No such property: script` | The field was never declared, or the constructor never assigned it |

---

## Interview angle

* "What is the difference between `vars/` and `src/` in a shared library?"
* "Why does `echo` not work inside a `src/` class, and how do you fix it?"
* "What does `new SomeClass(this)` pass, exactly?"
* "Why do shared library classes implement `Serializable`?"
* "How do you decide whether logic belongs in a step or a class?"

---

## Connects to

**Phase 6** adds `resources/` — the third folder — for files that are not code: templates, config,
scripts. Loaded with `libraryResource`.
