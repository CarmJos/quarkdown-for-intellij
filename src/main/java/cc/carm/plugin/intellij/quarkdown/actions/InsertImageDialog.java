package cc.carm.plugin.intellij.quarkdown.actions;

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.TextComponentAccessor;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBRadioButton;
import com.intellij.ui.components.JBTextField;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

public class InsertImageDialog extends DialogWrapper {

    private final Project project;

    private TextFieldWithBrowseButton pathField;
    private JBRadioButton percentRadio;
    private JBRadioButton fixedSizeRadio;
    private JSlider percentSlider;
    private JBTextField percentInput;
    private JBTextField widthField;
    private JBTextField heightField;
    private ComboBox<String> unitCombo;
    private JBTextField labelField;
    private JBTextField idField;

    private JPanel percentPanel;
    private JPanel fixedSizePanel;

    @Nullable
    private VirtualFile currentFileDir;

    public InsertImageDialog(@Nullable Project project) {
        super(project);
        this.project = project;
        setTitle("Insert Image");
        init();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        JPanel panel = new JPanel(new GridLayoutManager(7, 2, JBUI.insets(10), -1, -1));

        int row = 0;

        JBLabel pathLabel = new JBLabel("Image Path:");
        pathField = new TextFieldWithBrowseButton();
        FileChooserDescriptor imageDescriptor = new FileChooserDescriptor(true, false, false, false, false, false)
                .withFileFilter(f -> {
                    String name = f.getName().toLowerCase();
                    return name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg")
                            || name.endsWith(".gif") || name.endsWith(".bmp") || name.endsWith(".svg")
                            || name.endsWith(".webp");
                });
        pathField.addBrowseFolderListener(
                "Select Image", "Select an image file to insert", project, imageDescriptor,
                TextComponentAccessor.TEXT_FIELD_WHOLE_TEXT
        );
        pathField.addPropertyChangeListener("text", evt -> onPathChanged());
        panel.add(pathLabel, new GridConstraints(row, 0, 1, 1,
                GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED,
                null, null, null));
        panel.add(pathField, new GridConstraints(row, 1, 1, 1,
                GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
                GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED,
                null, null, null));
        row++;

        JBLabel modeLabel = new JBLabel("Size Mode:");
        JPanel modePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        percentRadio = new JBRadioButton("Percentage", true);
        fixedSizeRadio = new JBRadioButton("Fixed Size", false);
        ButtonGroup modeGroup = new ButtonGroup();
        modeGroup.add(percentRadio);
        modeGroup.add(fixedSizeRadio);
        modePanel.add(percentRadio);
        modePanel.add(Box.createHorizontalStrut(16));
        modePanel.add(fixedSizeRadio);
        panel.add(modeLabel, new GridConstraints(row, 0, 1, 1,
                GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED,
                null, null, null));
        panel.add(modePanel, new GridConstraints(row, 1, 1, 1,
                GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
                GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED,
                null, null, null));
        row++;

        percentPanel = buildPercentPanel();
        fixedSizePanel = buildFixedSizePanel();
        fixedSizePanel.setVisible(false);

        panel.add(percentPanel, new GridConstraints(row, 0, 1, 2,
                GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
                GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED,
                null, null, null));
        panel.add(fixedSizePanel, new GridConstraints(row, 0, 1, 2,
                GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
                GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED,
                null, null, null));
        row++;

        percentRadio.addActionListener(e -> toggleSizePanels());
        fixedSizeRadio.addActionListener(e -> toggleSizePanels());

        JBLabel labelLabel = new JBLabel("Label:");
        labelField = new JBTextField();
        panel.add(labelLabel, new GridConstraints(row, 0, 1, 1,
                GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED,
                null, null, null));
        panel.add(labelField, new GridConstraints(row, 1, 1, 1,
                GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
                GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED,
                null, null, null));
        row++;

        JBLabel idLabel = new JBLabel("Image ID:");
        idField = new JBTextField();
        panel.add(idLabel, new GridConstraints(row, 0, 1, 1,
                GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED,
                null, null, null));
        panel.add(idField, new GridConstraints(row, 1, 1, 1,
                GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
                GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED,
                null, null, null));
        row++;

        return panel;
    }

