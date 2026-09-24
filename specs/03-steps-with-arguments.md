# Phase 3 — Steps that take arguments

> Correlates with: `groovy-learning/specs/07-first-shared-library.md` § *Real steps with arguments*.
> Read that after this phase, not before.

## Why this phase exists

In Phase 2 you wrote a step that always does the same thing:

```groovy
hello()
```

It says hello. That is all it can ever do.

A useful step needs to be **told something**:

```groovy
buildApp('catalog')
```

Now the same step can build `catalog` today and `payments` tomorrow. One file, many uses. That is
the whole point of a library.

This phase adds arguments. Along the way it covers two things that waste more beginner hours than
anything else:

1. **Which quote character you used** — `'single'` or `"double"`.
2. **Putting a variable inside a shell command.**

Both are explained slowly below.

---

## Part 1 — An argument is just a value you hand to the step

### The step

```groovy
// vars/buildApp.groovy
def call(String appName) {
    echo "Building ${appName}"
}
```

### The call

```groovy
buildApp('catalog')
```

### What happens

```text
buildApp('catalog')
      ↓                      Jenkins: the file name is the step name
buildApp.call('catalog')
      ↓                      Groovy: () means call()   (Phase 2, 02b Q1)
def call(String appName)
      ↓                      the value 'catalog' is copied into appName
appName = 'catalog'
      ↓
echo "Building catalog"
```

So `appName` is just a name for the value you passed in. Nothing more mysterious than that.

### Should you write `String appName` or `def appName`?

Both work.

```groovy
def call(String appName) { ... }   // typed
def call(def appName)    { ... }   // untyped
```

Use the typed version in a library. Two reasons:

1. **It documents the step.** Someone reading the file sees immediately that a String is expected.
2. **Wrong input fails with a clearer message.**

One thing the type does *not* do: it does not catch mistakes early. Groovy checks the type when the
code **runs**, not when it is compiled. So a bad call still reaches Jenkins. It just fails with a
better message once it gets there.

---

## Part 2 — `'single'` vs `"double"` quotes

This is Groovy. It has nothing to do with Jenkins. And it is absolute.

### The rule

```groovy
def appName = 'catalog'

echo "Building ${appName}"     // prints:  Building catalog
echo 'Building ${appName}'     // prints:  Building ${appName}
```

| Quotes | Name | `${appName}` becomes |
|---|---|---|
| `"double"` | GString | the **value** — `catalog` |
| `'single'` | plain String | the **literal text** `${appName}` |

Single quotes never substitute anything. Ever.

### Why it is called a GString

`"..."` with a `${}` inside is not a normal String. Groovy creates a different object, called a
**GString** (Groovy String). It holds the text plus the values to slot in, and it produces the
finished text when it is used.

You do not need to think about that often. But it explains the name when you see it in an error
message.

### How to recognise the bug

You will one day look at a build log and see this:

```text
Building ${appName}
```

The value did not appear. Every single time, the cause is the same: single quotes. Change them to
double quotes.

**Rule of thumb:** if the string contains `${...}` and you want the value, you need `"`.

---

## Part 3 — Quotes inside `sh` — two different engines

This is the part worth reading twice.

When you write a shell step, **two different things can replace a variable**, at two different times:

* **Groovy** replaces `${...}` before the command is sent anywhere.
* **The shell** replaces `$NAME` when it runs the command.

They look similar. They are not.

### Case A — Groovy substitutes

```groovy
def appName = 'catalog'
sh "echo building ${appName}"
```

```text
Step 1  Groovy builds the string     → "echo building catalog"
Step 2  Jenkins sends that to the shell
Step 3  the shell runs               → echo building catalog
```

The shell never sees the word `appName`. It was already gone.

### Case B — the shell substitutes

```groovy
sh 'echo building $APP'
```

```text
Step 1  Groovy does nothing          → 'echo building $APP'   (single quotes!)
Step 2  Jenkins sends that to the shell
Step 3  the shell looks up $APP in its own environment and replaces it
```

Here `$APP` must exist as an **environment variable** inside the shell. If it does not exist, the
shell replaces it with nothing, and you get:

```text
building
```

An empty value where you expected a word almost always means this.

### Why anyone would choose Case B

Because of what ends up in the build log.

With Case A, Groovy bakes the value into the command, and Jenkins prints the command it ran:

```text
+ echo building catalog
```

If that value were a password, it is now in the log, in plain text, forever.

With Case B, the command that gets printed still says `$APP`. The value is resolved inside the
shell and never printed.

That is why real credential-handling code uses single quotes. You are not doing credentials yet.
For now, just store the fact:

> `"` and `'` are not two styles of the same thing. They do two different jobs.

### A picture

```text
sh "echo ${appName}"                 sh 'echo $APP'
       │                                    │
       │ Groovy replaces it                 │ Groovy leaves it alone
       ▼                                    ▼
"echo catalog"                       'echo $APP'
       │                                    │
       ▼                                    ▼
   the shell runs it                 the shell replaces $APP, then runs it
```

---

## Part 4 — Why putting input into `sh` is dangerous

