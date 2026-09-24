# Phase 6 — Files in `resources/`

> Correlates with: `groovy-learning/specs/10-resources.md`.

## Why this phase exists

Not everything a library needs is Groovy code.

A real library ends up carrying things like:

* a Maven `settings.xml` template
* a `Dockerfile` every project should start from
* a shell script that is 40 lines long
* a default config file

You *can* put these inside a Groovy string. People do. It looks like this:

```groovy
def settings = """<settings>
  <servers>
    <server>
      <id>cloudsmith</id>
      <username>${user}</username>
    </server>
  </servers>
</settings>"""
```

And it is horrible. No syntax highlighting. Quotes fight each other. A `$` in the file means
something to Groovy that you did not intend. Editing it is misery.

`resources/` is the answer. Put the file in the library as **a real file**, and read it when you
need it.

```text
resources/com/learning/settings.xml    ← a real XML file, edited like any other
vars/publish.groovy                    ← reads it when the build runs
```

That is the whole idea. This phase is short.

---

## Part 1 — What `resources/` is

The third folder Jenkins knows about:

```text
jenkins-shared-library/
├── vars/         steps          (Phase 2–4)
├── src/          classes        (Phase 5)
└── resources/    plain files    ← this phase
```

Anything can go in it: `.txt`, `.xml`, `.json`, `.yaml`, `.sh`, `Dockerfile`, even images.

### Paths

Files are addressed **relative to `resources/`**, with no leading slash:

```text
resources/com/learning/banner.txt
          └──────────┬──────────┘
                     │
        the path you use:  'com/learning/banner.txt'
```

The folder structure inside `resources/` is your choice. The convention is to mirror your package
name, the same way `src/` does. Why: if two libraries are loaded and both have `banner.txt`,
`com/learning/banner.txt` and `com/othercorp/banner.txt` do not collide.

---

## Part 2 — `libraryResource` reads the file

Jenkins gives you one step for this:

```groovy
def text = libraryResource('com/learning/banner.txt')
```

What it does, and what it does **not** do:

```text
libraryResource('com/learning/banner.txt')
        │
        ├── finds the file inside the loaded library
        ├── reads it
        └── returns the contents as a String
                │
                └── it does NOT create a file on disk
```

That last line is the one that surprises people. `libraryResource` hands you **text**. If you want a
file in the workspace, you write it yourself:

```groovy
def text = libraryResource('com/learning/settings.xml')
writeFile file: 'settings.xml', text: text
```

Now `settings.xml` exists in the build's workspace, and `sh 'mvn -s settings.xml ...'` can use it.

```text
resources/com/learning/settings.xml     in the library, on the controller
        │  libraryResource
        ▼
      a String in memory
        │  writeFile
        ▼
   settings.xml                          in the workspace, where sh can see it
```

Two steps, on purpose. Reading and writing are separate decisions — sometimes you only want the
text (to echo it, to parse it) and no file at all.

---

## Part 3 — Filling in the blanks

A template is only useful if you can put values into it.

Write the file with obvious placeholders:

```text
# resources/com/learning/banner.txt
==========================
  Building: @APP_NAME@
  Build:    @BUILD_NUMBER@
==========================
```

Then replace them:

```groovy
def text = libraryResource('com/learning/banner.txt')

text = text.replace('@APP_NAME@', config.name)
           .replace('@BUILD_NUMBER@', env.BUILD_NUMBER)

echo text
```

### Why `@NAME@` and not `${NAME}`

Because `${...}` already means something to Groovy and to the shell, and you will spend an evening
finding out which one ate your placeholder. A marker like `@APP_NAME@` means nothing to anybody, so
nothing touches it by accident.

Pick a marker style, use it in every template, and the whole category of bug disappears.

### A caution worth having now

`replace` puts the value into the file **exactly as given**. If that value came from a build
parameter, you have the Phase 4 problem again — an unchecked value going somewhere it can cause
harm, especially if the file is then executed as a script.

The habit is unchanged: **validate before you substitute.**

### Note on fancier templating

Groovy has template engines (`SimpleTemplateEngine` and friends) that do `${}` substitution for
real. They work, but in Jenkins they bring complications — they interact badly with how pipelines are
paused and resumed, and you end up needing `@NonCPS` annotations and knowing why.

For this course, plain `.replace()` is the right tool. It is obvious, it always works, and nobody
reading it has to know anything.

---

## Part 4 — Reading a resource from a `src/` class

Same rule as Phase 5: a class has no pipeline, so it cannot call `libraryResource` on its own.

```groovy
package com.learning

class Banner implements Serializable {

    def script

    Banner(script) { this.script = script }

    def show(String appName) {
        def text = script.libraryResource('com/learning/banner.txt')   // ← script.
        script.echo text.replace('@APP_NAME@', appName)
    }
}
```

