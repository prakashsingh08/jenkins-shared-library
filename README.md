# jenkins-shared-library

A **learn-by-doing Jenkins Shared Library**, built from an empty folder to a working
end-to-end "hello world" pipeline, one small step at a time.

If you have never written a shared library before, start at
[`specs/00-overview.md`](specs/00-overview.md) and work through the phases in order.

---

## What is a Jenkins Shared Library? (60-second version)

A `Jenkinsfile` describes how *one* repo is built. When you have 5, 50 or 200 repos, those
Jenkinsfiles end up being near-identical copy-paste. The day security says *"every build must run
a vulnerability scan"*, you edit 200 files.

A **shared library** is just **a Git repo full of Groovy code that Jenkins loads into your
pipelines**. You write the logic once, here, and every Jenkinsfile calls it:

```
  app-repo-1/Jenkinsfile  ─┐
  app-repo-2/Jenkinsfile  ─┤
  app-repo-3/Jenkinsfile  ─┼──►  @Library('shared-lib')  ──►  this repo (on GitHub)
       ...                 │                                     │
  app-repo-N/Jenkinsfile  ─┘                                     ▼
                                                            vars/       → the steps you call
                                                            src/        → helper classes
                                                            resources/  → templates & files
```

Now the scan is added in **one** place and all 200 pipelines pick it up.

The mental leap for a beginner: **a file named `vars/hello.groovy` becomes a pipeline step called
`hello()`.** That is the whole trick. Everything else in this course builds on it.

---

## Repository layout

Jenkins only recognises three top-level directories. The names are fixed — you cannot rename them.

```
jenkins-shared-library/
├── vars/                  ← the public API: one file = one pipeline step
│   ├── hello.groovy         hello()            — the step's code      (you write it in Phase 2)
│   └── hello.txt            hello()            — the step's documentation (optional, HTML)
├── src/                   ← normal Groovy classes, package folders (com/company/...)   (Phase 5)
├── resources/             ← non-Groovy files (templates, JSON, scripts) read with libraryResource (Phase 6)
├── examples/              ← NOT loaded by Jenkins: sample Jenkinsfiles you copy into a job
├── specs/                 ← NOT loaded by Jenkins: the step-by-step course
└── README.md              ← you are here
```

### Step naming: one version per phase

Steps in this repo carry the phase that introduced them — `helloP2`, `greetP3`, `buildAppP3`,
`buildAppP4`. When a later phase changes a step, it gets a **new file** rather than overwriting the
old one:

```text
vars/buildAppP3.groovy    positional argument, no validation   (Phase 3)
vars/buildAppP4.groovy    Map config, defaults, validation     (Phase 4)
```

Both stay runnable, so a Jenkinsfile can call them side by side and you can see exactly what each
phase changed. A production library would not do this — it would have one `buildApp` and use Git
history — but here the point is comparing the versions, not shipping them.

The names must stay flat and camelCase: Jenkins does not load subfolders inside `vars/`, and the
file name *is* the step name.

`examples/` follows the same idea — one Jenkinsfile per phase, each meant for its own Jenkins job:

```text
examples/phase02-hello.Jenkinsfile        → job  shared-lib-phase02
examples/phase03-arguments.Jenkinsfile    → job  shared-lib-phase03
examples/phase04-map-config.Jenkinsfile   → job  shared-lib-phase04
```

Separate jobs mean every phase stays runnable: you can go back and re-run Phase 2 after finishing
Phase 4, without editing anything.

* `vars/` — lowerCamelCase file names, because the file name *is* the step name. `vars/BuildApp.groovy`
  would give you a step called `BuildApp()`, which looks wrong in a Jenkinsfile.
* `src/` — used once a step grows too big for one file (covered late in the course).
* `resources/` — anything that is not code: a `Dockerfile` template, a default config file.
* `examples/` and `specs/` are ours, for learning. Jenkins ignores unknown directories.

---

## Using this library from a Jenkinsfile

Once it is registered in Jenkins (Phase 2 of the course walks you through the UI, screen by screen):

```groovy
@Library('shared-lib') _               // the underscore is required — see Phase 2

pipeline {
    agent any
    stages {
        stage('Say hello') {
            steps {
                hello()                // ← runs vars/hello.groovy
            }
        }
    }
}
```

The lonely `_` on the first line is not a typo. `@Library(...)` is an annotation, and a Groovy
annotation must be attached to *something*. `_` is a throwaway variable that gives it something to
attach to.

---

## The course

| | Phase | You end up with |
|---|---|---|
| 1 | Repo, Git and Jenkins ready | This repo pushed to GitHub, Jenkins running |
| 2 | `hello()` — your first step | A pipeline job printing "Hello" from the library |
| 3 | Steps that take arguments | `greet('Prakash')`, `buildApp.cleanup()` |
| 4 | Configuration as a Map | `buildApp(name: 'catalog', skipTests: true)` |
| 5 | Classes in `src/` | Logic split out of `vars/`, using the `script` handle |
| 6 | Files in `resources/` | A template loaded with `libraryResource` |
| 7 | **End-to-end demo** | `helloPipeline()` — a whole pipeline in one call |
| 8 | Versions and troubleshooting | `@Library('shared-lib@v1')` + an error cheat-sheet |

> **Library name.** Throughout the course the library is registered in Jenkins as **`shared-lib`**,
> the same name `java-maven-proj01/specs/08-pipeline-types-and-shared-library.md` uses — so one
> registration serves both courses and you can compare them side by side.

Full detail, prerequisites and the working agreement: **[`specs/00-overview.md`](specs/00-overview.md)**.

---

## Related repos in this workspace

| Repo | Role |
|---|---|
| `jenkins-shared-library` (this one) | The library itself + the beginner course |
| `java-maven-proj01` | The **application** repo whose Jenkinsfile consumes this library; also hosts the local Jenkins (`docker-compose.yml`) |
| `groovy-learning` | A deeper, 21-phase Groovy-for-Jenkins course. Do that one **after** this if you want the language fundamentals and the production-grade library |