    private JPanel buildPercentPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 0));
        JBLabel label = new JBLabel("Scale:");
        panel.add(label, BorderLayout.WEST);

        percentSlider = new JSlider(0, 150, 100);
        percentSlider.setMajorTickSpacing(25);
        percentSlider.setMinorTickSpacing(5);
        percentSlider.setPaintTicks(true);
        percentSlider.setPaintLabels(false);
        panel.add(percentSlider, BorderLayout.CENTER);

        percentInput = new JBTextField(5);
        percentInput.setText("100");
        percentInput.setHorizontalAlignment(SwingConstants.RIGHT);

        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        rightPanel.add(percentInput);
        rightPanel.add(new JBLabel("%"));
        panel.add(rightPanel, BorderLayout.EAST);

        percentSlider.addChangeListener(e -> {
            if (!percentSlider.getValueIsAdjusting()) {
                percentInput.setText(String.valueOf(percentSlider.getValue()));
            }
        });

        percentInput.addActionListener(e -> {
            try {
                int val = Integer.parseInt(percentInput.getText().replace("%", "").trim());
                percentSlider.setValue(Math.min(Math.max(val, 0), 150));
            } catch (NumberFormatException ignored) {
            }
        });

        return panel;
    }

    private JPanel buildFixedSizePanel() {
        JPanel panel = new JPanel(new GridLayoutManager(2, 4, JBUI.emptyInsets(), -1, -1));

        JBLabel widthLabel = new JBLabel("Width:");
        widthField = new JBTextField(6);
        JBLabel heightLabel = new JBLabel("Height:");
        heightField = new JBTextField(6);

        unitCombo = new ComboBox<>(new String[]{"px", "cm", "in"});
        unitCombo.setSelectedIndex(0);

        panel.add(widthLabel, new GridConstraints(0, 0, 1, 1,
                GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED,
                null, null, null));
        panel.add(widthField, new GridConstraints(0, 1, 1, 1,
                GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
                GridConstraints.SIZEPOLICY_CAN_SHRINK, GridConstraints.SIZEPOLICY_FIXED,
                null, null, null));
        panel.add(new JPanel(), new GridConstraints(0, 2, 1, 1,
                GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED,
                null, null, null));
        panel.add(unitCombo, new GridConstraints(0, 3, 1, 1,
                GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED,
                null, null, null));

        panel.add(heightLabel, new GridConstraints(1, 0, 1, 1,
                GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED,
                null, null, null));
        panel.add(heightField, new GridConstraints(1, 1, 1, 1,
                GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
                GridConstraints.SIZEPOLICY_CAN_SHRINK, GridConstraints.SIZEPOLICY_FIXED,
                null, null, null));

        JBLabel optionalLabel = new JBLabel("(optional)");
        optionalLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        panel.add(optionalLabel, new GridConstraints(1, 3, 1, 1,
                GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED,
                null, null, null));

        return panel;
    }

    private void toggleSizePanels() {
        boolean isPercent = percentRadio.isSelected();
        percentPanel.setVisible(isPercent);
        fixedSizePanel.setVisible(!isPercent);
        SwingUtilities.getWindowAncestor(percentPanel).pack();
    }

    private void onPathChanged() {
        if (idField.getText().isEmpty()) {
            String path = pathField.getText().trim();
            if (!path.isEmpty()) {
                String fileName = new File(path).getName();
                int dotIdx = fileName.lastIndexOf('.');
                if (dotIdx > 0) {
                    fileName = fileName.substring(0, dotIdx);
                }
                idField.setText(fileName);
            }
        }
    }

    public void setCurrentFileDir(@Nullable VirtualFile dir) {
        this.currentFileDir = dir;
    }

    @Override
    protected void doOKAction() {
        if (pathField.getText().trim().isEmpty()) return;
        super.doOKAction();
    }

    public @NotNull String buildImageSyntax() {
        String rawPath = pathField.getText().trim();
        String path = toRelativePath(rawPath);
        String size = buildSizeString();
        String label = labelField.getText().trim();
        String id = idField.getText().trim();

        StringBuilder sb = new StringBuilder();
        sb.append("!(").append(size).append(")");
        sb.append("[").append(id).append("]");
        sb.append("(").append(path);
        if (!label.isEmpty()) {
            sb.append(" \"").append(label).append("\"");
        }
        sb.append(")");
        if (!id.isEmpty()) {
            sb.append(" {#").append(id).append("}");
        }
        return sb.toString();
    }

    private String toRelativePath(String rawPath) {
        if (currentFileDir == null) return rawPath;
        try {
            Path imagePath = Paths.get(rawPath);
            if (!imagePath.isAbsolute()) return rawPath;
            Path baseDir = Paths.get(currentFileDir.getPath());
            Path relative = baseDir.relativize(imagePath);
            return relative.toString().replace('\\', '/');
        } catch (IllegalArgumentException ignored) {
        }
        return rawPath;
    }

    private @NotNull String buildSizeString() {
        if (percentRadio.isSelected()) {
            String text = percentInput.getText().replace("%", "").trim();
            return text.isEmpty() ? "100%" : text + "%";
        }

        String unit = (String) unitCombo.getSelectedItem();
        String w = widthField.getText().trim();
        String h = heightField.getText().trim();

        if (!w.isEmpty() && !h.isEmpty()) {
            return w + unit + " " + h + unit;
        }
        if (!w.isEmpty()) {
            return w + unit + " _";
        }
        if (!h.isEmpty()) {
            return "_ " + h + unit;
        }
        return "100%";
    }

}
