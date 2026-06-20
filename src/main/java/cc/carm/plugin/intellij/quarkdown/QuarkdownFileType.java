package cc.carm.plugin.intellij.quarkdown;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public final class QuarkdownFileType implements FileType {

    public static final QuarkdownFileType INSTANCE = new QuarkdownFileType();

    @NotNull
    @Override
    public String getName() {
        return "Quarkdown";
    }

    @NotNull
    @Override
    public String getDescription() {
        return "Quarkdown document";
    }

    @NotNull
    @Override
    public String getDefaultExtension() {
        return "qd";
    }

    @Nullable
    @Override
    public Icon getIcon() {
        return QuarkdownIcons.FILE;
    }

    @Override
    public boolean isBinary() {
        return false;
    }

    @Override
    public boolean isReadOnly() {
        return false;
    }

    @Nullable
    @Override
    public String getCharset(@NotNull VirtualFile file, @NotNull byte[] content) {
        return null;
    }
}
