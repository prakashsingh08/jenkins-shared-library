# Troubleshooting — Jenkins Shared Library errors

> **This file is a starter, not a finished reference.** The errors below are the ones this course
> makes you produce on purpose. Add your own as you hit them, in your own words — the wording *you*
> would search for is what makes this file worth keeping.
>
> Format that works: **what you saw → what it turned out to be → how you fixed it.**

## Before reading any stack trace, ask two questions

1. **Did I push?** Jenkins clones the library from GitHub. A file saved on your laptop does not
   exist as far as a build is concerned.
2. **Which version did this build load?** Look for `Loading library shared-lib@…` near the top of
   the log, and compare the commit SHA under it with `git log -1 --format=%H`.

Most lost time is one of those two.

And when a build fails with several errors: **read the first one, not the loudest.** Later errors
are usually the parser flailing after the first failure.

---

## The library will not load

| Message | Cause | Fix |
|---|---|---|
| `No library named shared-lib found` | The **Name** field in Jenkins and the string in `@Library('…')` differ | Make them match exactly |
| `Could not resolve shared-lib@main` / `Couldn't find any revision to build` | Branch or tag does not exist, or nothing was pushed | Check the ref exists on GitHub |
| `Authentication failed` during the clone | Private repo with no credential on the library config | Add credentials in the library's SCM settings |
| The `@version` in the Jenkinsfile is ignored | *Allow default version to be overridden* is unticked | Tick it in Manage Jenkins → System |

## The library loaded, but the code is wrong

| Message | Cause | Fix |
|---|---|---|
| `No such DSL method 'helloP2'` | Missing `@Library` line, wrong file name, or wrong case | File name **is** the step name, case-sensitive |
| `No such DSL method 'cleanup'` | Called a non-`call` method without its object | `buildAppP4.cleanup()`, not `cleanup()` |
| `Method calls on objects not allowed outside "script" blocks` | `buildAppP4.cleanup()` directly inside `steps { }` | Wrap it in `script { }` |
| `Missing required parameter: "message"` on that same line | Knock-on error from the one above | Fix the first error; this disappears |
| `unable to resolve class com.learning.phase05.Greeter` | `src/` folder path and `package` disagree, or not pushed | Path must mirror the package exactly |
| `No such property: echo for class: …Greeter` | A bare `echo` inside a `src/` class | Use `script.echo` — a class has no pipeline |
| `No signature of method: …Greeter()` | Constructor needs `script` and got nothing | `new Greeter(this)` |
| `NotSerializableException` | A library class held across steps without `implements Serializable` | Add it |
| `No such library resource com/learning/…` | Wrong `resources/` path, or not pushed | The message prints the path it tried — compare character by character |
| `expecting '}', found ''` and no stages ran | Compile error **anywhere** in `vars/` | Jenkins compiles the whole library before the build starts |

## Declarative pipeline structure

| Message | Cause |
|---|---|
| `Expected one of "steps", "stages", …` | A step outside `steps { }` |
| `Undefined section` / `Not a valid section definition` | Code after the `pipeline { }` block, or a typo in a directive |
| `Perhaps you forgot to surround the code with a step that provides this, such as: node` | No agent |
| `Expected a symbol` on `credentials(x)` or `branch x` | That slot wants a literal, not a variable |

## It runs, but does the wrong thing

| Symptom | Cause |
|---|---|
| A literal `${name}` in the log | Single quotes — `${}` only interpolates inside `"` |
| A `false` you passed comes out `true` | Elvis on a boolean: `config.x ?: true`. Use `config.get('x', false)` |
| `Building null` | A key typo with no validation — `nmae:` is not `name` |
| A step does nothing, build still green | Missing `()` on a no-argument call |
| `cat: build-info.txt: No such file` | `libraryResource` returns text; only `writeFile` creates a file |
| A placeholder like `@APP_NAME@` survives into the output | `.replace()` not called, or the marker differs between file and code |
| `$APP` is empty inside `sh` | Shell expansion with nothing set — you probably wanted `"${...}"` |
| Your change did not appear | Not pushed, or the job is pinned to another version |

## Jenkins environment

| Symptom | Cause |
|---|---|
| `mvn: not found` | No Maven on the agent — the `docker` agent is missing or was ignored |
| `The goal you specified requires a project … there is no POM in this directory` | Empty workspace: an inline *Pipeline script* job has nothing checked out. Use *Pipeline script from SCM* |
| Build is yellow, not red | Tests failed → UNSTABLE, which is not the same as FAILED |
| `env.BRANCH_NAME` is null | Only set for Multibranch / SCM-backed jobs |
| Every build re-downloads all dependencies | The `.m2` cache volume is not mounted on the agent |

---

## Your own errors

Add them here. One row each, in the wording you actually saw.

| What I saw | What it was | Fix |
|---|---|---|
| | | |
