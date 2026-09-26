# Phase 2c — The step registry, and what happens when a step is *not* found

> **Supporting doc for [Phase 2](02-first-step-hello.md).** Continues from
> [02a §10](02a-how-hello-works-explained.md) (how `echo` is resolved) and
> [02b](02b-questions-and-clarifications.md) (how `hello()` reaches `call()`).
>
> 02a answered *how `echo` is found*. This one answers the questions that come next: **what if it
> is not found**, what the thing doing the finding actually is, and how to look inside it on your
> own Jenkins.

---

## Q1. What if `echo` is not found in the Pipeline step registry either?

Then nothing can save it, and the build fails. `methodMissing` is the **last** place to look, not a
fallback that always succeeds.

The complete flow, including the branch 02a did not draw:

```text
echo 'Hello'
     │
     ▼
┌─────────────────────────────────────┐
│ 1. A method named echo in this file │
└─────────────────────────────────────┘
     │ NO
     ▼
┌─────────────────────────────────────┐
│ 2. A method on the superclass       │
│    (groovy.lang.Script)             │
└─────────────────────────────────────┘
     │ NO
     ▼
┌─────────────────────────────────────┐
│ 3. methodMissing() is triggered     │
└─────────────────────────────────────┘
     │
     ▼
┌─────────────────────────────────────────────┐
│ 4. Jenkins' CpsScript handles it:           │
│    "is echo a registered Pipeline step?"    │
└─────────────────────────────────────────────┘
     │
     ├──── YES ────►  the step runs  ────►  Hello
     │
     └──── NO  ────►  ERROR
```

Write a step that does not exist:

```groovy
def call() {
    foobar 'Hello'
}
```

and you get the error you have already met in Phase 2:

```text
No such DSL method 'foobar' found among steps
```

Note how much that message tells you once you know the flow: Groovy got all the way to
`methodMissing`, Jenkins took over, looked in the registry, and came back empty-handed.

### The mental model to keep

`methodMissing` is a **bridge, not an implementation**:

```text
              Groovy
                │
        "I cannot find echo"
                │
                ▼
          methodMissing()
                │
                ▼
             Jenkins
                │
      ┌─────────┴─────────┐
      │                   │
  step exists       step does not
      │                   │
      ▼                   ▼
 execute the step       ERROR
```

It does not create a method. It gives Jenkins a chance to *interpret* an unknown name as a Pipeline
step. If Jenkins cannot, the name really was a mistake.

---

## Q2. So what exactly is "the Pipeline step registry"?

The set of steps **your** Jenkins knows about — and it is not a fixed list baked into Jenkins.
Plugins contribute steps, so two Jenkins installations can have different registries.

```text
                    PIPELINE STEP REGISTRY
                              │
          ┌───────────────────┼────────────────────┐
          │                   │                    │
    Pipeline core       Pipeline plugins      Other plugins
          │                   │                    │
          ▼                   ▼                    ▼
       echo()              junit()              withMaven()
       sh()                checkout()           slackSend()
       node()              archiveArtifacts()   kubernetes()
       stage()             input()              ...
       timeout()           retry()
       catchError()        ...
```

This is the practical consequence, and it is worth internalising early:

> **`No such DSL method 'x'` sometimes means "you typed it wrong" and sometimes means "that plugin
> is not installed here".**

A pipeline that works on a colleague's Jenkins and fails on yours, on a step name you copied
correctly, is almost always the second one.

### Steps you will meet, by category

| Category | Examples | What they do |
|---|---|---|
| Basic | `echo`, `error`, `sleep`, `retry`, `timeout`, `catchError` | control and output |
| Agent / process | `node`, `sh`, `bat`, `powershell`, `dir`, `ws` | run commands, allocate a workspace |
| SCM | `checkout` | fetch source |
| Build | `build` | trigger another job |
| Artifacts | `archiveArtifacts`, `stash`, `unstash` | store and move files (Phase 14) |
| Testing | `junit` | publish test results (Phase 9) |
| Credentials | `withCredentials` | borrow a secret for a block (Phase 10) |
| Input | `input` | pause for a human |
| Parallelism | `parallel` | run branches at once (Phase 14) |
| Libraries | `library`, `libraryResource`, `load` | load code and files (Phase 6) |
| Environment | `withEnv`, `tool` | change the build environment |
| Plugin-specific | `withMaven`, `slackSend`, Docker, Kubernetes | whatever is installed |

The full generated reference is at **<https://www.jenkins.io/doc/pipeline/steps/>** — generated from
plugins, which is itself the clue that the list is not fixed.

---

## Q3. How do I see the registry on *my* Jenkins?

Far more useful than memorising any list. Two places, and they answer different questions.

### Snippet Generator — "what steps do I have, and what arguments do they take?"

```text
any Pipeline job  →  Pipeline Syntax  (left menu)  →  Snippet Generator
```

or go straight to `http://localhost:8080/pipeline-syntax`.

The **Sample Step** dropdown is built from the plugins installed on *your* controller. Pick a step,
fill in the form, and it generates the exact Groovy to paste — which is the fastest way to get the
argument names right for a step you have never used.

### Global Variable Reference — "what does *my library* offer?"

```text
any Pipeline job  →  Pipeline Syntax  →  Global Variable Reference
```

