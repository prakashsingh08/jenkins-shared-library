# Phase 1 — Setup and repo layout

> Correlates with: `java-maven-proj01/specs/08-pipeline-types-and-shared-library.md`
> § *Create the shared library repository*, and `groovy-learning/specs/07-first-shared-library.md`
> § *Layout*. Read those after finishing this phase — they will read like a summary.

## Why

Before a single line of Groovy is worth writing, one fact has to sink in:

> **Jenkins does not read your laptop. It clones this repo from GitHub at the start of every build.**

Your Jenkins runs inside a Docker container (`java-maven-proj01/docker-compose.yml`). That container
has its own filesystem. `/Users/prakashsingh/Documents/Learning_Java/jenkins-shared-library` does not
exist inside it, and never will. So a library step that works perfectly in your editor does nothing
at all until it is **committed and pushed**.

That is why Phase 1 is about Git and folders instead of code. Get this wrong and every later phase
fails with a confusing error that looks like a Groovy problem but is really a "you didn't push"
problem.

Right now this repo has a GitHub remote but **zero commits**. Nothing has ever been published. This
phase fixes that.

## Concepts to understand first

### 1. What Jenkins actually does with a library

```
   you: git push                          Jenkins, at build start
        │                                        │
        ▼                                        ▼
   GitHub: prakashsingh08/               git clone --branch main <repo>
           jenkins-shared-library   ──►  into a hidden workspace on the controller
                                                 │
                                                 ▼
                                         compiles vars/*.groovy and src/**/*.groovy
                                                 │
                                                 ▼
                                         your pipeline runs, hello() now exists
```

Two consequences worth memorising:

