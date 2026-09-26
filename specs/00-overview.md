# Overview — Jenkins Shared Library from zero to a working "hello world" pipeline

## Who this is for

Someone who has seen a `Jenkinsfile` once or twice, has Jenkins running locally, and has **never**
built a shared library. No Groovy knowledge is assumed — every Groovy idea is explained the first
time it appears.

By the end you will have, in this repo, a library that a 6-line Jenkinsfile can call to run an
entire build:

```groovy
@Library('shared-lib') _

helloPipeline(
    name: 'catalog',
    greeting: 'Namaste'
)
```

…and you will be able to explain *what Jenkins does with each of those lines*.

## The one idea

> **A shared library is a Git repo. Jenkins clones it before your pipeline starts, and every file in
> `vars/` becomes a step you can call.**

`vars/hello.groovy` → the step `hello()`. `vars/buildApp.groovy` → the step `buildApp()`.
Everything in this course is a refinement of that single sentence.

## Phases

Eight phases, each one spec file, each building on the last. Work in order — Phase 4 assumes
Phase 2's Jenkins job exists.

| # | Spec | Concepts introduced | You produce | Status |
|---|---|---|---|---|
| 1 | [01-setup-and-repo-layout.md](01-setup-and-repo-layout.md) | Why `vars/` / `src/` / `resources/`; Git doesn't track empty folders; branches vs tags as library versions; HTTPS vs SSH remotes | This repo committed and pushed to GitHub; Jenkins reachable on `localhost:8080` | **Done** |
| 2 | [02-first-step-hello.md](02-first-step-hello.md) (+ [02a — how `hello()` works, the slow version](02a-how-hello-works-explained.md), [02b — questions & clarifications](02b-questions-and-clarifications.md)) | `call()`, `@Library`, the `_`, registering a Global Pipeline Library in the Jenkins UI, reading library errors | `vars/hello.groovy` + a `hello-library-demo` pipeline job that prints it | **Done** |
| 3 | [03-steps-with-arguments.md](03-steps-with-arguments.md) | Method arguments, `'` vs `"` interpolation, shell injection, extra methods (`buildApp.cleanup()`), optional parens, `.txt` docs | `vars/greetP3.groovy`, `vars/buildAppP3.groovy`, `vars/buildAppP3.txt` | **Code pushed** |
| 4 | [04-configuration-as-a-map.md](04-configuration-as-a-map.md) | Groovy Maps, named arguments as one Map, Groovy truth, Elvis vs `get(k, default)`, validating input, failing fast with `error` | `vars/buildAppP4.groovy` with defaults and an allow-list check | **Code pushed** |
| 5 | [05-classes-in-src.md](05-classes-in-src.md) | Why `vars/` files get too big; packages and folder layout; passing `this` as `script` so a class can `echo`/`sh`; `implements Serializable`; what goes where | `src/com/learning/phase05/Greeter.groovy` used by a thin `greetP5` step | **Code pushed** |
| 6 | [06-resources-and-templates.md](06-resources-and-templates.md) | `libraryResource` vs `writeFile`, placeholder templating, reading resources from a `src/` class, why secrets never go here | `resources/com/learning/phase06/*` printed by `bannerP6` + written by `buildInfoP6` | **Code pushed** |
| 7 | [07-end-to-end-hello-pipeline.md](07-end-to-end-hello-pipeline.md) | A step that *is* the whole pipeline; the four Declarative rules; Scripted vs Declarative; `post` and `currentBuild`; the payoff and its danger | `vars/helloPipelineP7.groovy` + a 6-line consumer Jenkinsfile | **Code pushed** |
| 8 | [08-versioning-and-troubleshooting.md](08-versioning-and-troubleshooting.md) | Branch vs tag vs SHA, SemVer and the moving `v1` tag, who pins to what, **Replay**, a full error cheat-sheet, deprecating a config key | A `v1.0.0` tag + [specs/troubleshooting.md](troubleshooting.md) | **Code pushed** |

Update the **Status** column as you finish each phase.

**Status meanings:** *Done* — finished, including the exercises. *Code pushed* — the library files
exist and run, but the phase's exercises (predicting, breaking things on purpose) are still yours to
do; that is where most of the learning is.