This is where `env`, `params`, `currentBuild` are documented — **and where your own steps appear**,
with the text from their `vars/*.txt` file. `helloP2`, `buildAppP4` and the rest of your library are
listed there alongside Jenkins' own globals.

That is the same page Phase 3 sent you to, and it is the reason writing `.txt` docs is worth the
five minutes: it is where another person will actually look.

---

## Q4. Then why does my own library step give `No such DSL method 'helloP2'`?

Because from Groovy's point of view, nothing is different. Your step goes through the *same*
lookup, and Jenkins can only find it if the library was loaded.

```text
helloP2()
    │
    ├── a method in this Jenkinsfile?        no
    ├── a method on Script?                  no
    ├── a global variable named helloP2?     ← THIS is the one
    │      (Jenkins creates one per vars/ file, but only for LOADED libraries)
    │
    └── methodMissing → registry → not a step → ERROR
```

So `No such DSL method 'helloP2'` almost always means one of:

| Cause | Check |
|---|---|
| No `@Library('shared-lib') _` line | the first line of the Jenkinsfile |
| The library failed to load | look for `Loading library shared-lib@…` in the log |
| File name does not match the call | `vars/helloP2.groovy` → `helloP2()`, case-sensitive |
| Not pushed | Jenkins clones from GitHub, not your laptop |
| Wrong branch or tag pinned | the version in `@Library('shared-lib@…')` |

The same message, four different causes — which is why Phase 2 has you produce each of them on
purpose.

---

## Q5. Step, global variable, method — what is the difference?

Three words that get used loosely, and it is worth being precise once:

| Thing | Comes from | Example | How it is found |
|---|---|---|---|
| **Pipeline step** | Jenkins core or a plugin | `echo`, `sh`, `junit` | `methodMissing` → registry |
| **Global variable** | a `vars/` file, or Jenkins itself | `helloP2`, `env`, `currentBuild` | the binding / Jenkins' globals |
| **Method** | your own code | `call()`, `cleanup()` | normal Groovy lookup |

Which explains a detail from [02b Q1](02b-questions-and-clarifications.md): `buildAppP4` is a
*global variable* Jenkins created from the file name, and `cleanup` is a *method* on it. Jenkins
never published `cleanup` as a name of its own, so a bare `cleanup()` goes through the whole flow in
Q1 and ends at `No such DSL method 'cleanup'`.

---

## Q6. Can a shared library add its own steps to the registry?

Not to the *step registry* — that is for plugins. A library adds **global variables**, one per
`vars/` file.

In practice they are used identically:

```groovy
echo 'hi'          // a step, from a plugin
helloP2()          // a global variable, from your library
```

and that similarity is the whole design goal — a library step should feel exactly like a built-in
one. But the distinction matters in two places:

* **Discoverability.** Plugin steps appear in Snippet Generator; your library's appear in Global
  Variable Reference. Different pages, different mechanisms.
* **Privilege.** A plugin step is written in Java and reviewed by its maintainers; a trusted
  library's code is whatever your team pushed to `main`, running unsandboxed. That is the security
  point [Phase 13](13-architecture-and-governance.md) is built on.

---

## What to do

Five minutes each, and they turn this from reading into knowing.

### 1. Produce the error deliberately

In a scratch Pipeline job:

```groovy
@Library('shared-lib') _
pipeline {
    agent any
    stages {
        stage('t') {
            steps {
                foobar 'Hello'
            }
        }
    }
}
```

Read the message. Then change `foobar` to `echo` and watch the same lookup succeed.

### 2. Open your own registry

Go to `http://localhost:8080/pipeline-syntax` and scroll the **Sample Step** dropdown. Count roughly
how many there are, and find three you have never used.

Then open **Global Variable Reference** on the same page and find `buildAppP4` — your own step,
listed beside Jenkins' own.

### 3. Prove the "plugin not installed" case

Pick a step from the online reference that your Jenkins does **not** have — `slackSend` is a good
bet unless you installed the Slack plugin — and call it.

You get `No such DSL method 'slackSend' found among steps`. Exactly the same message as a typo. That
is the whole lesson of Q2: the error tells you the name was not found, never *why*.

### 4. Answer this from the log

When a build fails with `No such DSL method`, what is the **first** thing you look at, before
reading the stack trace?

(Answer: the `Loading library …` line. If the library did not load, every one of your steps is
missing, and chasing the step name is wasted effort.)

---

## Key takeaways

* `methodMissing` is a **bridge to Jenkins**, not a guarantee that something will be found.
* The step registry is **per-installation** — plugins contribute to it.
* `No such DSL method 'x'` means *the name was not found*, and says nothing about why: typo, missing
  plugin, unloaded library and unpushed code all produce it.
* Steps are discovered in **Snippet Generator**; your library's globals in **Global Variable
  Reference**.
* Before debugging the step name, check that the library loaded at all.

## References

* Pipeline Steps Reference — <https://www.jenkins.io/doc/pipeline/steps/>
* Getting started with Pipeline (Snippet Generator) — <https://www.jenkins.io/doc/book/pipeline/getting-started/>
* Pipeline Syntax — <https://www.jenkins.io/doc/book/pipeline/syntax/>
