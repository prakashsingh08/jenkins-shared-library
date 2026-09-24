// Phase 3 — an argument plus a second method in the same file.
//
//   buildAppP3('catalog')   →  call('catalog')   (() always means call())
//   buildAppP3.cleanup()    →  cleanup()         (any other method needs its name)
//
// In a Declarative pipeline, buildAppP3.cleanup() must go inside a script { } block:
// steps { } accepts step calls only, and a method call on an object is ordinary Groovy.
//
// The build is pretended with echo. Phase 4 (buildAppP4) adds Map config, defaults and
// validation; Phase 9 replaces the pretend build with real Maven on a container agent.

def call(String appName) {
    echo "Building ${appName}"

    // SAFETY NOTE — appName is interpolated straight into a shell command.
    // That is fine for a value typed by hand, but NOT for anything that came from
    // a build parameter, a branch name or a PR title: a value like
    //     catalog; rm -rf /
    // would be read by the shell as a SECOND command and run.
    // This step is unsafe on purpose — buildAppP4 adds the check that fixes it.
    sh "echo pretend: mvn clean package for ${appName}"
}

def cleanup() {
    echo 'Cleaning up the workspace (pretend)'
}
