# Phase 2a — How `hello()` actually works (the slow version)

> **Supporting doc for [Phase 2](02-first-step-hello.md).** Phase 2's Concepts §1 and §2 say the same
> things in half a page. If that half page felt dense, read this first and then go back — it builds
> the same model from zero, one small idea at a time.
>
> Nothing here is extra work. There is no exercise, no checklist. Just the picture.

The whole phase rests on one sentence:

> Jenkins maps the **filename** `hello.groovy` to the name `hello`, and Groovy makes `hello()` call
> that object's `call()` method.

Two different mechanisms, from two different places, meeting in the middle. Most beginner confusion
is really the two being mistaken for one. So we take them apart.

---

## 1. First, forget Jenkins for two minutes

Plain Groovy. An ordinary object:

```groovy
class Calculator {

    def add() {
        return 10 + 20
    }
}

def calculator = new Calculator()
```

You call it the normal way:

```groovy
calculator.add()
```

Because:

```text
calculator
    ↓
object
    ↓
add()
```

Nothing surprising.

---

## 2. Groovy has one special convenience

Now give an object a method named exactly `call`:

```groovy
class Greeter {

    def call() {
        return "Hello"
    }
}

def greeter = new Greeter()
```

You *could* write the normal thing:

```groovy
greeter.call()
```

But Groovy offers a shortcut — use the object as if it were a function:

```groovy
greeter()
```

These two are equivalent:

```groovy
greeter()
greeter.call()
```

```text
greeter()
   ↓
Groovy sees an object being "called"
   ↓
looks for a method named call()
   ↓
greeter.call()
```

**This is plain Groovy.** No Jenkins involved. Any Groovy object with a `call()` method can be
invoked like a function. That tiny rule is the key to the whole of `vars/`.

---

## 3. Now bring Jenkins in

Your library:

```text
jenkins-shared-library/
│
└── vars/
    └── hello.groovy
```

and inside `hello.groovy`:

```groovy
def call() {
    echo 'Hello from the shared library'
}
```

Notice what is *missing*. There is no:

```groovy
class Hello {
}
```

That is deliberate. A file inside `vars/` is a **Groovy script**, not a normal class. Methods sit at
the top level, with nothing wrapped around them.

---

## 4. How does `hello()` find `hello.groovy`? — the Jenkins half

This part is Jenkins' job, and it is pure convention.

Jenkins sees:

```text
vars/hello.groovy
```

and exposes it to your Jenkinsfile under the name:

```text
hello
```

```text
Jenkinsfile
     │
     │ hello()
     ↓
Jenkins looks in the shared library
     │
     │ finds
     ↓
vars/hello.groovy
```

The filename *becomes* the name you type:

```text
vars/hello.groovy     → hello
vars/buildApp.groovy  → buildApp
vars/deploy.groovy    → deploy
```

That is the **Jenkins half** of the story, and it is the whole of it. Jenkins does not care what is
inside the file yet — it just makes the name available.

---

## 5. Now the Groovy half

Jenkins has handed you an object representing that file:

```text
hello
  ↓
an object created from hello.groovy
```

Then you wrote:

```groovy
hello()
```

Groovy reads that and thinks:

> "You're using this object as if it were a function."

So Groovy looks for:

```groovy
call()
```

Which means:

```groovy
hello()
```

is really:

```groovy
hello.call()
```

and therefore this runs:

```groovy
def call() {
    echo 'Hello from the shared library'
}
```

**This is why the method must be named `call`.** Not because Jenkins demands it — because Groovy's
"call an object like a function" rule looks for that exact name.

