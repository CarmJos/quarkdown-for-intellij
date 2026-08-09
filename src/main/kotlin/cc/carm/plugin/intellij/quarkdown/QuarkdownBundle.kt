package cc.carm.plugin.intellij.quarkdown

import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.PropertyKey

/**
 * Resource bundle accessor for Quarkdown UI messages.
 *
 * Keys live in `src/main/resources/messages/QuarkdownBundle.properties`; add
 * `QuarkdownBundle_zh.properties` (or other locale suffixes) for translations.
 */
object QuarkdownBundle : DynamicBundle("messages.QuarkdownBundle") {

    @Nls
    fun message(@PropertyKey(resourceBundle = "messages.QuarkdownBundle") key: String, vararg params: Any): String =
        getMessage(key, *params)
}