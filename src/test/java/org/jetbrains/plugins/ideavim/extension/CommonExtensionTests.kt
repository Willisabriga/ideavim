/*
 * Copyright 2003-2023 The IdeaVim authors
 *
 * Use of this source code is governed by an MIT-style
 * license that can be found in the LICENSE.txt file or at
 * https://opensource.org/licenses/MIT.
 */

package org.jetbrains.plugins.ideavim.extension

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.invokeLater
import com.intellij.testFramework.PlatformTestUtil
import com.maddyhome.idea.vim.VimPlugin
import com.maddyhome.idea.vim.api.ExecutionContext
import com.maddyhome.idea.vim.api.VimEditor
import com.maddyhome.idea.vim.api.injector
import com.maddyhome.idea.vim.api.moveToMotion
import com.maddyhome.idea.vim.command.MappingMode
import com.maddyhome.idea.vim.command.OperatorArguments
import com.maddyhome.idea.vim.extension.Alias
import com.maddyhome.idea.vim.extension.ExtensionBeanClass
import com.maddyhome.idea.vim.extension.ExtensionHandler
import com.maddyhome.idea.vim.extension.VimExtension
import com.maddyhome.idea.vim.extension.VimExtensionFacade.putExtensionHandlerMapping
import com.maddyhome.idea.vim.extension.VimExtensionFacade.putKeyMapping
import com.maddyhome.idea.vim.extension.VimExtensionFacade.putKeyMappingIfMissing
import com.maddyhome.idea.vim.handler.Motion
import com.maddyhome.idea.vim.helper.isEndAllowed
import com.maddyhome.idea.vim.newapi.ij
import com.maddyhome.idea.vim.newapi.vim
import com.maddyhome.idea.vim.options.OptionAccessScope
import com.maddyhome.idea.vim.state.mode.Mode
import com.maddyhome.idea.vim.state.mode.SelectionType
import org.jetbrains.plugins.ideavim.VimTestCase
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OpMappingTest : VimTestCase() {
  private var initialized = false

  private lateinit var extension: ExtensionBeanClass

  @BeforeEach
  override fun setUp(testInfo: TestInfo) {
    super.setUp(testInfo)
    if (!initialized) {
      initialized = true

      extension = TestExtension.createBean()

      VimExtension.EP_NAME.point.registerExtension(extension, VimPlugin.getInstance())
      enableExtensions("TestExtension")
    }
  }

  @AfterEach
  override fun tearDown(testInfo: TestInfo) {
    @Suppress("DEPRECATION")
    VimExtension.EP_NAME.point.unregisterExtension(extension)
    super.tearDown(super.testInfo)
  }

  @Test
  fun `test simple delete`() {
    doTest(
      "dI",
      "${c}I found it in a legendary land",
      "${c}nd it in a legendary land",
      Mode.NORMAL(),
    )
  }

  @Test
  fun `test simple delete backwards`() {
    doTest(
      "dP",
      "I found ${c}it in a legendary land",
      "I f${c}it in a legendary land",
      Mode.NORMAL(),
    )
  }

  @Test
  fun `test delete emulate inclusive`() {
    doTest(
      "dU",
      "${c}I found it in a legendary land",
      "${c}d it in a legendary land",
      Mode.NORMAL(),
    )
  }

  @Test
  fun `test linewise delete`() {
    doTest(
      "dO",
      """
                Lorem Ipsum

                I ${c}found it in a legendary land
                consectetur adipiscing elit
                Sed in orci mauris.
                Cras id tellus in ex imperdiet egestas.
      """.trimIndent(),
      """
                Lorem Ipsum

                ${c}Sed in orci mauris.
                Cras id tellus in ex imperdiet egestas.
      """.trimIndent(),
      Mode.NORMAL(),
    )
  }

  @Test
  fun `test disable extension via set`() {
    configureByText("${c}I found it in a legendary land")
    typeText(injector.parser.parseKeys("Q"))
    assertState("I$c found it in a legendary land")

    enterCommand("set noTestExtension")
    typeText(injector.parser.parseKeys("Q"))
    assertState("I$c found it in a legendary land")

    enterCommand("set TestExtension")
    typeText(injector.parser.parseKeys("Q"))
    assertState("I ${c}found it in a legendary land")
  }

  @Test
  fun `test disable extension as extension point`() {
    configureByText("${c}I found it in a legendary land")
    typeText(injector.parser.parseKeys("Q"))
    assertState("I$c found it in a legendary land")

    @Suppress("DEPRECATION")
    VimExtension.EP_NAME.point.unregisterExtension(extension)
    assertEmpty(VimPlugin.getKey().getKeyMappingByOwner(extension.instance.owner))
    typeText(injector.parser.parseKeys("Q"))
    assertState("I$c found it in a legendary land")

    VimExtension.EP_NAME.point.registerExtension(extension, VimPlugin.getInstance())
    assertEmpty(VimPlugin.getKey().getKeyMappingByOwner(extension.instance.owner))
    enableExtensions("TestExtension")
    typeText(injector.parser.parseKeys("Q"))
    assertState("I ${c}found it in a legendary land")
  }

  @Test
  fun `test disable disposed extension`() {
    configureByText("${c}I found it in a legendary land")
    typeText(injector.parser.parseKeys("Q"))
    assertState("I$c found it in a legendary land")

    enterCommand("set noTestExtension")
    @Suppress("DEPRECATION")
    VimExtension.EP_NAME.point.unregisterExtension(extension)
    typeText(injector.parser.parseKeys("Q"))
    assertState("I$c found it in a legendary land")

    VimExtension.EP_NAME.point.registerExtension(extension, VimPlugin.getInstance())
    enableExtensions("TestExtension")
    typeText(injector.parser.parseKeys("Q"))
    assertState("I ${c}found it in a legendary land")
  }

  @Test
  fun `test delayed action`() {
    configureByText("${c}I found it in a legendary land")
    typeText(injector.parser.parseKeys("R"))
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
    assertState("I fou${c}nd it in a legendary land")

    typeText(injector.parser.parseKeys("dR"))
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
    assertState("I fou$c in a legendary land")
  }

  /**
   * This test tests an intentionally incorrectly implemented action
   */
  @Test
  fun `test delayed incorrect action`() {
    configureByText("${c}I found it in a legendary land")
    typeText(injector.parser.parseKeys("E"))
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
    assertState("I fou${c}nd it in a legendary land")

    typeText(injector.parser.parseKeys("dE"))
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
    assertState("I found it$c in a legendary land")
  }
}

