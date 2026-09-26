# Phase 14 — Parallel stages, `stash`, and build speed

> **Extension phase.** Follows [Phase 13](13-architecture-and-governance.md).
>
> The 13-phase arc is complete without this. This is the one remaining thing a real library does
> daily that the course has not covered.

## Why this phase exists

A build that takes 20 minutes changes how a team works. People batch up changes, stop running it,
and review less carefully because waiting is expensive.

The library is the right place to fix that, for the same reason it was the right place for
credentials: **fix it once, everyone gets faster.** A team cannot make their own build fast if the
pipeline is not theirs.

This phase covers the two levers with the biggest effect — doing things at the same time, and not
doing things twice — plus the one Groovy bug that catches everyone who builds parallel stages
dynamically.

---

## Part 1 — `parallel` in Declarative

Stages that do not depend on each other can run at once:

```groovy
stage('Checks') {
    parallel {
        stage('Unit tests') {
            steps { sh 'mvn -B test' }
        }
        stage('Lint') {
            steps { sh 'mvn -B checkstyle:check' }
        }
        stage('Security scan') {
            steps { sh 'echo pretend: scanning' }
        }
    }
}
```

```text
sequential                          parallel
──────────                          ────────
tests    ████████ 8 min             tests    ████████ 8 min
lint         ██ 2 min               lint     ██
scan          ███ 3 min             scan     ███
            = 13 min                         = 8 min
```

Rules worth knowing before you use it:

* `parallel` replaces `steps` **inside a stage**. A stage has either `steps` or `parallel`, not both.
* Each inner stage may have its own `agent` — which is the real reason to use it: one branch on a
  Maven container, another on a Node one.
* Add `failFast true` to kill the rest as soon as one branch fails. Good when the branches are all
  gates; bad when you want the full picture of what is broken.

```groovy
stage('Checks') {
    failFast true
    parallel { ... }
}
```

### When parallel makes things worse

* **Three branches on one agent competing for two CPUs** — you paid the coordination cost and got
  nothing.
* **Branches that share a workspace and write the same files** — now you have a race.
* **A log that becomes unreadable.** Interleaved output is the real cost people forget; `timestamps()`
  and clear `echo`s help.

Measure first (Part 5). "Parallel" is not a synonym for "faster".

---

## Part 2 — Building parallel branches dynamically

This is where a library differs from a Jenkinsfile. You often do not know the branches in advance —
the caller passes a list of modules, environments, or test suites.

The Declarative `parallel { }` block is fixed at parse time, so you build a **Map of name →
closure** and hand it to the `parallel` *step* instead:

```groovy
def branches = [:]
branches['unit']  = { sh 'mvn -B test' }
branches['lint']  = { sh 'mvn -B checkstyle:check' }

parallel branches
```

Inside a `vars/` step that would go in a `script { }` block, or in the step's own body.

### The bug that catches everyone

Build that Map in a loop and you will probably write this:

```groovy
def branches = [:]
for (String name : modules) {
    branches[name] = { sh "mvn -B test -pl ${name}" }   // ✗ WRONG
}
parallel branches
```

Every branch runs with the **last** value of `name`. Three modules, three identical commands.

Why: the closure does not copy `name`, it **refers to the variable**. By the time the closures run,
the loop has finished and `name` holds its final value.

```text
loop runs           closures run later
─────────           ──────────────────
name = 'a'  ──┐
name = 'b'  ──┼──→  all three closures read `name`  →  'c'
name = 'c'  ──┘
```

The fix: give each closure its **own** variable, declared inside the loop.

```groovy
for (String name : modules) {
    def moduleName = name                                  // a fresh variable per iteration
    branches[moduleName] = { sh "mvn -B test -pl ${moduleName}" }   // ✓
}
```

This is ordinary Groovy closure capture, not a Jenkins quirk — but Jenkins is where most people meet
it, because nothing fails. You just get three identical branches and a confusing afternoon.

> Related, from [Phase 12](12-cps-and-noncps.md): build the Map with a `for` loop, not `.each`,
> when the bodies call steps.

---

## Part 3 — `stash` and `unstash`: moving files between agents

Each agent has its own workspace. A file built on one is not on another:

```text
agent A (maven)          agent B (docker)
───────────────          ────────────────
target/app.jar           (empty workspace)
                         ← the jar is NOT here
```

`stash` and `unstash` move a small set of files between them, via the controller:

```groovy
stage('Build') {
    agent { docker { image 'maven:3.9-eclipse-temurin-17' } }
    steps {
        sh 'mvn -B package'
        stash name: 'jar', includes: 'target/*.jar'
    }
}

stage('Package image') {
    agent any
    steps {
        unstash 'jar'
        sh 'ls -la target/'
    }
}
```

Two limits that matter:

* **Stashes go through the controller** and live in memory/disk there. They are for jars, reports
  and small artifacts — not for a 2GB container image or a whole workspace.
* **A stash lives for one build.** For anything that must outlive the build, use
  `archiveArtifacts`, or a real artifact repository — which is what Phase 10's publish does.

A library step that spans agents almost always needs a stash, and forgetting it produces the most
literal error possible: the file is not there.

---

## Part 4 — Not doing things twice

Parallelism hides slowness. Caching removes it. In rough order of payoff:

