# Phase 7 — The end-to-end demo: `helloPipeline()`

> Correlates with: `groovy-learning/specs/15-standard-pipeline.md`, and
> `java-maven-proj01/specs/08-pipeline-types-and-shared-library.md` § *Shrink java-maven-proj01's
> Jenkinsfile*.

## Why this phase exists

Everything so far has been a step you call **inside** a pipeline:

```groovy
pipeline {
    agent any
    stages {
        stage('Greet') {
            steps {
                greet(name: 'Prakash')      // ← your library
            }
        }
    }
}
```

Useful. But every repo still writes its own `pipeline { }` block. Ten repos, ten copies of the same
skeleton, drifting apart.

This phase moves the **skeleton itself** into the library. The Jenkinsfile shrinks to this:

```groovy
@Library('shared-lib') _

helloPipeline(
    name: 'catalog',
    greeting: 'Namaste'
)
```

Six lines. No stages, no agent, no `post` block. All of that now lives in one file that every repo
shares.

This is the thing shared libraries are famous for, and it is the target the whole course has been
walking towards.

---

## Part 1 — A step can contain a whole pipeline

There is no new mechanism here. It is still `vars/<name>.groovy` with a `call()` method, exactly as
in Phase 2.

The only difference is what is **inside** `call()`:

```groovy
// vars/helloPipeline.groovy
def call(Map config = [:]) {

    pipeline {
        agent any
        stages {
            stage('Greet') {
                steps {
                    echo 'hello'
                }
            }
        }
    }
}
```

```text
Jenkinsfile
     │ helloPipeline(name: 'catalog')
     ▼
helloPipeline.call([name: 'catalog'])
     │
     ▼
the pipeline { } block runs — this IS the build
```

Everything you learned still applies. Phase 4's Map config, Phase 5's classes, Phase 6's resources —
all usable inside those stages.

---

## Part 2 — The rules for `pipeline { }` inside a library

Declarative Pipeline is stricter than ordinary Groovy. Four rules to know before you start, so the
errors make sense.

### Rule 1 — compute first, then the pipeline block

Read your config and work out your values **above** the `pipeline { }` block:

```groovy
def call(Map config = [:]) {

    // 1. read + default + validate   (Phase 4's beats)
    def appName  = config.name
    def greeting = config.greeting ?: 'Hello'

    if (!appName) {
        error "helloPipeline: 'name' is required"
    }

    // 2. then the pipeline
    pipeline {
        ...
    }
}
```

The local variables are then usable inside the stages. This ordering is not a style choice; it is
how the parameterisation works at all.

### Rule 2 — `pipeline { }` must be the last thing

Do not put code **after** the closing brace of `pipeline { }`. Declarative expects the block to be
the whole of the pipeline. Anything trailing behaves in confusing ways or is simply rejected.

```groovy
def call(Map config = [:]) {
    def appName = config.name      // ✓ before — fine

    pipeline {
        ...
    }

    echo "done"                    // ✗ after — don't
}
```

If you want something to happen at the end, that is what `post` is for.

### Rule 3 — some directives insist on a literal

Most places accept a variable. A few do not, because Declarative reads them before the code runs.

The two you are most likely to meet:

```groovy
environment {
    CREDS = credentials(someVariable)      // may be rejected
}

when {
    branch someVariable                    // may be rejected
}
```

If you hit a parse error on a line like these, you have found this rule — not a mistake in your
logic. The practical fallback is to use a literal there and lose that one piece of
parameterisation.

This exact risk is written down in `java-maven-proj01/specs/08-…`. Worth reading once you have seen
it happen.

### Rule 4 — steps go inside `steps { }`

```groovy
stage('Greet') {
    echo 'hi'          // ✗ not allowed here
    steps {
        echo 'hi'      // ✓
    }
}
```

Declarative has fixed slots: `stage` holds directives and a `steps` block; only `steps` holds steps.
The error for getting this wrong is usually the unhelpful
`Expected one of "steps", "stages"…` — now you know what it means.