class PlugExtensionsTest : VimTestCase() {

  private lateinit var extension: ExtensionBeanClass

  @BeforeEach
  override fun setUp(testInfo: TestInfo) {
    super.setUp(testInfo)
    configureByText("\n")

    extension = TestExtension.createBean()
    VimExtension.EP_NAME.point.registerExtension(extension, VimPlugin.getInstance())
  }

  @AfterEach
  override fun tearDown(testInfo: TestInfo) {
    @Suppress("DEPRECATION")
    VimExtension.EP_NAME.point.unregisterExtension(extension)
    super.tearDown(super.testInfo)
  }

  @Test
  fun `test enable via plug`() {
    executeVimscript("Plug 'MyTest'", false)

    assertTrue(extension.ext.initialized)
  }

  @Test
  fun `test enable via plugin`() {
    executeVimscript("Plugin 'MyTest'", false)

    assertTrue(extension.ext.initialized)
  }

  @Test
  fun `test enable via plug and disable via set`() {
    executeVimscript("Plug 'MyTest'")
    executeVimscript("set noTestExtension")
    assertTrue(extension.ext.initialized)
    assertTrue(extension.ext.disposed)
  }
}

class PlugMissingKeysTest : VimTestCase() {

  private lateinit var extension: ExtensionBeanClass

  @BeforeEach
  override fun setUp(testInfo: TestInfo) {
    super.setUp(testInfo)
    configureByText("\n")

    extension = TestExtension.createBean()
    VimExtension.EP_NAME.point.registerExtension(extension, VimPlugin.getInstance())
  }

  @AfterEach
  override fun tearDown(testInfo: TestInfo) {
    @Suppress("DEPRECATION")
    VimExtension.EP_NAME.point.unregisterExtension(extension)
    super.tearDown(super.testInfo)
  }

  @Test
  fun `test missing keys`() {
    executeLikeVimrc(
      "map myKey <Plug>TestMissing",
      "Plug 'MyTest'",
    )

    // Mapping to Z was override by the mapping to myKey
    val keyMappings = VimPlugin.getKey().getMapTo(MappingMode.NORMAL, injector.parser.parseKeys("<Plug>TestMissing"))
    kotlin.test.assertEquals(1, keyMappings.size)
    kotlin.test.assertEquals(injector.parser.parseKeys("myKey"), keyMappings.first().first)

    val iKeyMappings = VimPlugin.getKey().getMapTo(MappingMode.INSERT, injector.parser.parseKeys("<Plug>TestMissing"))
    kotlin.test.assertEquals(1, iKeyMappings.size)
    kotlin.test.assertEquals(injector.parser.parseKeys("L"), iKeyMappings.first().first)
  }

