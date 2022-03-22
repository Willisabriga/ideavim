/*
 * IdeaVim - Vim emulator for IDEs based on the IntelliJ platform
 * Copyright (C) 2003-2022 The IdeaVim authors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.maddyhome.idea.vim.action.window

import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorActionManager
import com.maddyhome.idea.vim.VimPlugin
import com.maddyhome.idea.vim.api.injector
import com.maddyhome.idea.vim.command.Command
import com.maddyhome.idea.vim.command.OperatorArguments
import com.maddyhome.idea.vim.handler.VimActionHandler
import com.maddyhome.idea.vim.newapi.vim

/**
 * @author Alex Plate
 */
class LookupUpAction : VimActionHandler.SingleExecution() {

  private val keySet = parseKeysSet("<C-P>")

  override val type: Command.Type = Command.Type.OTHER_READONLY

  override fun execute(editor: Editor, context: DataContext, cmd: Command, operatorArguments: OperatorArguments): Boolean {
    val activeLookup = LookupManager.getActiveLookup(editor)
    if (activeLookup != null) {
      EditorActionManager.getInstance().getActionHandler(IdeActions.ACTION_EDITOR_MOVE_CARET_UP)
        .execute(editor, editor.caretModel.primaryCaret, context)
    } else {
      val keyStroke = keySet.first().first()
      val actions = VimPlugin.getKey().getKeymapConflicts(keyStroke)
      for (action in actions) {
        if (injector.actionExecutor.executeAction(action.vim, context.vim)) break
      }
    }
    return true
  }
}