* **A build uses the code that was on the branch when the build started.** Edit, push, *then* build.
* **A compile error in any `vars/` file fails the build before your first stage runs** — Jenkins
  compiles the whole library, not just the step you called. One bad file breaks every pipeline using
  the library. (This is the argument for Phase 8's version pinning.)

### 2. The three folders Jenkins knows

| Folder | Holds | Jenkins treats it as |
|---|---|---|
| `vars/` | `hello.groovy`, `buildApp.groovy` … | Global steps. **File name = step name** |
| `src/` | `com/learning/Greeter.groovy` | A normal Groovy source root, added to the classpath |
| `resources/` | `com/learning/banner.txt` | Files readable with `libraryResource` |

Anything else — `specs/`, `examples/`, `README.md` — Jenkins ignores. That is why the course can live
inside the library repo without affecting it.

Naming rules that bite beginners:

* `vars/` file names are **lowerCamelCase**: `buildApp.groovy`, not `BuildApp.groovy`, not
  `build-app.groovy`. A hyphen is not a legal Groovy identifier, so `build-app()` could never compile.
* `src/` uses **package folders** mirroring the package declaration, exactly like Java.
* `vars/` files are **not** classes with a `class` keyword — they are scripts. Phase 2 shows why that
  matters.

### 3. Git tracks files, not folders

`mkdir src` then `git commit` commits **nothing** — Git has no concept of an empty directory. This is
the first surprise of the phase. The two honest options:

1. Put a placeholder file in the folder (`.gitkeep` is the convention — the name has no magic, it is
   just an empty file whose only job is to exist), or
2. Don't create the folder until you have a real file for it.

Either is fine. Choose one deliberately and be able to say why.

### 4. A branch is a library version

Jenkins' "Default version" field takes **anything Git can check out**: a branch (`main`), a tag
(`v1`), or a commit SHA. That single field is the whole versioning story, and Phase 8 builds on it.
For now: everything lives on `main`.

### 5. HTTPS vs SSH — a real trap in this workspace

This repo's remote is **HTTPS**:

```
origin  https://github.com/prakashsingh08/jenkins-shared-library.git
```

but your sibling repo `java-maven-proj01` pushes over **SSH using a custom host alias**:

```
origin  git@github-prakash:prakashsingh08/java-maven-proj01.git
```

That alias is defined in `~/.ssh/config` (`Host github-prakash` → `github.com`, key
`~/.ssh/github_prakash`). So `java-maven-proj01` pushes with no password prompt, while this repo will
ask you to authenticate over HTTPS — and GitHub has not accepted account passwords since 2021, so a
plain password will be rejected.

Two directions, both correct:

* **Match your other repo** (recommended — one auth method to remember): change this remote to
  `git@github-prakash:prakashsingh08/jenkins-shared-library.git`.
* **Stay on HTTPS**: authenticate with a Personal Access Token instead of a password.

Note the distinction, because it catches people: **your `git push` URL and the URL Jenkins clones
from are independent.** You can push over SSH and still hand Jenkins the HTTPS URL in Phase 2 —
they are two different clients doing two different clones.

## Requirements

### 1. Confirm the ground is solid

Check each, and note *what the failure looks like* so you recognise it later:

```bash
docker ps                      # Docker Desktop running?
docker ps | grep jenkins       # is the Jenkins container up?
git --version
git -C . remote -v             # does this repo point at GitHub?
git -C . log --oneline         # expect: "does not have any commits yet"
```

Open `http://localhost:8080` and confirm you can reach **Manage Jenkins**. If Jenkins is down:
`cd ../java-maven-proj01 && docker compose up -d jenkins`.

### 2. Decide the GitHub repo's visibility

Open `https://github.com/prakashsingh08/jenkins-shared-library` in a browser.

* **Public** → Jenkins can clone it with no credentials. Simplest, and fine for a learning library
  that contains no secrets.
* **Private** → Phase 2 will additionally need a credential in Jenkins.

Either works. Decide now and write down which you chose — Phase 2 asks.

> A shared library is *code Jenkins executes with high privilege*. In a real company, who can push to
> it is a security control, not an afterthought. Worth holding in mind even though this one is a toy.

### 3. Create the skeleton

Create the folders from the table in Concepts §2, plus `examples/` for sample Jenkinsfiles. Apply
your §3 decision about empty folders.

Add a `.gitignore`. There is no build output in a library repo, so it stays short — macOS noise
(`.DS_Store`) and editor/IDE directories are enough. Resist adding anything speculative.

### 4. Make the first commit and push

You want, at the end of this step, a `main` branch on GitHub containing your skeleton, the README and
`specs/`.

* Stage and commit with a message that says what the commit does, in the imperative
  (`Add shared library skeleton and beginner course`), not `first commit`.
* Confirm the branch is named `main` before pushing (`git branch --show-current`). If it is not,
  `git branch -M main` renames it.
* Push with upstream tracking so later pushes are a bare `git push`.

If you hit an auth wall, that is Concepts §5 arriving on schedule — fix the remote or set up a token
rather than working around it.

### 5. Verify from the outside

This is the step people skip, and it is the one that matters:

```bash
git -C . log --oneline
git -C . status            # expect: "nothing to commit, working tree clean"
```

Then open the repo in a browser and confirm you can **see the folders on `main`**. What GitHub shows
is what Jenkins will clone. Your laptop's copy is now irrelevant to Jenkins.

### 6. Predict, then check

Answer these *before* running anything. Write the answers down; check them in Phase 2.

1. You create `vars/hello.groovy`, save it, and click **Build Now**. Nothing changed. Why?
2. You name the file `vars/Hello.groovy` and call `hello()`. What fails — the clone, the compile, or
   the step call?
3. You add a file with a syntax error to `vars/` but never call it. Does an unrelated pipeline that
   uses this library still build?
4. Jenkins runs in a container. If you registered the library with the path
   `/Users/prakashsingh/Documents/Learning_Java/jenkins-shared-library`, what would happen?

## Done when

- [ ] `git log --oneline` shows at least one commit on `main`.
- [ ] `https://github.com/prakashsingh08/jenkins-shared-library` shows your folders and README on `main`.
- [ ] `git status` is clean.
- [ ] You can state, in one sentence, why editing a file on your laptop does not change a build.
- [ ] You know whether your repo is public or private, and what that implies for Phase 2.
- [ ] You have written answers to all four Predict questions.

## Troubleshooting

| Symptom | Likely cause |
|---|---|
| `Support for password authentication was removed` | HTTPS remote + account password. Use a PAT, or switch the remote to the `github-prakash` SSH alias |
| `Permission denied (publickey)` | SSH key not loaded, or the host alias doesn't match `~/.ssh/config`. Test with `ssh -T git@github-prakash` |
| `Updates were rejected because the remote contains work that you do not have` | The GitHub repo was created with a README/licence. Pull and rebase, or reconcile the histories |
| `src/` and `resources/` missing on GitHub after pushing | Empty folders. Concepts §3 |
| `error: src refspec main does not match any` | No commit yet, or the branch has another name |

## Connects to

**Phase 2** registers this repo in Jenkins as `shared-lib` and gets your first step running. Every
edit from here on follows the same loop: *edit → commit → push → build*.
