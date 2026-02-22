package com.oliveyoung.ivmlite.pkg.sinks.adapters

import com.oliveyoung.ivmlite.pkg.sinks.ports.SinkPluginRegistryPort
import com.oliveyoung.ivmlite.sinks.contract.SinkPlugin

/**
 * InMemory SinkPlugin Registry (개발/테스트용)
 */
class InMemorySinkPluginRegistry(
    private val plugins: Map<String, SinkPlugin> = emptyMap()
) : SinkPluginRegistryPort {

    private val mutablePlugins = plugins.toMutableMap()

    override fun resolve(target: String): SinkPlugin? = mutablePlugins[target]

    override fun registeredTargets(): Set<String> = mutablePlugins.keys.toSet()

    fun register(target: String, plugin: SinkPlugin) {
        mutablePlugins[target] = plugin
    }
}