### Extension phases (optional, after the course)

The eight phases above are complete on their own. These go further, when you want them.

| # | Spec | Concepts introduced | You produce | Status |
|---|---|---|---|---|
| 9 | [09-from-pretend-to-real-maven.md](09-from-pretend-to-real-maven.md) | Docker agents and the `.m2` cache volume; why an inline job has no source code; `checkout scm`; `junit`, `archiveArtifacts`, UNSTABLE vs FAILED; what belongs in the library vs the app repo | `vars/buildJava.groovy` running a real Maven build of `java-maven-proj01` from a branch | **Ready to do** |
| 10 | [10-credentials-and-error-handling.md](10-credentials-and-error-handling.md) | Where secrets live; `withCredentials` vs `environment { credentials() }`; what masking misses; closures and wrapper steps; `try`/`catch`/`finally`, and why swallowing an exception is worse than the failure | `vars/withCloudsmithP10.groovy` + `vars/buildJavaP10.groovy` with a guarded Publish stage | **Code pushed** |
| 11 | [11-testing-the-library.md](11-testing-the-library.md) | Why `src/` classes are testable and `vars/` scripts are not; a hand-written fake `script`; JenkinsPipelineUnit; what to test and what to skip; the three layers of safety net | A `test/` folder and a `pom.xml`; validation and defaults covered by tests that run in seconds | **Spec only** |

### Why this order

* **1–2 get something on screen fast.** The single biggest beginner blocker is not Groovy, it is
  Jenkins not finding the library at all. We hit that wall early, while the code is one line long
  and easy to rule out as the cause.
* **3–4 are the shape of every real step**: take input, apply defaults, validate, act.
* **5–6 are the "where do I put this?" phases** — the questions you ask once a step outgrows one file.
* **7 is the payoff**: the end-to-end demo the whole repo is named after.
* **8 is what makes it safe to share** with other teams: pinned versions, and knowing how to read
  the errors.

## Prerequisites

| Need | How to check | If missing |
|---|---|---|
| Jenkins on `localhost:8080`, admin access | Open it, look for **Manage Jenkins** | `cd ../java-maven-proj01 && docker compose up -d jenkins` |
| Jenkins **Pipeline** plugin (includes Shared Groovy Libraries) | Manage Jenkins → Plugins → Installed → search "Pipeline: Shared Groovy Libraries" | Install it, restart Jenkins |
| Git + a GitHub account | `git --version` | — |
| This repo has a GitHub remote | `git -C . remote -v` | Already set to `prakashsingh08/jenkins-shared-library` |
| Docker Desktop running | `docker ps` | Start Docker Desktop |

You do **not** need a local Groovy or JDK install. All Groovy in this course runs inside Jenkins.

**Note on the current state of this repo:** `vars/` has been **emptied on purpose** — the earlier
`hello.groovy` / `buildApp.groovy` / `buildApp.txt` from the `groovy-learning` course were removed so
you start from a clean slate and type every file yourself. Phase 1 is done: the skeleton
(`vars/`, `src/`, `resources/`, `examples/`, `.gitignore`) is committed and pushed to `main`, so
Jenkins can clone the repo. Everything from Phase 2 onward is still yours to write.

## Working agreement

This repo teaches the same way the rest of this workspace does:

1. **Specs describe what and why; you write the code.** A spec shows small illustrative snippets
   and the exact Jenkins UI path to click, but never a finished file to paste. Typing it yourself is
   the point.
2. **One phase at a time.** Read the whole spec first, attempt it, then ask for help. Getting stuck
   and debugging is the learning, not a detour from it.
3. **Predict before you run.** Where a spec asks "what do you think this prints?", answer before
   clicking Build. The gap between your guess and the console output is the lesson.
4. **Finish the "Done when" checklist** at the end of each phase before moving on.
5. **Every push matters.** Jenkins reads the library from GitHub, not from your laptop. A change
   that is not pushed does not exist as far as the pipeline is concerned. (Phase 8 shows "Replay",
   the escape hatch for fast experiments.)

## Glossary (the words that trip beginners up)

