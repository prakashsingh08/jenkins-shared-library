# Phase 11 — Testing the library

> **Extension phase.** Follows [Phase 10](10-credentials-and-error-handling.md).
>
> Correlates with: `groovy-learning/specs/17-testing-shared-libraries.md`.

## Why this phase exists

Count what it currently costs you to change one line of library code:

```text
edit  →  commit  →  push  →  open Jenkins  →  Build Now  →  wait  →  read the log
```

A minute, optimistically. And every attempt goes to `main`, the branch other jobs load. You have
been testing in production this whole time — it is only safe because you are the only user.

Phase 7 named the danger (one bad push breaks every consumer). Phase 8 gave you pinning, which
*contains* the damage. This phase is how you stop producing the bad push at all:

```text
edit  →  run tests locally  →  seconds
```

The goal is not 100% coverage. It is a test for the handful of things you would be embarrassed to
get wrong — validation, defaults, and "does this step call `sh` with the right command".

---

## Part 1 — What is actually testable

Your library has two kinds of file, and they are testable to very different degrees.

```text
src/com/learning/phase05/Greeter.groovy      a PLAIN Groovy class
     │                                        no Jenkins anywhere in it
     └── easy: create it, pass a fake script, call a method, assert

vars/buildAppP4.groovy                        a Groovy SCRIPT that expects a pipeline
     │                                        echo, sh, error must come from somewhere
     └── harder: needs a harness that pretends to be Jenkins
```

This is the payoff for Phase 5's `script` handle. You passed the pipeline in through the
constructor because the class could not find it itself — and that same seam is what lets a test pass
in something that is **not** Jenkins.

> Code that receives its dependencies is code you can test. Code that reaches out for them is not.
> That is the whole design argument for `src/`, and it is worth being able to say in an interview.

---

## Part 2 — Start with no framework at all

Before installing anything, test `Greeter` with a hand-written fake.

The class only ever calls `script.echo`. So a "pipeline" for test purposes is any object with an
`echo` method:

```groovy
class FakeScript {
    List<String> echoed = []
    void echo(String message) { echoed << message }
}
```

Then:

```groovy
def fake = new FakeScript()
new Greeter(fake, 'Namaste').greet('Prakash')

assert fake.echoed.size() == 1
assert fake.echoed[0].contains('Namaste')
assert fake.echoed[0].contains('Prakash')
```

No Jenkins, no plugins, no waiting. This runs in under a second.

Sit with how unremarkable that is. The class you wrote in Phase 5 is *ordinary code*, and ordinary
code is testable. Everything harder in this phase is about the parts that are **not** ordinary —
the `vars/` scripts.

---

## Part 3 — JenkinsPipelineUnit: a fake Jenkins

For `vars/` files you need something that provides `echo`, `sh`, `error`, `env` and the rest.
**JenkinsPipelineUnit** (JPU) is the library everyone uses for this.

What it gives you:

```text
your test
   │  loads  vars/buildAppP4.groovy
   ▼
JenkinsPipelineUnit
   │  provides fake echo / sh / error / env / currentBuild
   │  records every call in a "call stack"
   ▼
you assert on what was called, with what arguments
```

The important idea: **JPU does not run anything for real.** `sh 'mvn deploy'` does not run Maven; it
records that `sh` was called with that string. That is usually what you want to check anyway.

### The shape of a test

```groovy
class BuildAppP4Test extends BasePipelineTest {

    @Before
    void setUp() {
        super.setUp()
        // Teach the fake Jenkins about any step your code calls.
        helper.registerAllowedMethod('sh', [String], { cmd -> println "sh: ${cmd}" })
    }

    @Test
    void 'rejects a name containing shell punctuation'() {
        def step = loadScript('vars/buildAppP4.groovy')

        // Phase 4's allow-list should stop this before any sh runs.
        shouldFail { step.call(name: 'catalog; echo INJECTED') }
    }
}
```

Read that test again and notice what it is really checking: **the injection demo from Phase 3,
without Jenkins, in milliseconds.** You ran that by hand once. Now it runs on every change, forever.

### Dependencies

JPU is a normal Maven/Gradle dependency — `com.lesfurets:jenkins-pipeline-unit`, plus JUnit and a
Groovy compiler plugin. Versions move; check the current one rather than trusting a number written
in a spec. The pairing that matters is **JPU version ↔ Groovy version** — a mismatch produces
confusing compile errors, and it is the single most common setup problem.

You do not need Maven installed: you already have a Maven container.

```bash
docker run --rm -v "$PWD":/app -w /app maven:3.9-eclipse-temurin-17 mvn -B test
```

---

## Part 4 — What to test, and what to skip

Do not try to test everything. Test the things that are cheap to check and expensive to get wrong.

| Worth a test | Why |
|---|---|
| Validation rejects bad input | `buildAppP4(name: 'x; rm -rf /')` must fail. This is a security property |
| A missing required key fails clearly | Your error message is part of the API |
| Defaults are applied | Especially `skipTests: false` surviving — the Elvis bug from Phase 4 |
| The right command is built | `sh` was called with `-B` and the version flag |
| A `src/` class's logic | It is plain Groovy; there is no excuse |

