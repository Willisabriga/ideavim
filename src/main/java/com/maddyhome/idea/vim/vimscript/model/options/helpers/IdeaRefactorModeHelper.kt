/*
 * Copyright 2003-2023 The IdeaVim authors
 *
 * Use of this source code is governed by an MIT-style
 * license that can be found in the LICENSE.txt file or at
 * https://opensource.org/licenses/MIT.
 */

package com.maddyhome.idea.vim.vimscript.model.options.helpers

import com.intellij.codeInsight.lookup.LookupEvent
import com.intellij.codeInsight.lookup.LookupListener
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.openapi.editor.Editor
import com.maddyhome.idea.vim.VimPlugin
import com.maddyhome.idea.vim.api.VimEditor
import com.maddyhome.idea.vim.api.injector
import com.maddyhome.idea.vim.group.IjOptionConstants
import com.maddyhome.idea.vim.helper.hasBlockOrUnderscoreCaret
import com.maddyhome.idea.vim.helper.hasVisualSelection
import com.maddyhome.idea.vim.helper.vimStateMachine
import com.maddyhome.idea.vim.listener.SelectionVimListenerSuppressor
import com.maddyhome.idea.vim.newapi.ijOptions
import com.maddyhome.idea.vim.newapi.vim
import com.maddyhome.idea.vim.state.mode.Mode
import com.maddyhome.idea.vim.state.mode.mode
import com.maddyhome.idea.vim.state.mode.selectionType

public val VimEditor.isIdeaRefactorModeKeep: Boolean
  get() = injector.ijOptions(this).idearefactormode.contains(IjOptionConstants.idearefactormode_keep)

public val VimEditor.isIdeaRefactorModeSelect: Boolean
  get() = injector.ijOptions(this).idearefactormode.contains(IjOptionConstants.idearefactormode_select)

internal object IdeaRefactorModeHelper {

  fun correctSelection(editor: Editor) {
    val action: () -> Unit = {
      val mode = editor.vim.mode
      if (!mode.hasVisualSelection && editor.selectionModel.hasSelection()) {
        SelectionVimListenerSuppressor.lock().use {
          editor.selectionModel.removeSelection()
        }
      }
      if (mode.hasVisualSelection && editor.selectionModel.hasSelection()) {
        val autodetectedSubmode = VimPlugin.getVisualMotion().autodetectVisualSubmode(editor.vim)
        if (mode.selectionType != autodetectedSubmode) {
          // Update the submode
          val newMode = when (mode) {
            is Mode.SELECT -> mode.copy(selectionType = autodetectedSubmode)
            is Mode.VISUAL -> mode.copy(selectionType = autodetectedSubmode)
            else -> error("IdeaVim should be either in visual or select modes")
          }
          editor.vim.vimStateMachine.mode = newMode
        }
      }

      if (editor.hasBlockOrUnderscoreCaret()) {
        TemplateManagerImpl.getTemplateState(editor)?.currentVariableRange?.let { segmentRange ->
          if (!segmentRange.isEmpty && segmentRange.endOffset == editor.caretModel.offset && editor.caretModel.offset != 0) {
            editor.caretModel.moveToOffset(editor.caretModel.offset - 1)
          }
        }
      }
    }

    val lookup = LookupManager.getActiveLookup(editor) as? LookupImpl
    if (lookup != null) {
      val selStart = editor.selectionModel.selectionStart
      val selEnd = editor.selectionModel.selectionEnd
      lookup.performGuardedChange(action)
      lookup.addLookupListener(object : LookupListener {
        override fun beforeItemSelected(event: LookupEvent): Boolean {
          // FIXME: 01.11.2019 Nasty workaround because of problems in IJ platform
          //   Lookup replaces selected text and not the template itself. So, if there is no selection
          //   in the template, lookup value will not replace the template, but just insert value on the caret position
          lookup.performGuardedChange { editor.selectionModel.setSelection(selStart, selEnd) }
          lookup.removeLookupListener(this)
          return true
        }
      })
    } else {
      action()
    }
  }
}