  @Test
  fun `test missing keys enable plugin first`() {
    executeLikeVimrc(
      "Plug 'MyTest'",
      "map myKey <Plug>TestMissing",
    )

    // Mapping to Z was override by the mapping to myKey
    val keyMappings = VimPlugin.getKey().getMapTo(MappingMode.NORMAL, injector.parser.parseKeys("<Plug>TestMissing"))
    kotlin.test.assertEquals(1, keyMappings.size)
    kotlin.test.assertEquals(injector.parser.parseKeys("myKey"), keyMappings.first().first)

    val iKeyMappings = VimPlugin.getKey().getMapTo(MappingMode.INSERT, injector.parser.parseKeys("<Plug>TestMissing"))
    kotlin.test.assertEquals(1, iKeyMappings.size)
    kotlin.test.assertEquals(injector.parser.parseKeys("L"), iKeyMappings.first().first)
  }

  @Test
  fun `test packadd`() {
    assertOptionUnset("matchit")
    executeLikeVimrc("packadd matchit")
    assertOptionSet("matchit")
  }

  @Test
  fun `test packadd ex`() {
    assertOptionUnset("matchit")
    executeLikeVimrc("packadd! matchit")
    assertOptionSet("matchit")
  }

  private fun assertOptionSet(name: String) {
    val option = injector.optionGroup.getOption(name)!!
    assertTrue(injector.optionGroup.getOptionValue(option, OptionAccessScope.GLOBAL(fixture.editor.vim)).asBoolean())
  }

  private fun assertOptionUnset(name: String) {
    val option = injector.optionGroup.getOption(name)!!
    assertFalse(injector.optionGroup.getOptionValue(option, OptionAccessScope.GLOBAL(fixture.editor.vim)).asBoolean())
  }

  private fun executeLikeVimrc(vararg text: String) {
    injector.vimscriptExecutor.executingVimscript = true
    injector.vimscriptExecutor.executingIdeaVimRcConfiguration = true
    executeVimscript(text.joinToString("\n"), false)
    injector.vimscriptExecutor.executingIdeaVimRcConfiguration = false
    injector.vimscriptExecutor.executingVimscript = false
  }
}

private val ExtensionBeanClass.ext: TestExtension
  get() = this.instance as TestExtension

private class TestExtension : VimExtension {

  var initialized = false
  var disposed = false

  override fun getName(): String = "TestExtension"

  override fun init() {
    initialized = true
    putExtensionHandlerMapping(
      MappingMode.O,
      injector.parser.parseKeys("<Plug>TestExtensionEmulateInclusive"),
      owner,
      MoveEmulateInclusive(),
      false,
    )
    putExtensionHandlerMapping(
      MappingMode.O,
      injector.parser.parseKeys("<Plug>TestExtensionBackwardsCharacter"),
      owner,
      MoveBackwards(),
      false,
    )
    putExtensionHandlerMapping(MappingMode.O, injector.parser.parseKeys("<Plug>TestExtensionCharacter"), owner, Move(), false)
    putExtensionHandlerMapping(MappingMode.O, injector.parser.parseKeys("<Plug>TestExtensionLinewise"), owner, MoveLinewise(), false)
    putExtensionHandlerMapping(MappingMode.N, injector.parser.parseKeys("<Plug>TestMotion"), owner, MoveLinewiseInNormal(), false)
    putExtensionHandlerMapping(MappingMode.N, injector.parser.parseKeys("<Plug>TestMissing"), owner, MoveLinewiseInNormal(), false)
    putExtensionHandlerMapping(MappingMode.NO, injector.parser.parseKeys("<Plug>TestDelayed"), owner, DelayedAction(), false)
    putExtensionHandlerMapping(MappingMode.NO, injector.parser.parseKeys("<Plug>TestIncorrectDelayed"), owner, DelayedIncorrectAction(), false)

    putKeyMapping(MappingMode.O, injector.parser.parseKeys("U"), owner, injector.parser.parseKeys("<Plug>TestExtensionEmulateInclusive"), true)
    putKeyMapping(MappingMode.O, injector.parser.parseKeys("P"), owner, injector.parser.parseKeys("<Plug>TestExtensionBackwardsCharacter"), true)
    putKeyMapping(MappingMode.O, injector.parser.parseKeys("I"), owner, injector.parser.parseKeys("<Plug>TestExtensionCharacter"), true)
    putKeyMapping(MappingMode.O, injector.parser.parseKeys("O"), owner, injector.parser.parseKeys("<Plug>TestExtensionLinewise"), true)
    putKeyMapping(MappingMode.N, injector.parser.parseKeys("Q"), owner, injector.parser.parseKeys("<Plug>TestMotion"), true)
    putKeyMapping(MappingMode.NO, injector.parser.parseKeys("R"), owner, injector.parser.parseKeys("<Plug>TestDelayed"), true)
    putKeyMapping(MappingMode.NO, injector.parser.parseKeys("E"), owner, injector.parser.parseKeys("<Plug>TestIncorrectDelayed"), true)

    putKeyMappingIfMissing(MappingMode.N, injector.parser.parseKeys("Z"), owner, injector.parser.parseKeys("<Plug>TestMissing"), true)
    putKeyMappingIfMissing(MappingMode.I, injector.parser.parseKeys("L"), owner, injector.parser.parseKeys("<Plug>TestMissing"), true)
  }

