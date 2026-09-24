// Phase 4 — configuration as a Map.
//
//   buildAppP4(name: 'catalog', skipTests: true)
//       ↓  Groovy gathers the key: value pairs into ONE Map
//   buildAppP4([name: 'catalog', skipTests: true])
//       ↓  () means call()
//   call(Map config)
//
// The four beats every library step follows:
//   1. READ      pull values out of the Map
//   2. DEFAULT   fill in what the caller left out
//   3. VALIDATE  reject what is missing or unsafe   ← stop here if bad
//   4. ACT       do the work
//
// `Map config = [:]` gives the parameter its own default, so buildAppP4() with no
// arguments still reaches this method — and fails with OUR message, not Groovy's.

def call(Map config = [:]) {

    // 1 + 2 — read and default ------------------------------------------------
    def appName = config.name

    // Elvis (?:) is fine for a String: here "missing" and "empty" mean the same thing.
    def image = config.image ?: 'maven:3.9-eclipse-temurin-17'

    // NEVER use ?: for a boolean. `config.skipTests ?: true` would silently throw away
    // a caller's explicit `false`, because false is not "truthy" in Groovy.
    // get(key, default) only fills in when the key is genuinely absent.
    // (Groovy's two-argument get also writes the default back into the map. Harmless
    // here, but worth knowing before it surprises you somewhere it matters.)
    def skipTests = config.get('skipTests', false)

    // 3 — validate ------------------------------------------------------------
    // Fail fast, before any sh runs, and name the step in the message: in a pipeline
    // made of ten library calls, "name is required" does not say which one complained.
    if (!appName) {
        error "buildAppP4: 'name' is required, e.g. buildAppP4(name: 'catalog')"
    }

    // This closes the injection hole buildAppP3 leaves open on purpose.
    // An ALLOW-LIST: say what is permitted and reject everything else. Listing the
    // dangerous characters instead would mean forgetting one, eventually.
    // Rejected here: spaces and ; & | $ ` ' " — everything a shell treats as punctuation.
    if (!(appName ==~ /^[A-Za-z0-9._-]+$/)) {
        error "buildAppP4: 'name' may only contain letters, digits, dot, dash and underscore. Got: ${appName}"
    }

    // 4 — act -----------------------------------------------------------------
    echo "Building ${appName}  (image=${image}, skipTests=${skipTests})"

    // Safe to interpolate now: appName has been checked against the pattern above.
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
