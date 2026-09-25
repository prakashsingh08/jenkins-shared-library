// Phase 6 — print a banner that lives in resources/, not in the Groovy code.
//
//   resources/com/learning/phase06/banner.txt   a real file you can edit like any other
//        ↓  libraryResource  (inside the Banner class)
//   its text as a String
//        ↓  .replace('@APP_NAME@', ...)
//   echoed to the build log

import com.learning.phase06.Banner

def call(Map config = [:]) {

    def appName = config.name

    if (!appName) {
        error "bannerP6: 'name' is required, e.g. bannerP6(name: 'catalog')"
    }

    new Banner(this).show(appName)
}