  override fun dispose() {
    disposed = true
    super.dispose()
  }

  private class MoveEmulateInclusive : ExtensionHandler {
    override fun execute(editor: VimEditor, context: ExecutionContext, operatorArguments: OperatorArguments) {
      VimPlugin.getVisualMotion().enterVisualMode(editor, SelectionType.CHARACTER_WISE)
      val caret = editor.ij.caretModel.currentCaret
      val newOffset = VimPlugin.getMotion().getHorizontalMotion(editor, caret.vim, 5, editor.isEndAllowed)
      caret.vim.moveToMotion(newOffset)
    }
  }

  private class MoveBackwards : ExtensionHandler {
    override fun execute(editor: VimEditor, context: ExecutionContext, operatorArguments: OperatorArguments) {
      editor.ij.caretModel.allCarets.forEach { it.moveToOffset(it.offset - 5) }
    }
  }

  private class Move : ExtensionHandler {
    override fun execute(editor: VimEditor, context: ExecutionContext, operatorArguments: OperatorArguments) {
      editor.ij.caretModel.allCarets.forEach { it.moveToOffset(it.offset + 5) }
    }
  }

  private class MoveLinewise : ExtensionHandler {
    override fun execute(editor: VimEditor, context: ExecutionContext, operatorArguments: OperatorArguments) {
      VimPlugin.getVisualMotion().enterVisualMode(editor, SelectionType.LINE_WISE)
      val caret = editor.ij.caretModel.currentCaret
      val newOffset = VimPlugin.getMotion().getVerticalMotionOffset(editor, caret.vim, 1)
      caret.vim.moveToOffset((newOffset as Motion.AbsoluteOffset).offset)
    }
  }

  private class MoveLinewiseInNormal : ExtensionHandler {
    override fun execute(editor: VimEditor, context: ExecutionContext, operatorArguments: OperatorArguments) {
      val caret = editor.ij.caretModel.currentCaret
      val newOffset = VimPlugin.getMotion().getHorizontalMotion(editor, caret.vim, 1, true)
      caret.vim.moveToMotion(newOffset)
    }
  }

  private class DelayedAction : ExtensionHandler.WithCallback() {
    override fun execute(editor: VimEditor, context: ExecutionContext, operatorArguments: OperatorArguments) {
      invokeLater {
        invokeLater {
          editor.ij.caretModel.allCarets.forEach { it.moveToOffset(it.offset + 5) }
          continueVimExecution()
        }
      }
    }
  }

  // This action should be registered with WithCallback, but we intentionally made it incorrectly for tests
  private class DelayedIncorrectAction : ExtensionHandler {
    override fun execute(editor: VimEditor, context: ExecutionContext, operatorArguments: OperatorArguments) {
      invokeLater {
        invokeLater {
          editor.ij.caretModel.allCarets.forEach { it.moveToOffset(it.offset + 5) }
        }
      }
    }
  }

  companion object {
    fun createBean(): ExtensionBeanClass {
      val beanClass = ExtensionBeanClass()
      beanClass.implementation = TestExtension::class.java.canonicalName
      beanClass.aliases = listOf(Alias().also { it.name = "MyTest" })
      beanClass.pluginDescriptor = PluginManagerCore.getPlugin(VimPlugin.getPluginId())!!
      return beanClass
    }
  }
}
