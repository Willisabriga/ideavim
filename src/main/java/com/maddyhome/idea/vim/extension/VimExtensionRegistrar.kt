/*
 * Copyright 2003-2023 The IdeaVim authors
 *
 * Use of this source code is governed by an MIT-style
 * license that can be found in the LICENSE.txt file or at
 * https://opensource.org/licenses/MIT.
 */
package com.maddyhome.idea.vim.extension

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointListener
import com.intellij.openapi.extensions.PluginDescriptor
import com.maddyhome.idea.vim.VimPlugin
import com.maddyhome.idea.vim.api.VimExtensionRegistrator
import com.maddyhome.idea.vim.api.injector
import com.maddyhome.idea.vim.api.setToggleOption
import com.maddyhome.idea.vim.key.MappingOwner.Plugin.Companion.remove
import com.maddyhome.idea.vim.options.OptionAccessScope
import com.maddyhome.idea.vim.options.OptionDeclaredScope
import com.maddyhome.idea.vim.options.ToggleOption
import com.maddyhome.idea.vim.statistic.PluginState

internal object VimExtensionRegistrar : VimExtensionRegistrator {
  internal val registeredExtensions: MutableSet<String> = HashSet()
  internal val extensionAliases = HashMap<String, String>()
  private var extensionRegistered = false
  private val logger = logger<VimExtensionRegistrar>()

  private val delayedExtensionEnabling = mutableListOf<ExtensionBeanClass>()

  @JvmStatic
  fun registerExtensions() {
    if (extensionRegistered) return
    extensionRegistered = true

    VimExtension.EP_NAME.extensions.forEach(this::registerExtension)

    VimExtension.EP_NAME.point.addExtensionPointListener(
      object : ExtensionPointListener<ExtensionBeanClass> {
        override fun extensionAdded(extension: ExtensionBeanClass, pluginDescriptor: PluginDescriptor) {
          registerExtension(extension)
        }

        override fun extensionRemoved(extension: ExtensionBeanClass, pluginDescriptor: PluginDescriptor) {
          unregisterExtension(extension)
        }
      },
      false,
      VimPlugin.getInstance(),
    )
  }

  @Synchronized
  private fun registerExtension(extensionBean: ExtensionBeanClass) {
    val name = extensionBean.name ?: extensionBean.instance.name
    if (name in registeredExtensions) return

    registeredExtensions.add(name)
    registerAliases(extensionBean)
    val option = ToggleOption(name, OptionDeclaredScope.GLOBAL, getAbbrev(name), false)
    VimPlugin.getOptionGroup().addOption(option)
    VimPlugin.getOptionGroup().addGlobalOptionChangeListener(option) {
      if (injector.optionGroup.getOptionValue(option, OptionAccessScope.GLOBAL(null)).asBoolean()) {
        initExtension(extensionBean, name)
        PluginState.enabledExtensions.add(name)
      } else {
        extensionBean.instance.dispose()
      }
    }
  }

  private fun getAbbrev(name: String): String {
    return if (name == "NERDTree") "nerdtree" else name
  }

  private fun initExtension(extensionBean: ExtensionBeanClass, name: String) {
    if (injector.vimscriptExecutor.executingVimscript) {
      delayedExtensionEnabling += extensionBean
    } else {
      extensionBean.instance.init()
      logger.info("IdeaVim extension '$name' initialized")
    }
  }

  /**
   * During vim initialization process, it firstly loads the .vimrc file, then executes scripts from the plugins folder.
   * This practically means that the .vimrc file is initialized first, then the plugins are loaded.
   * See `:h initialization`
   *
   * In IdeaVim we don't have a separate plugins folder to load it after .ideavimrc load. However, we can collect
   *   the list of plugins mentioned in the .ideavimrc and load them after .ideavimrc execution is finished.
   *
   * Why this matters? Because this affects the order of commands are executed. For example:
   * ```
   * plug 'tommcdo/vim-exchange'
   * let g:exchange_no_mappings=1
   * ```
   * Here the user will expect that the exchange plugin won't have default mappings. However, if we load vim-exchange
   *    immediately, this variable won't be initialized at the moment of plugin initialization.
   *
   * There is also a tricky case for mappings override:
   * ```
   * plug 'tommcdo/vim-exchange'
   * map X <Plug>(ExchangeLine)
   * ```
   * For this case, a plugin with a good implementation detects that there is already a defined mapping for
   *   `<Plug>(ExchangeLine)` and doesn't register the default cxx mapping. However, such detection requires the mapping
   *   to be defined before the plugin initialization.
   */
  @JvmStatic
  fun enableDelayedExtensions() {
    delayedExtensionEnabling.forEach {
      it.instance.init()
      logger.info("IdeaVim extension '${it.name}' initialized")
    }
    delayedExtensionEnabling.clear()
  }

  @Synchronized
  private fun unregisterExtension(extension: ExtensionBeanClass) {
    val name = extension.name ?: extension.instance.name
    if (name !in registeredExtensions) return
    registeredExtensions.remove(name)
    removeAliases(extension)
    extension.instance.dispose()
    VimPlugin.getOptionGroup().removeOption(name)
    remove(name)
    logger.info("IdeaVim extension '$name' disposed")
  }

  override fun setOptionByPluginAlias(alias: String): Boolean {
    val name = extensionAliases[alias] ?: return false
    val option = injector.optionGroup.getOption(name) as? ToggleOption ?: return false
    injector.optionGroup.setToggleOption(option, OptionAccessScope.GLOBAL(null))
    return true
  }

  override fun getExtensionNameByAlias(alias: String): String? {
    return extensionAliases[alias]
  }

  private fun registerAliases(extension: ExtensionBeanClass) {
    extension.aliases
      ?.mapNotNull { it.name }
      ?.forEach { alias -> extensionAliases[alias] = extension.name ?: extension.instance.name }
  }

  private fun removeAliases(extension: ExtensionBeanClass) {
    extension.aliases?.mapNotNull { it.name }?.forEach { extensionAliases.remove(it) }
  }
}
