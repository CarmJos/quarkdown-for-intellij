package cc.carm.plugin.intellij.quarkdown.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.NotNull;

public class InsertTableAction extends AnAction implements DumbAware {

    private static final String TABLE_TEMPLATE = "\n| Header 1 | Header 2 | Header 3 |\n"
            + "|----------|----------|----------|\n"
            + "| Cell 1   | Cell 2   | Cell 3   |\n"
            + "| Cell 4   | Cell 5   | Cell 6   |\n";

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
            int offset = editor.getCaretModel().getOffset();
            editor.getDocument().insertString(offset, TABLE_TEMPLATE);
            editor.getCaretModel().getPrimaryCaret().moveToOffset(offset + TABLE_TEMPLATE.length());
        });
    }
}
