/*
 * Copyright 2003-2023 The IdeaVim authors
 *
 * Use of this source code is governed by an MIT-style
 * license that can be found in the LICENSE.txt file or at
 * https://opensource.org/licenses/MIT.
 */
package org.jetbrains.plugins.ideavim.action.motion.gn

import com.intellij.idea.TestFor
import com.maddyhome.idea.vim.VimPlugin
import com.maddyhome.idea.vim.action.motion.search.SearchWholeWordForwardAction
import com.maddyhome.idea.vim.api.injector
import com.maddyhome.idea.vim.command.VimStateMachine
import com.maddyhome.idea.vim.common.Direction
import org.jetbrains.plugins.ideavim.SkipNeovimReason
import org.jetbrains.plugins.ideavim.TestWithoutNeovim
import org.jetbrains.plugins.ideavim.VimTestCase

@Suppress("SpellCheckingInspection")
class VisualSelectNextSearchTest : VimTestCase() {
  @TestFor(classes = [SearchWholeWordForwardAction::class])
  fun testSearch() {
    typeTextInFile(injector.parser.parseKeys("*" + "b" + "gn"), "h<caret>ello world\nhello world hello world")
    assertOffset(16)
    assertSelection("hello")
    assertMode(VimStateMachine.Mode.VISUAL)
  }

  @TestFor(classes = [SearchWholeWordForwardAction::class])
  fun testSearchMulticaret() {
    typeTextInFile(
      injector.parser.parseKeys("*" + "b" + "gn"),
      "h<caret>ello world\nh<caret>ello world hello world"
    )
    assertEquals(1, myFixture.editor.caretModel.caretCount)
    assertMode(VimStateMachine.Mode.VISUAL)
  }

  @TestFor(classes = [SearchWholeWordForwardAction::class])
  fun testSearchFordAndBack() {
    typeTextInFile(
      injector.parser.parseKeys("*" + "2b" + "gn" + "gN"),
      "h<caret>ello world\nhello world hello world"
    )
    assertOffset(0)
    assertSelection("h")
    assertMode(VimStateMachine.Mode.VISUAL)
  }

  @TestWithoutNeovim(reason = SkipNeovimReason.UNCLEAR)
  fun testWithoutSpaces() {
    configureByText("test<caret>test")
    VimPlugin.getSearch().setLastSearchState(myFixture.editor, "test", "", Direction.FORWARDS)
    typeText(injector.parser.parseKeys("gn"))
    assertOffset(7)
    assertSelection("test")
    assertMode(VimStateMachine.Mode.VISUAL)
  }

  @TestFor(classes = [SearchWholeWordForwardAction::class])
  fun testSearchCurrentlyInOne() {
    typeTextInFile(injector.parser.parseKeys("*" + "gn"), "h<caret>ello world\nhello world hello world")
    assertOffset(16)
    assertSelection("hello")
    assertMode(VimStateMachine.Mode.VISUAL)
  }

  @TestFor(classes = [SearchWholeWordForwardAction::class])
  fun testSearchTwice() {
    typeTextInFile(injector.parser.parseKeys("*" + "2gn"), "h<caret>ello world\nhello world hello, hello")
    assertOffset(28)
    assertSelection("hello")
  }

  @TestFor(classes = [SearchWholeWordForwardAction::class])
  fun testSearchTwiceInVisual() {
    typeTextInFile(
      injector.parser.parseKeys("*" + "gn" + "2gn"),
      "h<caret>ello world\nhello world hello, hello hello"
    )
    assertOffset(35)
    assertSelection("hello world hello, hello")
  }

  @TestFor(classes = [SearchWholeWordForwardAction::class])
  fun testTwoSearchesStayInVisualMode() {
    typeTextInFile(injector.parser.parseKeys("*" + "gn" + "gn"), "h<caret>ello world\nhello world hello, hello")
    assertOffset(28)
    assertSelection("hello world hello")
    assertMode(VimStateMachine.Mode.VISUAL)
  }

  @TestFor(classes = [SearchWholeWordForwardAction::class])
  fun testCanExitVisualMode() {
    typeTextInFile(
      injector.parser.parseKeys("*" + "gn" + "gn" + "<Esc>"),
      "h<caret>ello world\nhello world hello, hello"
    )
    assertOffset(28)
    assertSelection(null)
    assertMode(VimStateMachine.Mode.COMMAND)
  }

  fun testNullSelectionDoesNothing() {
    typeTextInFile(injector.parser.parseKeys("/bye<CR>" + "gn"), "h<caret>ello world\nhello world hello world")
    assertOffset(1)
    assertSelection(null)
  }

  @TestFor(classes = [SearchWholeWordForwardAction::class])
  fun testIfInLastPositionOfSearchAndInNormalModeThenSelectCurrent() {
    typeTextInFile(injector.parser.parseKeys("*0e" + "gn"), "h<caret>ello hello")
    assertOffset(4)
    assertSelection("hello")
    assertMode(VimStateMachine.Mode.VISUAL)
  }

  @TestFor(classes = [SearchWholeWordForwardAction::class])
  fun testIfInMiddlePositionOfSearchAndInVisualModeThenSelectCurrent() {
    typeTextInFile(injector.parser.parseKeys("*0llv" + "gn"), "h<caret>ello hello")
    assertOffset(4)
    assertSelection("llo")
    assertMode(VimStateMachine.Mode.VISUAL)
  }

  @TestFor(classes = [SearchWholeWordForwardAction::class])
  fun testIfInLastPositionOfSearchAndInVisualModeThenSelectNext() {
    typeTextInFile(injector.parser.parseKeys("*0ev" + "gn"), "h<caret>ello hello")
    assertOffset(10)
    assertSelection("o hello")
    assertMode(VimStateMachine.Mode.VISUAL)
  }

  @TestFor(classes = [SearchWholeWordForwardAction::class])
  fun testMixWithN() {
    typeTextInFile(
      injector.parser.parseKeys("*" + "gn" + "n" + "gn"),
      "h<caret>ello world\nhello world hello, hello"
    )
    assertOffset(28)
    assertSelection("hello world hello")
    assertMode(VimStateMachine.Mode.VISUAL)
  }

  @TestFor(classes = [SearchWholeWordForwardAction::class])
  fun testMixWithPreviousSearch() {
    typeTextInFile(
      injector.parser.parseKeys("*" + "gn" + "gn" + "gN" + "gn"),
      "h<caret>ello world\nhello world hello, hello"
    )
    assertOffset(28)
    assertSelection("hello world hello")
    assertMode(VimStateMachine.Mode.VISUAL)
  }

  @TestFor(classes = [SearchWholeWordForwardAction::class])
  fun testSearchWithTabs() {
    typeTextInFile(injector.parser.parseKeys("*" + "gn"), "\tf<caret>oo")
    assertSelection("foo")
  }
}
