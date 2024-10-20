/*
 * Installer
 * Copyright (c) 2016-2018.
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation version 2.1
 * of the License.
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA
 */
package net.minecraftforge.installer.ui;

import java.awt.Color;
import java.awt.Desktop;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.swing.AbstractAction;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.DefaultComboBoxModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import javax.swing.border.LineBorder;
import net.minecraftforge.installer.DownloadUtils;
import net.minecraftforge.installer.SimpleInstaller;
import net.minecraftforge.installer.actions.Action;
import net.minecraftforge.installer.actions.ActionCanceledException;
import net.minecraftforge.installer.actions.Actions;
import net.minecraftforge.installer.actions.FatInstallerAction;
import net.minecraftforge.installer.actions.ProgressCallback;
import net.minecraftforge.installer.actions.ServerInstall;
import net.minecraftforge.installer.actions.TargetValidator;
import net.minecraftforge.installer.json.InstallV1;
import net.minecraftforge.installer.json.OptionalLibrary;

@SuppressWarnings("unused")
public class InstallerPanel extends JPanel {
    private static final Path INSTALLER_SETTINGS = new File(SimpleInstaller.getMCDir(), ".neoforge_installer.properties").toPath();
    public static final L10nManager TRANSLATIONS = new L10nManager("neoforged/installer", INSTALLER_SETTINGS);

    private static final long serialVersionUID = 1L;
    private File targetDir;
    private ButtonGroup choiceButtonGroup;
    private JTextField selectedDirText;
    private JLabel infoLabel;
    private JButton sponsorButton;
    private JDialog dialog;
    //private JLabel sponsorLogo;
    private JPanel sponsorPanel;

    private final AtomicReference<Actions> action = new AtomicReference<>(Actions.CLIENT);
    private JPanel fileEntryPanel;
    private JPanel fatInstallerOptionsPanel;
    private JPanel serverOptionsPanel;

    private JCheckBox fatIncludeMC, fatIncludeMCLibs, fatIncludeInstallerLibs, fatOffline;
    private JCheckBox serverStarterJar;

    private List<OptionalListEntry> optionals = new ArrayList<>();
    private Map<String, Function<ProgressCallback, Action>> actions = new HashMap<>();

    private Optional<JButton> proceedButton = Optional.empty();

    private final InstallV1 profile;
    private final File installer;

    private class FileSelectAction extends AbstractAction {
        private static final long serialVersionUID = 1L;

        @Override
        public void actionPerformed(ActionEvent e) {
            JFileChooser dirChooser = new JFileChooser();
            dirChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            dirChooser.setFileHidingEnabled(false);
            dirChooser.setDialogTitle("Select installation directory");
            dirChooser.setApproveButtonText("Select");
            dirChooser.ensureFileIsVisible(targetDir);
            dirChooser.setSelectedFile(targetDir);

            int response = dirChooser.showOpenDialog(InstallerPanel.this);
            if (response == JFileChooser.APPROVE_OPTION) {
                targetDir = dirChooser.getSelectedFile();
                updateFilePath();
            }
        }
    }

    private class SelectButtonAction extends AbstractAction {
        private static final long serialVersionUID = 1L;

        @Override
        public void actionPerformed(ActionEvent e) {
            updateFilePath();
        }
    }

