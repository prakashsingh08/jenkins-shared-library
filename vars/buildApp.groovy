// Phase 3 — an argument plus a second method in the same file.
//
//   buildApp('catalog')   →  call('catalog')   (() always means call())
//   buildApp.cleanup()    →  cleanup()         (any other method needs its name)
//
// The build is pretended with echo for now. Phase 9 replaces it with real Maven,
// which needs a container agent.

def call(String appName) {
    echo "Building ${appName}"

    // SAFETY NOTE — appName is interpolated straight into a shell command.
    // That is fine for a value typed by hand, but NOT for anything that came from
    // a build parameter, a branch name or a PR title: a value like
    //     catalog; rm -rf /
    // would be read by the shell as a SECOND command and run.
    // This step is unsafe on purpose right now — Phase 4 adds the check that fixes it.
    sh "echo pretend: mvn clean package for ${appName}"
}

def cleanup() {
    echo 'Cleaning up the workspace (pretend)'
}
