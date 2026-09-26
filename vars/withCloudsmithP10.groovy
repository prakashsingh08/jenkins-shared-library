// Phase 10 — a wrapper step: your first step that takes a BLOCK of code.
//
//   withCloudsmithP10('cloudsmith-creds') {
//       sh 'mvn -B deploy -s settings.xml'
//   }
//
// A closure is just code stored as a value:
//
//   def block = { echo 'hi' }    // nothing runs yet
//   block()                      // now it runs
//
// Groovy's convenience: if the LAST argument is a closure, it can sit outside the
// parentheses. So these two are the same call:
//
//   withCloudsmithP10('id', { sh 'mvn deploy' })
//   withCloudsmithP10('id') { sh 'mvn deploy' }
//
// Every wrapper step you have ever used — withCredentials, dir, timeout, node —
// is exactly this shape. None of them are special syntax.

// Two explicit signatures rather than a default argument, so the overload is obvious:
//   withCloudsmithP10 { ... }              → uses the default credential id
//   withCloudsmithP10('other-creds') { }   → uses the one you name
def call(Closure body) {
    call('cloudsmith-creds', body)
}

def call(String credentialsId, Closure body) {

    if (!credentialsId) {
        error "withCloudsmithP10: a credentials ID is required"
    }

    withCredentials([usernamePassword(
            credentialsId: credentialsId,
            usernameVariable: 'CS_USER',
            passwordVariable: 'CS_PASS')]) {

        // CS_USER and CS_PASS exist ONLY between these braces. On the way out,
        // Jenkins unbinds them again.
        //
        // Inside the caller's block, use them the SHELL way:
        //     sh 'mvn deploy -Duser=$CS_USER'     ✓ the shell expands it
        //     sh "mvn deploy -Duser=${CS_USER}"   ✗ Groovy expands it INTO THE LOG
        body()
    }
}
