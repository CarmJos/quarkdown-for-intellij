package cc.carm.plugin.intellij.quarkdown.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.NotNull;

public class InsertLinkAction extends AnAction implements DumbAware {

    @Override
    public void update(@NotNull AnActionEvent e) {
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        e.getPresentation().setEnabled(editor != null && !editor.isViewer());
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        if (editor == null) return;

        WriteCommandAction.runWriteCommandAction(e.getProject(), () -> {
            if (editor.getCaretModel().getPrimaryCaret().hasSelection()) {
                String selected = editor.getCaretModel().getPrimaryCaret().getSelectedText();
                int start = editor.getCaretModel().getPrimaryCaret().getSelectionStart();
                int end = editor.getCaretModel().getPrimaryCaret().getSelectionEnd();
                String wrapped = "[" + selected + "](url)";
                editor.getDocument().replaceString(start, end, wrapped);
                editor.getCaretModel().getPrimaryCaret().moveToOffset(start + wrapped.length());
            } else {
                editor.getDocument().insertString(editor.getCaretModel().getOffset(), "[text](url)");
                int pos = editor.getCaretModel().getOffset();
                editor.getCaretModel().getPrimaryCaret().setSelection(pos + 1, pos + 5);
            }
        });
    }
}
