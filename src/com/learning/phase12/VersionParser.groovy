// PHASE 12 — pure computation, kept away from pipeline steps.
//
// Every method here is a CALCULATION: input in, value out, no echo, no sh, no waiting.
// That matters for two reasons:
//
//   1. CPS. Jenkins rewrites pipeline code so a build can be paused and saved to disk.
//      Anything alive across a step boundary must be serializable — and a
//      java.util.regex.Matcher is not. These methods create a Matcher and finish with
//      it before returning, so nothing exotic ever survives a save point.
//
//   2. Testability (Phase 11). No Jenkins in here means no fake Jenkins needed:
//      see test/phase12/VersionParserTest.groovy.
//
// WHEN YOU WOULD ADD @NonCPS
// --------------------------
// If a method like this used closures over a collection — .collect { }, .findAll { } —
// the CPS rewriting and Groovy's own iteration methods can mix badly. The fix is to
// mark the method so Jenkins runs it as plain Groovy:
//
//     import com.cloudbees.groovy.cps.NonCPS
//
//     @NonCPS
//     static String describe(List<String> names) {
//         return names.collect { it.toUpperCase() }.join(', ')
//     }
//
// That import only exists inside Jenkins, which is why this file avoids it — it would
// stop the class compiling under `mvn test`. vars/cpsDemoP12.groovy has a real, runnable
// @NonCPS example instead.
//
// The rules for @NonCPS, when you do use it:
//   * no pipeline steps inside it — no sh, no echo, no withCredentials
//   * it must finish quickly; there is no pause point inside
//   * return something serializable — a String, a number, a plain Map or List
//   * keep it small: a calculation, not a workflow

package com.learning.phase12

class VersionParser implements Serializable {

    /**
     * Pulls a ticket key such as PROJ-123 out of a branch name.
     * Returns 'none' when there is no match.
     *
     * Note the shape: the Matcher is created, used and DISCARDED inside this method.
     * Only a String leaves. That is the habit that avoids NotSerializableException.
     */
    static String ticketFrom(String branchName) {
        if (!branchName) {
            return 'none'
        }
        def matcher = (branchName =~ /([A-Z][A-Z0-9]*-\d+)/)
        return matcher ? matcher[0][1] : 'none'
    }

    /**
     * Splits a SemVer string into a plain Map. A Map of Strings and Integers is
     * serializable, so the caller can safely hold it across steps.
     */
    static Map parse(String version) {
        def cleaned = version?.trim()?.replaceFirst(/^v/, '') ?: ''
        def matcher = (cleaned =~ /^(\d+)\.(\d+)\.(\d+)$/)

        if (!matcher) {
            return [valid: false, major: 0, minor: 0, patch: 0, text: version]
        }

        return [
            valid: true,
            major: matcher[0][1] as Integer,
            minor: matcher[0][2] as Integer,
            patch: matcher[0][3] as Integer,
            text : cleaned
        ]
    }

    /**
     * Which part of the version changed — the vocabulary from Phase 8 and 13.
     * 'major' means a caller was broken, whatever the size of the diff.
     */
    static String bumpKind(String from, String to) {
        def a = parse(from)
        def b = parse(to)

        if (!a.valid || !b.valid) return 'unknown'
        if (a.major != b.major)   return 'major'
        if (a.minor != b.minor)   return 'minor'
        if (a.patch != b.patch)   return 'patch'
        return 'none'
    }
}
