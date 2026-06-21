package cc.carm.plugin.intellij.quarkdown

import com.intellij.lang.Language

class QuarkdownLanguage private constructor() : Language("Quarkdown") {

    companion object {
        @JvmField
        val INSTANCE = QuarkdownLanguage()
    }
}