---

## Part 3 — Scripted is the other option

You may see library pipelines written like this instead:

```groovy
def call(Map config = [:]) {
    node {
        stage('Greet') {
            echo 'hello'
        }
    }
}
```

That is **Scripted Pipeline**. `node { }` instead of `agent`, plain Groovy inside, no fixed slots.

| | Declarative (`pipeline { }`) | Scripted (`node { }`) |
|---|---|---|
| Structure | fixed, strict | none, it is just Groovy |
| `post` blocks, `when`, `options` | built in | you write them by hand |
| Flexibility | limited in a few places | unlimited |
| Industry default | **yes** | older, still supported |

Use Declarative. Reach for Scripted only when Declarative genuinely cannot express something. Knowing
both exist is enough for now — when you meet a library written in Scripted style, you will recognise
it rather than being baffled by it.

---

## Part 4 — `post`, and knowing how the build went

`post` runs after the stages, whatever happened:

```groovy
post {
    success { echo "✅ ${appName} built" }
    failure { echo "❌ ${appName} failed" }
    always  { echo "Build ${currentBuild.number} finished: ${currentBuild.currentResult}" }
}
```

```text
stages run
    │
    ├── all good      →  success { }
    ├── something failed →  failure { }
    │
    └── either way    →  always { }
```

Order on screen does not matter; Jenkins runs the ones that apply.

`currentBuild` is the build itself, available anywhere:

| | |
|---|---|
| `currentBuild.number` | the build number |
| `currentBuild.currentResult` | `SUCCESS`, `FAILURE`, `UNSTABLE` |
| `currentBuild.displayName` | the name shown in the UI — you can set it |

This is where a real library earns its keep: put the Slack message, the cleanup, the build-status
notification in `post`, once, and all 200 repos get it.

---

## Part 5 — What the consumer looks like

The Jenkinsfile in the application repo:

```groovy
@Library('shared-lib') _

helloPipeline(
    name: 'catalog',
    greeting: 'Namaste'
)
```

That is the entire file.

```text
app repo            library
────────            ───────
Jenkinsfile   ──►   vars/helloPipeline.groovy
(6 lines)           ├── agent
                    ├── stages
                    ├── post
                    ├── src/  classes
                    └── resources/  templates
```

Notice what the app repo no longer decides: which agent, which stages, what runs on failure. It only
says *what it is* (`name: 'catalog'`) and *how it differs* (`greeting: 'Namaste'`).

That is the trade, and it is worth saying out loud: **consistency in exchange for control.** For a
platform team running many similar repos, that trade is usually correct. For one unusual repo, it is
not — which is why real libraries keep the individual steps (`buildApp`, `greet`) available too, so a
repo can opt out of the skeleton and assemble its own.

---

## Part 6 — The payoff, and the new danger

### The payoff

Change one file in the library, push, and every repo picks it up on its next build. No pull request
in 200 repos. That is the entire business case, and you will prove it in exercise 4.

### The danger

The same sentence, read again: **every repo picks it up on its next build.**

Push a broken `helloPipeline.groovy` at 5pm and every pipeline in the company fails — including the
ones nobody touched. You have turned 200 independent failures into one shared failure.

This is not a reason to avoid libraries. It is the reason for the next phase:

```text
@Library('shared-lib@main') _      follows every change — fast, risky
@Library('shared-lib@v1') _        pinned to a tag — stable, deliberate
```

Phase 8 is about that choice.

---

## What to do

### 1. Write `helloPipeline`

Create `vars/helloPipeline.groovy`. Follow Rule 1: read, default, validate, *then* the pipeline
block.

Config:

| Key | Required | Default |
|---|---|---|
| `name` | **yes** | — |
| `greeting` | no | `Hello` |

Pipeline:

* `agent any`
* **Stage 1 — Greet:** call your `greet` step (Phase 5, using the `Greeter` class)
* **Stage 2 — Build:** call `buildApp(name: ...)` (Phase 4, still pretend)
* **Stage 3 — Banner:** print the banner from `resources/` (Phase 6) with the app name filled in
* `post` with `success`, `failure` and `always`, using `currentBuild` in the `always` branch

