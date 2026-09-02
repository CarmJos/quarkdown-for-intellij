package cc.carm.plugin.intellij.quarkdown.ui.preview

import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser

/**
 * JCEF probe that never leaks a linkage error to its caller.
 *
 * Since 2026.2 the embedded browser is no longer part of the platform core: it lives in the
 * bundled "Web Browser (JCEF)" plugin (`com.intellij.modules.jcef`), which is only visible to a
 * plugin that declares a dependency on it. When that dependency is not resolved, the very first
 * attempt to *resolve* [JBCefApp] throws `NoClassDefFoundError`, so a plain
 * `JBCefApp.isSupported()` call cannot protect its caller - the check itself fails.
 * Keeping every JCEF reference inside this small class (and swallowing [Throwable]) confines the
 * failure, so callers can fall back to a browser-less UI instead of breaking the plugin.
 */
internal object JcefSupport {

    /** @return `true` when the JCEF API is on the classpath *and* usable in the running IDE. */
    fun isAvailable(): Boolean = try {
        JBCefApp.isSupported()
    } catch (t: Throwable) {
        // NoClassDefFoundError: the "Web Browser (JCEF)" module is not available to us.
        false
    }

    /** @return a freshly created browser, or `null` when JCEF is unavailable. */
    fun createBrowser(): JBCefBrowser? = try {
        if (isAvailable()) JBCefBrowser() else null
    } catch (t: Throwable) {
        null
    }
}
