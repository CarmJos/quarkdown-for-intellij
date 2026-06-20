package cc.carm.plugin.intellij.quarkdown.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.NotNull;

public abstract class BaseToggleAction extends AnAction implements DumbAware {

    @Override
    public void update(@NotNull AnActionEvent e) {
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        e.getPresentation().setEnabled(editor != null && !editor.isViewer());
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        if (editor == null) return;
        Document document = editor.getDocument();

        WriteCommandAction.runWriteCommandAction(e.getProject(), () -> {
            for (Caret caret : editor.getCaretModel().getAllCarets()) {
                if (caret.hasSelection()) {
                    toggleSelection(document, caret);
                } else {
                    insertWrappers(document, caret);
                }
            }
        });
    }

    protected abstract @NotNull String getWrapper();

    private void toggleSelection(Document document, Caret caret) {
        int start = caret.getSelectionStart();
        int end = caret.getSelectionEnd();
        String selected = document.getText().substring(start, end);
        String wrapper = getWrapper();
        int wrapperLen = wrapper.length();

        if (selected.startsWith(wrapper) && selected.endsWith(wrapper) && selected.length() >= wrapperLen * 2) {
            String inner = selected.substring(wrapperLen, selected.length() - wrapperLen);
            document.replaceString(start, end, inner);
            caret.removeSelection();
            caret.moveToOffset(start + inner.length());
        } else {
            String wrapped = wrapper + selected + wrapper;
            document.replaceString(start, end, wrapped);
            caret.setSelection(start, start + wrapped.length());
        }
    }

    private void insertWrappers(Document document, Caret caret) {
        String wrapper = getWrapper();
        document.insertString(caret.getOffset(), wrapper + wrapper);
        caret.moveCaretRelatively(wrapper.length(), 0, false, false);
    }
}