`libraryResource` is a pipeline step like any other. Steps reached from a class need `script.` in
front. Nothing new here — but it is the kind of thing people forget the first time, so it is worth
seeing written out.

---

## Part 5 — When to use a resource, and when not to

| Put it in `resources/` | Keep it inline in Groovy |
|---|---|
| More than a few lines | One or two lines |
| It is a real file format someone edits — XML, YAML, Dockerfile | A short message |
| It benefits from syntax highlighting | Nothing to highlight |
| Something non-Groovy people will maintain | Purely internal |

And one rule with no exceptions:

> **Never put secrets in `resources/`.**

A library resource is just a file in a Git repo, readable by anyone who can read the repo, and its
contents can be echoed by any pipeline that loads the library. Credentials belong in Jenkins'
credential store, injected at build time. A template with a `@PASSWORD@` placeholder is correct; a
template with the password in it is a leak.

---

## What to do

### 1. A banner

Create `resources/com/learning/banner.txt` — a few lines of ASCII art or a box, with an
`@APP_NAME@` placeholder in it.

Add a step, or extend an existing one, that:

1. reads it with `libraryResource`
2. replaces the placeholder
3. echoes the result

Push and run. Seeing your own banner in the console output is a small thing, and oddly satisfying.

### 2. A template written to the workspace

Create `resources/com/learning/build-info.txt` with at least three placeholders — app name, build
number, branch.

Write a step that fills them in, then `writeFile`s the result into the workspace as
`build-info.txt`.

Prove it actually landed:

```groovy
sh 'cat build-info.txt'
```

Then, if you want to see the full loop, `archiveArtifacts 'build-info.txt'` and download it from the
build page. That is exactly how a real pipeline produces a generated `settings.xml` or
`values.yaml`.

### 3. Read a resource from a class

Move the banner logic into `src/com/learning/Banner.groovy`, following Phase 5:
`script` field, constructor, `script.libraryResource(...)`.

Then make the `vars/` step do nothing but create it and call it.

### 4. Break it on purpose

One at a time. Predict the error first.

| # | Break it | Watch for |
|---|---|---|
| 1 | Ask for `'banner.txt'` with no folders | the "no such library resource" message — note it prints the path it looked for |
| 2 | Use a leading slash: `'/com/learning/banner.txt'` | does it still work? |
| 3 | `libraryResource` on a file you created but did not push | the same error as #1 — and a reminder of the Phase 2 loop |
| 4 | In the class, call `libraryResource(...)` without `script.` | the Phase 5 error again, in a new place |

### 5. A question to answer

Your step reads the banner on every call. The file never changes during a build.

Would you cache it in a field so it is only read once? What did Phase 2 say about `vars/` scripts
and state? What about Phase 5's per-call object?

Write your answer down. Then ask: for a file this small, does it matter at all?

(It does not. But the reasoning — where state lives, and who shares it — matters a great deal once
the thing being cached is expensive or mutable.)

---

## Done when

- [ ] A banner from `resources/` prints in your build log, with a value substituted into it.
- [ ] A filled-in template is written to the workspace and you have seen it with `cat`.
- [ ] A `src/` class reads a resource via `script.libraryResource`.
- [ ] You can explain why `libraryResource` alone does not create a file.
- [ ] You know why `@PLACEHOLDER@` is a better marker than `${PLACEHOLDER}` in a template.
- [ ] You can say why secrets must never live in `resources/`.

---

## Troubleshooting

| What you see | What it usually means |
|---|---|
| `No such library resource com/learning/banner.txt could be found` | Wrong path, not pushed, or a typo. The message shows the exact path it tried — compare it character by character |
| `cat: build-info.txt: No such file or directory` | You read the resource but never `writeFile`d it. Part 2 |
| The placeholder is still in the output | `replace` not called, or the marker in the file and in the code differ |
| `No such property: libraryResource` inside a class | Missing `script.` — Part 4 |
| The file works locally but not in Jenkins | Not pushed. Jenkins reads the library from Git, not your laptop |
| Everything is fine but the file is empty | You wrote the variable name rather than the text, or replaced the whole string by accident |

---

## Interview angle

* "What is `resources/` for, and how do you read from it?"
* "Does `libraryResource` create a file? What do you do if you need one?"
* "How would you ship a `settings.xml` template to 200 pipelines?"
* "Why should a library resource never contain credentials?"

---

## Connects to

**Phase 7** is the payoff: a single step that *is* the whole pipeline. `helloPipeline()` — stages,
`post`, and a Jenkinsfile six lines long.