| Skip it | Why |
|---|---|
| That Maven really builds | That is Maven's test suite, not yours |
| That `echo` prints | You are testing Jenkins, not your code |
| Whole `pipeline { }` blocks | JPU handles Declarative poorly; see Part 5 |

---

## Part 5 — The limits, stated honestly

Unit tests will **not** catch:

* **Declarative parse errors.** `pipeline { }` inside a `vars/` file is barely testable by JPU. So
  `helloPipelineP7` and `buildJavaP10` stay mostly untested — and that is exactly where Phase 7's
  four rules bite.
* **A wrong agent or a missing image.** No container is started.
* **Anything about the real environment** — credentials, the `.m2` volume, whether the repo was
  checked out.

Which gives the shape of a real safety net:

```text
unit tests        catch logic, validation, defaults          seconds
canary job        catches Declarative and agent problems     minutes   ← Phase 8
version pinning   limits the blast radius when both miss     always    ← Phase 8
```

Three layers, none sufficient alone. A library with tests and no canary job still breaks its
consumers on a `pipeline { }` typo.

---

## What to do

### 1. The no-framework test first

Write a plain Groovy script under `test/` that creates a fake script object and exercises
`com.learning.phase05.Greeter` — the `FakeScript` from Part 2.

Run it with the Groovy container:

```bash
docker run --rm -v "$PWD":/app -w /app groovy:jdk17 groovy test/GreeterManualTest.groovy
```

Make it fail on purpose (assert the wrong greeting) so you have seen both outcomes.

### 2. Set up the test project

Add a `pom.xml` at the repo root with JUnit, Groovy and JenkinsPipelineUnit, and a `test/` source
root. `test/` is not a folder Jenkins loads — like `specs/` and `examples/`, it is ours.

Prove the setup works with one trivial test before writing a real one. When the versions disagree
you want the error to be about *one* thing.

### 3. Test the validation

Write tests for `buildAppP4`:

| Test | Expect |
|---|---|
| `name` missing | fails, message mentions `buildAppP4` and `name` |
| `name: ''` | fails |
| `name: 'catalog; echo INJECTED'` | fails on the pattern |
| `name: 'catalog'` | succeeds |
| `skipTests: false` | the "skipping" branch is **not** taken |

That last one is the Phase 4 Elvis bug, now guarded forever.

### 4. Test the command that gets built

Assert that `buildJavaP9` calls `sh` with a command containing `-B` and `-Drevision=`.

This is the test that catches the kind of change that looks harmless — someone "tidying" the Maven
flags — and would otherwise only show up as a 10,000-line build log.

### 5. Break a step on purpose, watch a test go red

Delete the allow-list check from `buildAppP4`. Run the tests.

A red test that names the security property you just removed is a very different experience from
discovering it in production. That feeling is the point of the phase.

### 6. A question to answer

Your tests run on your laptop, by hand.

What would make them run automatically when someone pushes to the library? Sketch the answer: a
Jenkins job on this repo, pointed at a Jenkinsfile that runs `mvn test`.

Then notice the strange loop — **the shared library needs a pipeline of its own**, and it cannot use
`buildJavaP10` without a certain amount of thought about what happens when the library that builds
the library is broken. Write down how you would handle that.

---

## Done when

- [ ] `Greeter` is tested with a hand-written fake, no framework involved.
- [ ] `mvn test` runs in the Maven container and passes.
- [ ] `buildAppP4`'s validation is covered, including the injection case.
- [ ] `skipTests: false` is covered, so the Elvis bug cannot come back.
- [ ] You removed a validation rule and watched a test go red.
- [ ] You can explain why `src/` classes are easy to test and `vars/` scripts are not.
- [ ] You can name three things these tests will never catch.

---

## Troubleshooting

| What you see | What it usually means |
|---|---|
| `unable to resolve class BasePipelineTest` | JPU dependency missing or the wrong scope |
| `No signature of method: … sh()` in a test | That step is not registered — `helper.registerAllowedMethod` |
| Groovy compile errors that make no sense | JPU version and Groovy version disagree — Part 3 |
| A test passes that should fail | You asserted on nothing. Make it fail first, then make it pass |
| `pipeline { }` step tests behave oddly | Expected. JPU handles Declarative poorly — Part 5 |
| Tests pass, the real build breaks | Something in Part 5's "will not catch" list. Keep the canary job |

---

## Interview angle

* "How do you test a Jenkins shared library?"
* "Why is a `src/` class easier to test than a `vars/` step?"
* "What can't unit tests catch in a pipeline library, and what do you do about it?"
* "How would you stop a library change from breaking 200 pipelines?"
* "What would you test first in a library with no tests at all?"

---

## Connects to

| Next | Where |
|---|---|
| Why `Serializable`, why `for` instead of `.each`, why Replay exists | CPS and `@NonCPS` — a good Phase 12 |
| Versioning, deprecation and governance at scale | `groovy-learning/specs/18-architecture-and-versioning.md` |
| A full test suite for a production-sized library | `groovy-learning/specs/17-testing-shared-libraries.md` |
