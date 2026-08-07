package cc.carm.plugin.intellij.quarkdown.lang.completion

import com.intellij.openapi.components.Service

/**
 * Marker service for the Quarkdown completion subsystem.
 *
 * The completion contributor, typed-handler and confidence extensions are declared
 * declaratively in `META-INF/plugin.xml`. They must NOT be registered programmatically:
 * a manually constructed [com.intellij.codeInsight.completion.CompletionContributorEP]
 * has a `null` plugin descriptor, which crashes the IDE when the extension is lazily
 * instantiated (`LazyExtensionInstance: pluginDescriptor must not be null`).
 */
@Service(Service.Level.APP)
class QuarkdownCompletionRegistrar