Every earlier phase should appear in it. That is deliberate — this is the capstone of the course.

### 2. Write the consumer

New Pipeline job, `hello-pipeline-demo`. The script is the six lines from Part 5, nothing more.

Run it. Confirm you see three stages in the stage view and your `post` output at the end.

### 3. Break it on purpose

One at a time. Predict first.

| # | Break it | Watch for |
|---|---|---|
| 1 | Put `echo 'done'` after the closing `}` of `pipeline { }` | Rule 2 |
| 2 | Put a step directly in `stage { }`, outside `steps { }` | Rule 4 — and note how unhelpful the message is |
| 3 | Remove `agent any` | what Declarative says about a missing agent |
| 4 | Call `helloPipeline()` with no `name` | your own validation firing before anything runs |
| 5 | Add `when { branch someVariable }` to a stage | Rule 3 — does your Jenkins accept it? |

#5 has no guaranteed answer; it depends on your Jenkins version. Finding out is the exercise.

### 4. Prove the payoff

This is the most important exercise in the phase.

1. Add an `echo` to a stage in `vars/helloPipeline.groovy`.
2. Push the **library** only. Do not open `hello-pipeline-demo` at all.
3. Build the job again.

Your new line appears. You changed one repo and a different repo's build changed. Sit with that for
a second — that is the thing shared libraries exist to do.

### 5. Optional — point a real repo at it

If you want the full loop, create a second job that builds `java-maven-proj01` from SCM with a
Jenkinsfile containing just the `@Library` line and `helloPipeline(name: 'java-maven-proj01')`.

Use a branch, not `main` — that repo's real pipeline still works and there is no reason to disturb
it.

### 6. A question to answer

`helloPipeline` hardcodes `agent any`.

A team asks for their own agent — they need the Maven container. Do you:

* add an `agent` key to the config Map, or
* keep `agent any` and tell them to use the individual steps instead?

Write down your answer and one reason each way.

(Both happen in real libraries. The more you parameterise, the more the library looks like the thing
it replaced — a pipeline written per repo, just with more indirection. Knowing where you would draw
the line is the senior-engineer part of this job.)

---

## Done when

- [ ] `hello-pipeline-demo` runs green from a six-line Jenkinsfile.
- [ ] All three stages appear in the stage view, and `post` output is in the log.
- [ ] The pipeline uses your `vars/` step, your `src/` class and your `resources/` file.
- [ ] `helloPipeline()` with no `name` fails immediately with your message.
- [ ] You changed only the library, rebuilt the job, and saw the change appear.
- [ ] You can explain the trade the consumer repo is making by using it.

---

## Troubleshooting

| What you see | What it usually means |
|---|---|
| `Expected one of "steps", "stages", …` | A step outside `steps { }` — Rule 4 |
| `Undefined section` / `Not a valid section definition` | Something after the `pipeline` block, or a typo in a directive name — Rule 2 |
| `Perhaps you forgot to surround the code with a step that provides this, such as: node` | No agent, or work happening outside the pipeline block |
| `Expected a symbol @ line …, column …` on `credentials(x)` or `branch x` | Rule 3 — that slot wants a literal |
| The step view shows one stage, not three | An exception inside the first stage — read the first error, not the last |
| Nothing changed after a library edit | Not pushed, or the job pins a different version |

---

## Interview angle

* "Show me what a Jenkinsfile looks like in a mature shared-library setup."
* "What are the downsides of putting the whole pipeline in the library?"
* "What happens to 200 pipelines when someone pushes a broken library change, and how do you prevent
  it?"
* "When would you *not* use the standard pipeline step?"

---

## Connects to

**Phase 8** closes the course: pinning a version with `@Library('shared-lib@v1')`, using **Replay** to
iterate without pushing, and turning everything you broke on purpose into a troubleshooting
cheat-sheet.