Look at this line:

```groovy
sh "echo pretend: mvn clean package for ${appName}"
```

If you typed `'catalog'` yourself, it is fine.

Now imagine `appName` did not come from you. Imagine it came from a build parameter, or a branch
name, or a pull request title. Someone sets it to:

```text
catalog; echo INJECTED
```

Groovy builds this command:

```text
echo pretend: mvn clean package for catalog; echo INJECTED
```

The shell sees a `;`. To a shell, `;` means *"end of command, here comes another one."* So it runs
**two** commands. The second one is not yours.

```text
your command          their command
─────────────────     ──────────────
echo pretend: ...  ;  echo INJECTED
```

`echo INJECTED` is harmless. `rm -rf /` is not. This is called **command injection**.

Why it is worse in a shared library:

* Library code runs **outside the Jenkins sandbox** — it is allowed to do more than a Jenkinsfile.
* **Every pipeline** using the library inherits the hole, not just one project.

The habit to build, starting now:

> **Check the value before you put it in a command.**

Phase 4 adds that check. In this phase you just need to see the problem happen, so the warning means
something.

---

## Part 5 — Two methods in one file

A `vars/` file can hold more than one method:

```groovy
// vars/buildApp.groovy
def call(String appName) { ... }
def cleanup()            { ... }
```

How you reach each one:

```groovy
buildApp('catalog')     // runs call('catalog')
buildApp.cleanup()      // runs cleanup()
```

Why the difference:

```text
buildApp('catalog')          buildApp.cleanup()
      ↓                            ↓
() means call()              you named the method
      ↓                            ↓
def call(String)             def cleanup()
```

Only `call` gets the short form. Every other method needs its name. (This is the rule from
[02b Q1](02b-questions-and-clarifications.md) — same thing, now in a real file.)

A common mistake:

```groovy
cleanup()           // ✗ No such DSL method 'cleanup'
```

Jenkins only published **one** name to your Jenkinsfile: `buildApp`, from the file name. It never
published `cleanup`. So `cleanup()` on its own means nothing.

### Calling it from a Declarative pipeline needs `script { }`

Here is the part that surprises everyone the first time:

```groovy
steps {
    buildApp('catalog')      // ✓ fine — this is a step call
    buildApp.cleanup()       // ✗ Method calls on objects not allowed outside "script" blocks
}
```

Both lines call your library. Declarative accepts the first and rejects the second. The reason is
what each line *looks like* to the Declarative parser:

```text
buildApp('catalog')      a name followed by arguments   →  "that is a step"       ✓
buildApp.cleanup()       a method called ON an object   →  "that is Groovy code"  ✗
```

A `steps { }` block may only contain **step calls**. Anything that is general Groovy — a method call
on an object, an assignment, an `if`, a `for` loop — has to go inside a `script { }` block:

```groovy
steps {
    script {
        buildApp.cleanup()
    }
}
```

`script { }` is Declarative's escape hatch: *"the strict rules stop here, treat this as ordinary
Groovy."*

You will also see a puzzling second error on the same line:

```text
Missing required parameter: "message"
```

Ignore it. Having failed to parse the line as Groovy, Declarative tries to read it as a step and
produces a message about the wrong thing entirely. The first error is the real one — a habit worth
carrying generally: **read the first error, not the loudest.**

> **Design note.** If a library method is awkward to call, that is information. Many libraries
> avoid `x.method()` for this exact reason and ship a separate `vars/cleanupWorkspace.groovy`
> instead, so callers write `cleanupWorkspace()` with no `script { }` wrapper. Knowing both
> options — and why one is friendlier to Declarative users — is the real lesson here.

### When to add a second method, and when not to

Put a method in the same file if it belongs to the **same idea**:

```text
buildApp('catalog')      build the app
buildApp.cleanup()       clean up after building      ✓ same idea
buildApp.sendSlack()     post a Slack message         ✗ different idea — own file
```

A file with five unrelated methods is a file nobody can find anything in.

---

## Part 6 — Why parentheses are sometimes optional

You have seen both of these:

```groovy
echo 'hi'
echo('hi')
```

Both are the same call. Groovy lets you drop the parentheses on a method call **when you pass at
least one argument**.

That is the entire reason pipelines look like a special language:

```groovy
echo 'building'
sh 'mvn -B test'
junit 'target/surefire-reports/*.xml'
```

These look like keywords. They are ordinary method calls with the parentheses removed.

### The important exception

A call with **no arguments** must keep its parentheses.

```groovy
hello()      // ✓ calls the step
hello        // ✗ does nothing at all
```

Why: bare `hello` is just the name of the object. Writing a name by itself is not an instruction to
do anything. Groovy reads it, shrugs, and moves on.

The nasty part: **the build still goes green.** Nothing failed. Nothing ran either.

> A step that "does nothing, but the build passed" is nearly always a missing `()`.

---

## Part 7 — Documenting a step with a `.txt` file

Put a file next to your step, with the same name:

```text
vars/buildApp.groovy     the code
vars/buildApp.txt        the documentation
```

