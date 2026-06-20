package cc.carm.plugin.intellij.quarkdown.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

public class InsertImageAction extends AnAction implements DumbAware {

    @Override
    public void update(@NotNull AnActionEvent e) {
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        e.getPresentation().setEnabled(editor != null && !editor.isViewer());
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        if (editor == null) return;

        InsertImageDialog dialog = new InsertImageDialog(e.getProject());

        VirtualFile docFile = FileDocumentManager.getInstance().getFile(editor.getDocument());
        if (docFile != null) {
            dialog.setCurrentFileDir(docFile.getParent());
        }

        if (!dialog.showAndGet()) return;

        String syntax = dialog.buildImageSyntax();

        WriteCommandAction.runWriteCommandAction(e.getProject(), () -> {
            if (editor.getCaretModel().getPrimaryCaret().hasSelection()) {
                int start = editor.getCaretModel().getPrimaryCaret().getSelectionStart();
                int end = editor.getCaretModel().getPrimaryCaret().getSelectionEnd();
                editor.getDocument().replaceString(start, end, syntax);
                editor.getCaretModel().getPrimaryCaret().moveToOffset(start + syntax.length());
            } else {
                editor.getDocument().insertString(editor.getCaretModel().getOffset(), syntax);
            }
        });
    }
}