| Lever | What it saves | Where |
|---|---|---|
| Maven `.m2` volume | re-downloading every dependency, every build | already in `buildJavaP9` |
| `-B` (batch mode) | thousands of progress lines, and log parsing time | already there |
| Shallow clone / no tags | minutes on a large repo | job's SCM config: *Advanced clone behaviours* |
| `skipDefaultCheckout()` + one explicit checkout | checking the repo out per stage on multi-agent builds | pipeline `options` |
| Reusing one agent for several stages | container start-up per stage | top-level `agent` |
| `-DskipTests` on a package that already tested | running the suite twice | `buildJavaP9` does this |

And two that prevent *waste* rather than saving time:

```groovy
options {
    timeout(time: 30, unit: 'MINUTES')   // a hung build stops occupying an agent
    disableConcurrentBuilds()            // stop two builds fighting over one workspace
}
```

`retry(2)` exists too, but think before adding it: retrying a flaky test hides the flake, while
retrying a network fetch is reasonable. A library that retries everything teaches its users to
distrust red builds.

---

## Part 5 — Measure before you optimise

Jenkins tells you where the time went, if you ask.

* **`options { timestamps() }`** — already in `buildJavaP9`. Every log line gets a clock, so you can
  see which command took the eight minutes.
* **The stage view** on the job page — per-stage durations, and the trend across builds. The stage
  that grew is usually visible at a glance.
* **`currentBuild.duration`** — usable in `post` if you want to echo or record it.

The discipline: write down the before number. "It feels faster" is not a result, and optimisations
that make no difference are very common — especially parallelism on a single small agent.

---

## What to do

### 1. Add parallel checks to a library step

Copy `buildJavaP9` to `buildJavaP14` and replace the Test stage with a `parallel` block containing
unit tests plus one or two pretend checks (`echo pretend: lint`).

Run it. Compare the total time with the sequential version — and be honest about whether it actually
helped on your single-agent local Jenkins.

### 2. Build branches dynamically

Add a `modules` config key (a List). Build the parallel Map in a loop, as in Part 2, and run one
pretend command per module.

Then **write it the wrong way first** — without the fresh variable inside the loop — and watch every
branch print the same module. Only then fix it.

That bug is worth producing on purpose. It never throws; it just quietly does the wrong thing.

### 3. Try `failFast`

Make one branch fail. Run with and without `failFast true`, and compare what the stage view shows
you in each case.

Decide which you would want as the default for a library used by other teams, and why.

### 4. Stash something between agents

Split a step into two stages on different agents: build the jar on the Maven container, then
`unstash` it on `agent any` and `ls` it.

First run it **without** the stash, to see the plain "file not found" — the clearest demonstration
of what a workspace is.

### 5. Measure something real

Pick `buildJavaP9` against `java-maven-proj01`:

1. Note the current duration.
2. Remove the `.m2` volume from `args`. Run twice. Note the duration.
3. Put it back. Run twice.

You now have a number for what that one line is worth. That is the sort of fact that makes a good
answer in an interview, and a good argument in a planning meeting.

### 6. A question to answer

Your library's standard pipeline runs checks in parallel. A team complains that their build is now
*slower*, because their agent has two CPUs and three heavy branches.

What do you do — a config key to turn parallelism off, agent-aware defaults, or tell them to use the
individual steps? Write down your answer.

(This is Phase 7's question yet again, in its most concrete form: the library optimises for the
common case, and the common case is not everyone.)

---

## Done when

- [ ] A library step runs checks in `parallel` and you know whether it actually helped.
- [ ] You built parallel branches from a List, and produced the loop-capture bug on purpose first.
- [ ] You can explain in one sentence why every branch got the last value.
- [ ] You moved a file between two agents with `stash` / `unstash`, having first seen it fail.
- [ ] You have a real before/after number for the `.m2` cache.
- [ ] You can name a case where parallel makes a build slower.

---

## Troubleshooting

| What you see | What it usually means |
|---|---|
| Every parallel branch runs the same command | Closure capture — no fresh variable inside the loop. Part 2 |
| `Expected one of "steps", "stages", "parallel"…` | A stage with both `steps` and `parallel`, or steps outside either |
| Files missing in a later stage | Different agent, different workspace. You need `stash` / `unstash` |
| `No such saved stash 'jar'` | Never stashed, a name typo, or a different build |
| Parallel branches corrupt each other's files | They share a workspace. Give them separate agents or separate directories (`dir { }`) |
| The build is slower than before | Contention on one agent, or the branches were never the bottleneck. Part 5 |
| A hung build occupies an agent forever | No `timeout` in `options` |

---

## Interview angle

* "How do you run stages in parallel, and when should you not?"
* "How would you build parallel branches when the list is only known at runtime?"
* "Why would every parallel branch end up running the same command?"
* "How do you move a built artifact between two agents?"
* "A build takes 25 minutes. Walk me through how you would approach it."
* "When is `retry` the wrong fix?"

---

## Connects to

| Next | Where |
|---|---|
| Agent types and where builds run | `java-maven-proj01/specs/16-jenkins-agent-types.md` |
| Build caching in depth | `java-maven-proj01/specs/11-build-caching.md` |
| Parallel builds from the Jenkins side | `java-maven-proj01/specs/13-parallel-builds.md` |
| Pipeline optimisation | `java-maven-proj01/specs/15-pipeline-optimization.md` |