The `.txt` file contains **HTML**. Not Markdown. Not plain text.

```html
<p>Builds an application.</p>
<h3>Usage</h3>
<pre>
buildApp('catalog')
buildApp.cleanup()
</pre>
```

Jenkins shows it here:

```text
your job  →  Pipeline Syntax  →  Global Variable Reference
```

That page lists every step your libraries provide. It is the closest thing a shared library has to
published documentation, and it is the first place a teammate looks.

A library with twenty undocumented steps is a library only you can use.

---

## What to do

### 1. Write `greet`

Create `vars/greet.groovy`. One `call()` method. It takes a name and echoes a greeting containing
that name.

Then try these four, one at a time. **Write down what you expect before each run.**

| # | Try this | Think about |
|---|---|---|
| 1 | `greet('Prakash')` | the normal case |
| 2 | `greet 'Prakash'` | no parentheses — Part 6 |
| 3 | `greet()` | no argument at all — what does the error say? |
| 4 | change your `echo` to single quotes | Part 2 |

#3's error message is worth reading closely. It tells you which signatures *would* have worked.

### 2. Write `buildApp`

Create `vars/buildApp.groovy` with two methods:

* `def call(String appName)` — echo what is being built, then a `sh` step that *pretends* to build:
  `echo pretend: mvn clean package for ...`
* `def cleanup()` — echo that the workspace is being cleaned.

Keep the build pretend. A real `mvn` needs the Maven container agent, which is Phase 7's job, and a
failing Maven build would teach you nothing new here.

Above the `sh` line, write a comment **in your own words** about the danger from Part 4. Write it
yourself; it is the kind of note that stops you doing the unsafe thing in two years' time.

Then add stages to `hello-library-demo` that call `buildApp('catalog')` and `buildApp.cleanup()`.

### 3. Document it

Create `vars/buildApp.txt` in HTML. Cover:

* what the step does
* how to call both methods
* the parameter: its name, its type, whether it is required

Push. Then open **Pipeline Syntax → Global Variable Reference** and find your step.

Not showing up? Check, in this order:

1. Is the `.txt` name *exactly* the `.groovy` name?
2. Did you push?
3. Does the job you opened that page from actually load the library?

### 4. Break the argument on purpose

Predict first, then run. One at a time.

1. `buildApp(42)` — a number where a String is declared.
2. `buildApp('catalog', 'extra')` — too many arguments.
3. Rename `cleanup()` to `call()`, so the file has two `call` methods — one with no arguments, one
   with a String. Now try `buildApp()` and `buildApp('catalog')`. Which runs which? (This is the
   overloading from 02b Q1, in a real file.)

### 5. See the injection happen

In a scratch pipeline — **not** in the library — do this:

```groovy
def appName = 'catalog; echo INJECTED'
buildApp(appName)
```

Read the shell output carefully. You will see your step run a command you never wrote.

Nothing is harmed; `echo INJECTED` does nothing. But watching it happen is far more convincing than
any warning. Note what it would have taken to stop it. Phase 4 builds exactly that.

---

## Done when

- [ ] `greet('Prakash')` and `buildApp('catalog')` both work from the library.
- [ ] `buildApp.cleanup()` runs, and you can say why it needs the method name.
- [ ] You can state the `'` vs `"` rule without pausing, and say which one lets the **shell** expand
      a variable.
- [ ] Your step appears, with docs, in Pipeline Syntax → Global Variable Reference.
- [ ] You have seen the injection demo run, and can say why a shared library makes it worse.
- [ ] You know what `greet()` with no argument reports.

---

## Troubleshooting

| What you see | What it usually means |
|---|---|
| The log prints a literal `${appName}` | Single quotes. Part 2 |
| `MissingMethodException: … greet() is applicable for argument types: ()` | Wrong number or type of arguments. The message lists what *would* have worked — read that part |
| `No such DSL method 'cleanup'` | You wrote `cleanup()` instead of `buildApp.cleanup()`. Part 5 |
| `Method calls on objects not allowed outside "script" blocks` | `buildApp.cleanup()` directly inside `steps { }`. Wrap it in `script { }` — Part 5 |
| `Missing required parameter: "message"` on that same line | A knock-on error from the one above. Fix the first error and this disappears |
| `$APP` comes out empty inside `sh` | Shell expansion with nothing set. You probably wanted Groovy's `${}` with double quotes. Part 3 |
| Your step is missing from Global Variable Reference | `.txt` name mismatch, not pushed, or that job does not load the library |
| The step does nothing, but the build is green | Missing `()` on a no-argument call. Part 6 |

---

## Interview angle

* "What is the difference between `sh "echo ${x}"` and `sh 'echo $x'`?"
* "How do you expose more than one operation from a single `vars/` file — and when should you not?"
* "How would you stop a branch name from reaching a shell command in a library step?"
* "How do other teams find out what steps your library offers?"

---

## Connects to

**Phase 4** swaps positional arguments for a **Map**:

```groovy
buildApp(name: 'catalog', skipTests: true)
```

That is how nearly every real library step takes input. It also adds the defaults and the
validation this phase kept promising.