    //private static final String URL = "89504E470D0A1A0A0000000D4948445200000014000000160803000000F79F4C3400000012504C5445FFFFFFCCFFFF9999996666663333330000009E8B9AE70000000274524E53FF00E5B7304A000000564944415478016DCB410E003108425169E5FE579E98584246593EBF8165C24C5C614BED08455ECABC947929F392584A12CD8021EBEF91B0BD46A13969682BCC45E3706AE04E0DE0E42C819FA3D10F10BE954DC4C4DE07EB6A0497D14F4E8F0000000049454E44AE426082";
    public static byte[] hexToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i + 1), 16));
        }
        return data;
    }

    public InstallerPanel(File targetDir, InstallV1 profile, File installer) {
        this.profile = profile;
        this.installer = installer;

        this.setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        BufferedImage image = Images.getImage(profile.getLogo());
        //final BufferedImage urlIcon = getImage(profile.getUrlIcon(), URL);

        JPanel logoSplash = new JPanel();
        logoSplash.setLayout(new BoxLayout(logoSplash, BoxLayout.Y_AXIS));
        ImageIcon icon = new ImageIcon(image);
        JLabel logoLabel = new JLabel(icon);
        logoLabel.setAlignmentX(CENTER_ALIGNMENT);
        logoLabel.setAlignmentY(CENTER_ALIGNMENT);
        logoLabel.setSize(image.getWidth(), image.getHeight());
        logoSplash.add(logoLabel);
        JLabel tag = new JLabel();
        TRANSLATIONS.translate(tag, TranslationTarget.LABEL_TEXT, profile.getWelcome());
        tag.setAlignmentX(CENTER_ALIGNMENT);
        tag.setAlignmentY(CENTER_ALIGNMENT);
        logoSplash.add(tag);

        {
            // The version is a box that has a first label that is non-bold
            final Box version = Box.createHorizontalBox();
            version.add(TRANSLATIONS.label("installer.welcome.version"));
            tag = new JLabel(profile.getVersion());
            // and a bold part which represents the actual version
            tag.setFont(tag.getFont().deriveFont(Font.BOLD));
            version.add(tag);
            version.setAlignmentX(CENTER_ALIGNMENT);
            version.setAlignmentY(CENTER_ALIGNMENT);
            logoSplash.add(version);
        }

        if (DownloadUtils.OFFLINE_MODE) {
            JLabel offline = new JLabel();
            offline.setFont(offline.getFont().deriveFont(Font.BOLD));
            offline.setForeground(Color.RED);
            TRANSLATIONS.translate(offline, TranslationTarget.LABEL_TEXT, "installer.welcome.offline");
            offline.setAlignmentX(CENTER_ALIGNMENT);
            offline.setAlignmentY(CENTER_ALIGNMENT);
            logoSplash.add(offline);
        }

        logoSplash.setAlignmentX(CENTER_ALIGNMENT);
        logoSplash.setAlignmentY(TOP_ALIGNMENT);
        this.add(logoSplash);

        sponsorPanel = new JPanel();
        sponsorPanel.setLayout(new BoxLayout(sponsorPanel, BoxLayout.X_AXIS));
        sponsorPanel.setAlignmentX(CENTER_ALIGNMENT);
        sponsorPanel.setAlignmentY(CENTER_ALIGNMENT);

        sponsorButton = new JButton();
        sponsorButton.setAlignmentX(CENTER_ALIGNMENT);
        sponsorButton.setAlignmentY(CENTER_ALIGNMENT);
        sponsorButton.setBorderPainted(false);
        sponsorButton.setOpaque(false);
        sponsorButton.addActionListener(e -> openURL(sponsorButton.getToolTipText()));
        sponsorPanel.add(sponsorButton);

        this.add(sponsorPanel);

        choiceButtonGroup = new ButtonGroup();

        JPanel choicePanel = new JPanel();
        choicePanel.setLayout(new BoxLayout(choicePanel, BoxLayout.PAGE_AXIS));
        boolean first = true;
        SelectButtonAction sba = new SelectButtonAction();
        for (Actions action : Actions.values()) {
            if (action == Actions.CLIENT && profile.hideClient()) continue;
            if (action == Actions.SERVER && profile.hideServer()) continue;
            if (action == Actions.FAT_INSTALLER && DownloadUtils.OFFLINE_MODE) continue;

            actions.put(action.name(), prog -> action.getAction(profile, prog));
            JRadioButton radioButton = TRANSLATIONS.radioButton(sba, action.getButtonLabel());
            TRANSLATIONS.setTooltip(radioButton, action.getTooltip());
            radioButton.setActionCommand(action.name());
            radioButton.setSelected(first);
            radioButton.setAlignmentX(LEFT_ALIGNMENT);
            radioButton.setAlignmentY(CENTER_ALIGNMENT);
            radioButton.setSize(15, 15);
            // The default gap of 4 is too small for the size of the buttons, so almost triple the gap
            // to avoid clipping and improve readability
            radioButton.setIconTextGap(10);
            // Pad the button 3 pixels everywhere to avoid overlapping
            radioButton.setMargin(new Insets(3, 3, 3, 3));
            choiceButtonGroup.add(radioButton);
            choicePanel.add(radioButton);
            // Add a pixel between the buttons that ensures vertical separation and prevents clipping
            choicePanel.add(Box.createRigidArea(new Dimension(1, 1)));
            first = false;

            radioButton.addChangeListener(e -> {
                if (radioButton.isSelected()) {
                    this.action.set(action);
                }
            });

            if (action == Actions.FAT_INSTALLER) {
                radioButton.addChangeListener(e -> {
                    if (radioButton.isSelected()) {
                        this.remove(fileEntryPanel);
                        this.add(fatInstallerOptionsPanel);
                    } else {
                        if (!Arrays.asList(this.getComponents()).contains(fileEntryPanel)) {
                            this.add(fileEntryPanel);
                        }
                        this.remove(fatInstallerOptionsPanel);
                    }
                });
            } else if (action == Actions.SERVER) {
                radioButton.addChangeListener(e -> {
                    if (radioButton.isSelected() != serverStarterJar.isSelected()) {
                        serverStarterJar.setVisible(radioButton.isSelected());
                        revalidate();
                    }
                });
            }
        }
        choicePanel.setAlignmentX(CENTER_ALIGNMENT);
        choicePanel.setAlignmentY(CENTER_ALIGNMENT);
        add(choicePanel);

        JPanel entryPanel = new JPanel();
        entryPanel.setLayout(new BoxLayout(entryPanel, BoxLayout.X_AXIS));

        this.targetDir = targetDir;
        selectedDirText = new JTextField();
        selectedDirText.setEditable(false);
        TRANSLATIONS.setTooltip(selectedDirText, "installer.welcome.target.tooltip");
        selectedDirText.setColumns(30);
        entryPanel.add(selectedDirText);
        JButton dirSelect = new JButton();
        dirSelect.setAction(new FileSelectAction());
        dirSelect.setText("...");
        TRANSLATIONS.setTooltip(dirSelect, "installer.welcome.dirselect.tooltip");
        entryPanel.add(dirSelect);

        entryPanel.setAlignmentX(LEFT_ALIGNMENT);
        entryPanel.setAlignmentY(TOP_ALIGNMENT);
        infoLabel = new JLabel();
        infoLabel.setHorizontalTextPosition(JLabel.LEFT);
        infoLabel.setVerticalTextPosition(JLabel.TOP);
        infoLabel.setAlignmentX(LEFT_ALIGNMENT);
        infoLabel.setAlignmentY(TOP_ALIGNMENT);
        infoLabel.setForeground(Color.RED);
        infoLabel.setVisible(false);

        serverOptionsPanel = centreAlignedPanel();
        serverStarterJar = TRANSLATIONS.checkBox("installer.action.install.server.starterjar");
        serverStarterJar.setMargin(new Insets(3, 0, 3, 0));
        serverStarterJar.setVisible(false);
        TRANSLATIONS.setTooltip(serverStarterJar, "installer.action.install.server.starterjar.tooltip");
        serverOptionsPanel.add(serverStarterJar);
        this.add(serverOptionsPanel);

        fileEntryPanel = new JPanel();
        fileEntryPanel.setLayout(new BoxLayout(fileEntryPanel, BoxLayout.Y_AXIS));
        fileEntryPanel.add(infoLabel);
        fileEntryPanel.add(Box.createVerticalGlue());
        fileEntryPanel.add(entryPanel);
        fileEntryPanel.setAlignmentX(CENTER_ALIGNMENT);
        fileEntryPanel.setAlignmentY(TOP_ALIGNMENT);
        this.add(fileEntryPanel);

        fatInstallerOptionsPanel = centreAlignedPanel();

        this.fatIncludeMC = TRANSLATIONS.checkBox("installer.fat.includemc");
        TRANSLATIONS.setTooltip(fatIncludeMC, "installer.fat.includemc.tooltip");
        this.fatIncludeMCLibs = TRANSLATIONS.checkBox("installer.fat.includemclibs");
        TRANSLATIONS.setTooltip(fatIncludeMCLibs, "installer.fat.includemclibs.tooltip");
        this.fatIncludeInstallerLibs = TRANSLATIONS.checkBox("installer.fat.includeinstallerlibs");
        TRANSLATIONS.setTooltip(fatIncludeInstallerLibs, "installer.fat.includeinstallerlibs.tooltip");
        fatInstallerOptionsPanel.add(fatIncludeMC);
        fatInstallerOptionsPanel.add(fatIncludeMCLibs);
        fatInstallerOptionsPanel.add(fatIncludeInstallerLibs);

        final List<JCheckBox> fatOptions = Arrays.asList(fatIncludeMC, fatIncludeMCLibs, fatIncludeInstallerLibs);

        this.fatOffline = TRANSLATIONS.checkBox("installer.fat.offline");
        TRANSLATIONS.setTooltip(fatOffline, "installer.fat.offline.tooltip");
        fatOffline.setMargin(new Insets(3, 0, 0, 0));
        fatOffline.addChangeListener(e -> {
            if (fatOffline.isSelected()) {
                fatOptions.forEach(box -> {
                    box.setSelected(true);
                    box.setEnabled(false);
                });
            } else {
                fatOptions.forEach(box -> box.setEnabled(true));
            }
        });
        fatInstallerOptionsPanel.add(fatOffline);

        updateFilePath();
    }

    private JPanel centreAlignedPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setAlignmentX(CENTER_ALIGNMENT);
        panel.setAlignmentY(TOP_ALIGNMENT);
        return panel;
    }

    private void updateFilePath() {
        try {
            targetDir = targetDir.getCanonicalFile();
            selectedDirText.setText(targetDir.getPath());
        } catch (IOException e) {

        }

        Action action = actions.get(choiceButtonGroup.getSelection().getActionCommand()).apply(null);
        TargetValidator.ValidationResult valid = action.getTargetValidator().validate(targetDir);

        if (profile.getMirror() != null && profile.getMirror().isAdvertised()) {
            sponsorButton.setText(action.getSponsorMessage());
            sponsorButton.setToolTipText(profile.getMirror().getHomepage());
            if (profile.getMirror().getImageAddress() != null)
                sponsorButton.setIcon(profile.getMirror().getImage());
            else
                sponsorButton.setIcon(null);
            sponsorPanel.setVisible(true);
        } else {
            sponsorPanel.setVisible(false);
        }

        if (valid.valid) {
            selectedDirText.setForeground(null);
            infoLabel.setVisible(false);
            fileEntryPanel.setBorder(null);
            proceedButton.ifPresent(button -> button.setEnabled(true));
        } else {
            selectedDirText.setForeground(Color.RED);
            fileEntryPanel.setBorder(new LineBorder(Color.RED));
            TRANSLATIONS.translate(infoLabel, TranslationTarget.html(TranslationTarget.LABEL_TEXT), valid.message.key, valid.message.arguments);
            infoLabel.setVisible(true);
            proceedButton.ifPresent(button -> button.setEnabled(!valid.critical));
        }

        if (dialog != null) {
            dialog.invalidate();
            dialog.pack();
        }
    }

    public void run(ProgressCallback monitor) {
        final JComboBox<L10nManager.LocaleSelection> languageBox = new JComboBox<>();

        final List<L10nManager.LocaleSelection> known = TRANSLATIONS.getKnownLocales();
        languageBox.setModel(new DefaultComboBoxModel(known.toArray()));

        final Locale current = TRANSLATIONS.getLocale();
        languageBox.setSelectedItem(known.stream().filter(locate -> locate.locale.equals(current)).findFirst().orElse(known.get(0)));
        languageBox.addActionListener(e -> TRANSLATIONS.setLocale(((L10nManager.LocaleSelection) languageBox.getSelectedItem()).locale, true));

        JButton proceedButton = TRANSLATIONS.button("installer.button.proceed");
        JButton cancelButton = TRANSLATIONS.button("installer.button.cancel");
        JOptionPane optionPane = new JOptionPane(this, JOptionPane.PLAIN_MESSAGE, JOptionPane.OK_CANCEL_OPTION, null, new Object[] { proceedButton, cancelButton, languageBox });

        proceedButton.addActionListener(e -> optionPane.setValue(JOptionPane.OK_OPTION));
        cancelButton.addActionListener(e -> optionPane.setValue(JOptionPane.OK_CANCEL_OPTION));
        this.proceedButton = Optional.of(proceedButton);

        dialog = optionPane.createDialog("");
        TRANSLATIONS.translate(dialog, new TranslationTarget<>(Dialog::setTitle), "installer.window.title", profile.getProfile());
        dialog.setIconImages(Images.getWindowIcons(profile.getIcon()));
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setVisible(true);
        int result = (Integer) (optionPane.getValue() != null ? optionPane.getValue() : -1);
        if (result == JOptionPane.OK_OPTION) {
            if (action.get() == Actions.FAT_INSTALLER) {
                if (fatIncludeMC.isSelected()) {
                    JOptionPane isOk = new JOptionPane(new JLabel(TRANSLATIONS.translate("installer.fat.includemc.warning")), JOptionPane.PLAIN_MESSAGE, JOptionPane.OK_CANCEL_OPTION);
                    JDialog okDialog = isOk.createDialog("WARNING");
                    okDialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
                    okDialog.setVisible(true);

                    if (isOk.getValue() == null || (Integer) isOk.getValue() == JOptionPane.OK_CANCEL_OPTION) {
                        System.exit(0);
                    }
                    FatInstallerAction.OPTIONS.add(FatInstallerAction.Options.MC_JAR);

                    okDialog.dispose();
                }
                if (fatIncludeMCLibs.isSelected()) {
                    FatInstallerAction.OPTIONS.add(FatInstallerAction.Options.MC_LIBS);
                }
                if (fatIncludeInstallerLibs.isSelected()) {
                    FatInstallerAction.OPTIONS.add(FatInstallerAction.Options.INSTALLER_LIBS);
                }
                targetDir = new File(installer.getParent(), installer.getName().replace(".jar", "-fat.jar"));
            } else if (action.get() == Actions.SERVER) {
                ServerInstall.serverStarterJar = serverStarterJar.isSelected();
            }

            ProgressFrame prog = new ProgressFrame(monitor, Thread.currentThread()::interrupt, "installer.frame.installing", profile);
            SimpleInstaller.hookStdOut(prog);
            Predicate<String> optPred = input -> {
                Optional<OptionalListEntry> ent = this.optionals.stream().filter(e -> e.lib.getArtifact().equals(input)).findFirst();
                return !ent.isPresent() || ent.get().isEnabled();
            };
            Action action = actions.get(choiceButtonGroup.getSelection().getActionCommand()).apply(prog);
            try {
                prog.setVisible(true);
                prog.toFront();
                if (action.run(targetDir, optPred, installer)) {
                    prog.start("Finished!");
                    prog.getGlobalProgress().percentageProgress(1);
                    JOptionPane.showMessageDialog(null, TRANSLATIONS.translate(action.getSuccessMessage()), TRANSLATIONS.translate("installer.installation.complete"), JOptionPane.INFORMATION_MESSAGE);
                }
            } catch (ActionCanceledException e) {
                JOptionPane.showMessageDialog(null, TRANSLATIONS.translate("installer.installation.cancelled"), dialog.getTitle(), JOptionPane.WARNING_MESSAGE);
            } catch (Exception e) {
                JOptionPane.showMessageDialog(null, "There was an exception running task: " + e.toString(), "Error", JOptionPane.ERROR_MESSAGE);
                e.printStackTrace();
            } finally {
                prog.dispose();
                SimpleInstaller.hookStdOut(monitor);
            }
        } else if (result == JOptionPane.OK_CANCEL_OPTION) {
            System.exit(0);
        }
        dialog.dispose();
    }

    private void openURL(String url) {
        try {
            Desktop.getDesktop().browse(new URI(url));
            EventQueue.invokeLater(new Runnable() {
                @Override
                public void run() {
                    InstallerPanel.this.dialog.toFront();
                    InstallerPanel.this.dialog.requestFocus();
                }
            });
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(InstallerPanel.this, "An error occurred launching the browser", "Error launching browser", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static class OptionalListEntry {
        OptionalLibrary lib;
        private boolean enabled = false;

        OptionalListEntry(OptionalLibrary lib) {
            this.lib = lib;
            this.enabled = lib.getDefault();
        }

        public boolean isEnabled() {
            return this.enabled;
        }

        public void setEnabled(boolean v) {
            this.enabled = v;
        }
    }
}