| Term | Plain meaning |
|---|---|
| **Step** | Anything you can call inside `steps { }` — `echo`, `sh`, `junit`, and your own `hello()` |
| **`vars/`** | The folder whose file names become step names. Not "variables" in the normal sense |
| **`call()`** | The method Groovy runs when you use an object like a function. `hello()` really means `hello.call()` |
| **Global Pipeline Library** | Jenkins' name for a library registered once, available to every job |
| **Implicit load** | The "Load implicitly" checkbox — makes the library available with no `@Library` line. We keep it **off**, so the dependency stays visible in each Jenkinsfile |
| **Consumer** | A repo whose Jenkinsfile uses the library. Here: `java-maven-proj01`, or a scratch job |
| **Replay** | A Jenkins build's "Replay" link — lets you edit the pipeline *and* library code for one run without committing |
| **CPS** | Jenkins rewrites your Groovy so a build can survive a restart. It is why some normal Groovy behaves oddly. Only mentioned here; the `groovy-learning` course goes deep |

## Where this fits with the other repos

```
Learning_Java/
├── jenkins-shared-library/     ← you are here: the library + this beginner course
│   ├── specs/                    the 8 phases
│   ├── vars/  src/  resources/   the library Jenkins loads
│   └── examples/                 sample Jenkinsfiles to copy into a job
├── java-maven-proj01/          ← the app repo + the local Jenkins (docker-compose.yml)
└── groovy-learning/            ← the deeper 21-phase Groovy course, do it after this one
```

If the two courses ever disagree, this one is the simplified beginner path; `groovy-learning` is the
production-grade version of the same ideas.

## Correlating with the specs you already have

This course does not invent a new setup. It walks the **same** setup as the two specs below, just
slower and with the reasoning spelled out — so you can read them side by side.

| Source spec | What it gives you | Where it lands in this course |
|---|---|---|
| `java-maven-proj01/specs/08-pipeline-types-and-shared-library.md` § "Create the shared library repository" | The exact `mkdir` / `git init` / `git remote add` / `git push -u origin main` sequence that created this repo | **Phase 1** — you finish it (the push was never made) |
| same spec, § "Register the library in Jenkins" | Manage Jenkins → System → **Global Pipeline Libraries** → Add: Name `shared-lib`, Default version `main`, Modern SCM → Git, `https://github.com/prakashsingh08/jenkins-shared-library.git`, *Load implicitly* unchecked | **Phase 2** — done once, reused by every later phase |
| same spec, § "Shrink java-maven-proj01's Jenkinsfile" | `@Library('shared-lib@main') _` + one call — the real payoff | **Phase 7**, in miniature: `helloPipeline()` instead of `simpleMavenPipeline()` |
| `groovy-learning/specs/07-first-shared-library.md` § "Concepts", "`vars/` rules" | `vars/` = global steps, camelCase names, one instance per build, `this` is the pipeline script | **Phase 2 and 3**, unpacked one rule at a time |
| same spec, § "Break it on purpose" | Four deliberate failures: wrong file name, syntax error, missing `@Library`, bad branch | **Phase 2** (first two) and **Phase 8** (the cheat-sheet) |
| same spec, § "Real steps with arguments" | `buildApp('catalog')`, `buildApp.cleanup()`, `buildApp.txt` | **Phase 3**, same files, written from scratch |
| same spec, § "Version pinning" | Tag `v0.1.0`, compare `@v0.1.0` vs `@main` | **Phase 8** |

**Reading order that works well:** do a phase here first, then skim the matching section above. The
source specs are terser and assume more; after the phase they read as a summary rather than a wall.

**The one thing to know before Phase 1:** `java-maven-proj01`'s Phase 8 was started but never
finished — this repo has a GitHub remote but no commits, no `simpleMavenPipeline.groovy`, and
`java-maven-proj01/Jenkinsfile` still contains the full inline pipeline. Nothing is broken; there is
simply nothing published yet. Phase 1 is where that changes. Whether the `shared-lib` registration
already exists in your Jenkins is something Phase 2 has you check rather than assume.

## Next step

Ask for **Phase 1** (`01-setup-and-repo-layout.md`) and it will be written into this folder.