> *"But what if the object has several methods — how does Groovy pick?"* It doesn't pick: `()`
> always targets `call`, and other methods are reached by name. Worked through in
> [02b, Q1](02b-questions-and-clarifications.md#q1-if-greeter-has-several-methods-how-does-greeter-decide-which-one-runs).

---

## 6. The complete picture

```text
                    Jenkinsfile
                        │
                     hello()
                        │
                        ▼
              Jenkins Shared Library
                        │
                        │ filename
                        ▼
                 vars/hello.groovy
                        │
                        │ Jenkins creates an object
                        ▼
                   hello  (object)
                        │
                        │ Groovy sees ()
                        ▼
                    hello.call()
                        │
                        ▼
                 def call() {
                     echo "Hello"
                 }
                        │
                        ▼
                  the Jenkins echo step
```

Two separate mechanisms:

```text
Jenkins:                  Groovy:
vars/hello.groovy         hello()
       ↓                        ↓
     hello                hello.call()
```

Hold those apart in your head and the rest of the course gets easier.

---

## 7. The next question: where did `echo` come from?

Look again:

```groovy
def call() {
    echo 'Hello from the shared library'
}
```

You never wrote any of these:

```groovy
import echo
def echo() { }
Jenkins.echo()
```

And yet `echo 'Hello'` works. This is where the Pipeline machinery starts.

---

## 8. In normal Groovy, this would fail

```groovy
class Person {

    def greet() {
        hello()          // where is hello() defined?
    }
}
```

There is no `hello()` anywhere, so this blows up at runtime.

But a `vars/` script is not floating in space. Jenkins connects it to the **running Pipeline** by
giving it the Pipeline's *binding*, so names can be resolved against the live build.

---

## 9. What is a "binding"?

Ignore the intimidating word.

> **Binding = a bag of things the script can reach by name.**

Conceptually, the Pipeline's bag holds:

```text
Pipeline context
│
├── echo
├── sh
├── junit
├── archiveArtifacts
├── withCredentials
├── env
├── params
└── currentBuild
```

Your `vars/` script is plugged into *that* bag — not a fresh empty one. That connection is what makes
the next step possible.

---

## 10. So why can `vars/hello.groovy` use `echo`?

Groovy sees:

```groovy
echo "Hello"
```

and asks: *where is `echo`?* It checks the script, the superclass, the binding… and finds nothing.

Normally that is an error. But Jenkins' Pipeline script implements a Groovy hook called:

```text
methodMissing()
```

which means roughly: *"if a method name cannot be found, hand it to me and I'll deal with it."*
Jenkins deals with it by checking its registry of installed Pipeline steps.

```text
"echo"
  ↓
not found by normal resolution
  ↓
methodMissing → Jenkins Pipeline
  ↓
is "echo" a registered Pipeline step?  yes
  ↓
execute the echo step
```

Do not memorise `methodMissing` yet. For now:

> **Jenkins catches the unknown method name and checks whether it is a Pipeline step.**

That one hook is why `echo`, `sh`, `junit`, `withCredentials` and every plugin-provided step work
with no import anywhere.

---

## 11. Why a class in `src/` cannot do this

Now a thing you have already met elsewhere makes sense. Compare:

```groovy
class MavenBuilder {

    def script

    MavenBuilder(script) {
        this.script = script
    }

    def build() {
        script.echo("Building")
        script.sh("mvn package")
    }
}
```

Why not simply this?

```groovy
class MavenBuilder {

    def build() {
        echo "Building"        // ✗ fails
        sh "mvn package"       // ✗ fails
    }
}
```

Because `MavenBuilder` is a **normal class**. It is not a script, it has no Pipeline binding, and it
does not inherit Jenkins' `methodMissing`. There is genuinely nothing there to resolve `echo`
against.

```text
vars/hello.groovy
       │
       └── connected to the Pipeline
                 │
                 ├── echo()
                 ├── sh()
                 └── env

src/MavenBuilder.groovy
       │
       └── a normal Groovy class
                 │
                 └── knows nothing about echo / sh / env
```

Hence the `script.` prefix:

```groovy
script.echo(...)
script.sh(...)
```

*(This course's Phase 5 does the same thing with a smaller class called `Greeter`. Same mechanism,
fewer moving parts — and `MavenBuilder` from the `groovy-learning` course is the production-sized
version of it.)*

---

## 12. Why `this`?

Inside a `vars/` script:

```groovy
def call() {
    echo "Hello"
}
```

`this` *is* the running Pipeline script. These two lines do the same thing:

```groovy
echo 'hi'
this.echo('hi')
```

So when you write:

```groovy
new MavenBuilder(this)
```

you are saying:

```text
give MavenBuilder the current Pipeline context
```

The class stores it:

```groovy
this.script = script
```

and can then reach everything in the bag:

```groovy
script.echo(...)
script.sh(...)
script.env
```

---

## 13. One picture to remember

The single-page version of everything above — worth a look now that each piece makes sense on its
own — is at the top of [Phase 2's Concepts section](02-first-step-hello.md#concepts-to-understand-first).
Box 5 in it is section 10 of this doc; box 6 is a map of which words belong to Groovy, which to
Jenkins, and which to your own code.

And the picture for the `vars/` → `src/` handover:

```text
                  Jenkinsfile
                       │
                       │ hello()
                       ▼
              ┌──────────────────┐
              │ vars/hello.groovy│
              │                  │
              │ def call() {     │
              │   echo "Hello"   │
              │ }                │
              └────────┬─────────┘
                       │
                       │ has Pipeline context
                       ▼
                Jenkins Pipeline
                 /      |       \
              echo     sh       env
                       │
                       │ passed along as `this`
                       ▼
              ┌──────────────────┐
              │ MavenBuilder     │
              │      src/        │
              │                  │
              │ script.echo()    │
              │ script.sh()      │
              │ script.env       │
              └──────────────────┘
```

---

## 14. And where does `Serializable` fit?

You may have seen:

```groovy
class MavenBuilder implements Serializable {
```

The reason: Jenkins Pipelines can **pause and resume** — a controller restart should not lose a
running build. To make that possible, Jenkins saves the state of a running pipeline to disk, and
objects taking part in that state need to be serializable.

Do not chase this down into CPS yet. Park it as:

```text
vars/
 ↓
Pipeline-connected script
 ↓
can use echo, sh, env directly

src/
 ↓
normal class
 ↓
does NOT know Pipeline steps
 ↓
receive `script`

implements Serializable
 ↓
lets the class take part safely
in Jenkins' save/resume model
```

---

## The five things worth remembering

**① `vars/hello.groovy`**

```text
filename → step name
```

**② `hello()`**

```text
hello()
  ↓
hello.call()
```

**③ `call()`**

```text
a special Groovy method name that lets
an object be used like a function
```

**④ `vars/` vs `src/`**

```text
vars/ → Pipeline-connected script
src/  → normal Groovy class
```

**⑤ `script`**

```text
normal class
     ↓
needs Pipeline context
     ↓
receives `script`
     ↓
script.echo() / script.sh() / script.env
```

---

## Now go and do it

Don't move on to CPS, or to `src/`, or to anything clever. The next useful thing is concrete: create
`vars/hello.groovy`, push it, register the library, run `hello()`, and read the console output line
by line.

That is exactly what [Phase 2](02-first-step-hello.md) walks you through. Go back to it now — the
Concepts section should read as a summary of what you just understood, not as new material.
