// Phase 6 — fill in a template and write the result into the workspace.
//
// This is the pattern behind every generated settings.xml, values.yaml or
// Dockerfile a real library produces:
//
//   libraryResource  →  text in memory  →  .replace(...)  →  writeFile  →  a real file
//
// libraryResource alone does NOT create a file. Reading and writing are separate
// decisions: sometimes you only want the text.

def call(Map config = [:]) {

    def appName  = config.name
    def fileName = config.file ?: 'build-info.txt'

    if (!appName) {
        error "buildInfoP6: 'name' is required, e.g. buildInfoP6(name: 'catalog')"
    }

    String text = libraryResource('com/learning/phase06/build-info.txt')

    // env.BRANCH_NAME only exists for Multibranch / SCM-backed jobs. In a plain
    // Pipeline job it is null, so give it something readable rather than printing "null".
    def branch = env.BRANCH_NAME ?: 'n/a (not a multibranch job)'

    text = text.replace('@APP_NAME@',     appName)
               .replace('@BUILD_NUMBER@', "${env.BUILD_NUMBER}")
               .replace('@BRANCH@',       branch)
               .replace('@JOB_NAME@',     "${env.JOB_NAME}")
               .replace('@TIMESTAMP@',    new Date().format('yyyy-MM-dd HH:mm:ss'))

    writeFile file: fileName, text: text

    echo "Wrote ${fileName} into the workspace"

    return fileName
}
