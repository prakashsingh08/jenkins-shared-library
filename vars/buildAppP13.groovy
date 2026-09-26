// PHASE 13 — a real deprecation, done properly.
//
// The change: the config key `name` is being renamed to `appName`, because `name` is
// vague once a step takes eight keys.
//
// With twenty consumers you cannot simply rename it. You would break twenty pipelines
// belonging to people who never asked for the improvement. So this step is STEP 1 of
// the three-step deprecation from GOVERNANCE.md:
//
//   1. ACCEPT BOTH, warn on the old one           ← this file. A MINOR release.
//   2. WAIT           a quarter, not a fortnight. The warning lands in the logs of
//                     exactly the people who must act.
//   3. REMOVE         in a MAJOR release. Consumers opt in by changing their pin.
//
// Note the cost, honestly: for one whole version the code is uglier and two spellings
// mean the same thing. That ugliness IS the deprecation — it is what buys everyone
// else time to move.

def call(Map config = [:]) {

    // ---- 1 + 2. read and default -------------------------------------------
    // The new key wins when both are given. Never silently prefer the old one:
    // someone who wrote both is telling you which they intend.
    def appName = config.appName ?: config.name

    def image     = config.image ?: 'maven:3.9-eclipse-temurin-17'
    def skipTests = config.get('skipTests', false)

    // ---- the deprecation warning -------------------------------------------
    // Loud enough to be noticed, quiet enough not to fail anyone's build. It names
    // the step, the old key, the new key, and when the old one disappears — every
    // piece of information the reader needs to act without asking us.
    if (config.containsKey('name')) {
        echo "WARNING: buildAppP13 — the config key 'name' is deprecated, use 'appName' instead. " +
             "'name' keeps working until the next major release. See CHANGELOG.md."
    }

    // Both given and different: that is a mistake worth surfacing now rather than
    // building the wrong application quietly.
    if (config.containsKey('name') && config.containsKey('appName') && config.name != config.appName) {
        error "buildAppP13: 'name' and 'appName' were both given with different values " +
              "('${config.name}' and '${config.appName}'). Keep only 'appName'."
    }

    // ---- 3. validate --------------------------------------------------------
    if (!appName) {
        error "buildAppP13: 'appName' is required, e.g. buildAppP13(appName: 'catalog')"
    }
    if (!(appName ==~ /^[A-Za-z0-9._-]+$/)) {
        error "buildAppP13: 'appName' may only contain letters, digits, dot, dash and underscore. Got: ${appName}"
    }

    // ---- 4. act -------------------------------------------------------------
    echo "Building ${appName}  (image=${image}, skipTests=${skipTests})"

    sh "echo pretend: mvn clean package for ${appName}"

    if (skipTests) {
        echo 'Skipping tests, because skipTests=true'
    } else {
        sh "echo pretend: mvn test for ${appName}"
    }
}

def cleanup() {
    echo 'Cleaning up the workspace (pretend)'
}
