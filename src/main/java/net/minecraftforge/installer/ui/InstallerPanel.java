/*
 * Installer
 * Copyright (c) 2016-2018.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation version 2.1
 * of the License.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package net.minecraftforge.installer.ui;

import net.minecraftforge.installer.SimpleInstaller;
import net.minecraftforge.installer.actions.Action;
import net.minecraftforge.installer.actions.ActionCanceledException;
import net.minecraftforge.installer.actions.Actions;
import net.minecraftforge.installer.actions.ProgressCallback;
import net.minecraftforge.installer.actions.TargetValidator;
import net.minecraftforge.installer.json.InstallV1;
import net.minecraftforge.installer.json.OptionalLibrary;

import javax.swing.*;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@SuppressWarnings("unused")
public class InstallerPanel extends JPanel {
    public static final L10nManager TRANSLATIONS = new L10nManager("neoforged/installer", new File(SimpleInstaller.getMCDir(), "libraries/.neoforge_installer.properties"));
    private static final long serialVersionUID = 1L;
    private File targetDir;
    private ButtonGroup choiceButtonGroup;
    private JTextField selectedDirText;
    private JLabel infoLabel;
    private JButton sponsorButton;
    private JDialog dialog;
    //private JLabel sponsorLogo;
    private JPanel sponsorPanel;
    private JPanel fileEntryPanel;
    private List<OptionalListEntry> optionals = new ArrayList<>();
    private Map<String, Function<ProgressCallback, Action>> actions = new HashMap<>();

    private Optional<JButton> proceedButton = Optional.empty();

    private final InstallV1 profile;
    private final File installer;

    private class FileSelectAction extends AbstractAction
    {
        private static final long serialVersionUID = 1L;

        @Override
        public void actionPerformed(ActionEvent e)
        {
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

    private class SelectButtonAction extends AbstractAction
    {
        private static final long serialVersionUID = 1L;

        @Override
        public void actionPerformed(ActionEvent e)
        {
            updateFilePath();
        }

    }

    //private static final String URL = "89504E470D0A1A0A0000000D4948445200000014000000160803000000F79F4C3400000012504C5445FFFFFFCCFFFF9999996666663333330000009E8B9AE70000000274524E53FF00E5B7304A000000564944415478016DCB410E003108425169E5FE579E98584246593EBF8165C24C5C614BED08455ECABC947929F392584A12CD8021EBEF91B0BD46A13969682BCC45E3706AE04E0DE0E42C819FA3D10F10BE954DC4C4DE07EB6A0497D14F4E8F0000000049454E44AE426082";
    public static byte[] hexToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                                 + Character.digit(s.charAt(i+1), 16));
        }
        return data;
    }

    public InstallerPanel(File targetDir, InstallV1 profile, File installer)
    {
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
            version.add(TRANSLATIONS.label("welcome.version"));
            tag = new JLabel(profile.getVersion());
            // and a bold part which represents the actual version
            tag.setFont(tag.getFont().deriveFont(Font.BOLD));
            version.add(tag);
            version.setAlignmentX(CENTER_ALIGNMENT);
            version.setAlignmentY(CENTER_ALIGNMENT);
            logoSplash.add(version);
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
        for (Actions action : Actions.values())
        {
            if (action == Actions.CLIENT && profile.hideClient()) continue;
            if (action == Actions.SERVER && profile.hideServer()) continue;
            if (action == Actions.EXTRACT && profile.hideExtract()) continue;
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
            // Pad the button 5 pixels everywhere to avoid overlapping
            radioButton.setMargin(new Insets(3, 3, 3, 3));
            choiceButtonGroup.add(radioButton);
            choicePanel.add(radioButton);
            // Add a pixel between the buttons that ensures vertical separation and prevents clipping
            choicePanel.add(Box.createRigidArea(new Dimension(1, 1)));
            first = false;
        }

        choicePanel.setAlignmentX(CENTER_ALIGNMENT);
        choicePanel.setAlignmentY(CENTER_ALIGNMENT);
        add(choicePanel);

        JPanel entryPanel = new JPanel();
        entryPanel.setLayout(new BoxLayout(entryPanel,BoxLayout.X_AXIS));

        this.targetDir = targetDir;
        selectedDirText = new JTextField();
        selectedDirText.setEditable(false);
        TRANSLATIONS.setTooltip(selectedDirText, "welcome.target.tooltip");
        selectedDirText.setColumns(30);
        entryPanel.add(selectedDirText);
        JButton dirSelect = new JButton();
        dirSelect.setAction(new FileSelectAction());
        dirSelect.setText("...");
        TRANSLATIONS.setTooltip(dirSelect, "welcome.dirselect.tooltip");
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

        fileEntryPanel = new JPanel();
        fileEntryPanel.setLayout(new BoxLayout(fileEntryPanel,BoxLayout.Y_AXIS));
        fileEntryPanel.add(infoLabel);
        fileEntryPanel.add(Box.createVerticalGlue());
        fileEntryPanel.add(entryPanel);
        fileEntryPanel.setAlignmentX(CENTER_ALIGNMENT);
        fileEntryPanel.setAlignmentY(TOP_ALIGNMENT);
        this.add(fileEntryPanel);
        updateFilePath();
    }


    private void updateFilePath()
    {
        try
        {
            targetDir = targetDir.getCanonicalFile();
            selectedDirText.setText(targetDir.getPath());
        }
        catch (IOException e)
        {

        }

        Action action = actions.get(choiceButtonGroup.getSelection().getActionCommand()).apply(null);
        TargetValidator.ValidationResult valid = action.getTargetValidator().validate(targetDir);

        if (profile.getMirror() != null)
        {
            sponsorButton.setText(action.getSponsorMessage());
            sponsorButton.setToolTipText(profile.getMirror().getHomepage());
            if (profile.getMirror().getImageAddress() != null)
                sponsorButton.setIcon(profile.getMirror().getImage());
            else
                sponsorButton.setIcon(null);
            sponsorPanel.setVisible(true);
        }
        else
        {
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

        if (dialog!=null)
        {
            dialog.invalidate();
            dialog.pack();
        }
    }

    public void run(ProgressCallback monitor)
    {
        final JComboBox<L10nManager.LocaleSelection> languageBox = new JComboBox<>();

        final List<L10nManager.LocaleSelection> known = TRANSLATIONS.getKnownLocales();
        languageBox.setModel(new DefaultComboBoxModel(known.toArray()));

        final Locale current = TRANSLATIONS.getLocale();
        languageBox.setSelectedItem(known.stream().filter(locate -> locate.locale.equals(current)).findFirst().orElse(known.get(0)));
        languageBox.addActionListener(e -> TRANSLATIONS.setLocale(((L10nManager.LocaleSelection)languageBox.getSelectedItem()).locale, true));

        JOptionPane optionPane = new JOptionPane(this, JOptionPane.PLAIN_MESSAGE, JOptionPane.OK_CANCEL_OPTION, null, new Object[] {
                TRANSLATIONS.button("button.proceed"), TRANSLATIONS.button("button.cancel"), languageBox
        });

        // Attempt to change the OK button to a Proceed button
        // Use index 1 (the buttons panel) as 0 is this panel
        final JPanel buttonPanel = (JPanel) optionPane.getComponents()[1];
        final List<JButton> buttons = Arrays.stream(buttonPanel.getComponents())
                .filter(comp -> comp instanceof JButton)
                .map(JButton.class::cast)
                .collect(Collectors.toList());
        if (buttons.size() == 2) {
            proceedButton = Optional.of(buttons.get(0));
            buttons.get(0).addActionListener(e -> optionPane.setValue(JOptionPane.OK_OPTION));
            buttons.get(1).addActionListener(e -> optionPane.setValue(JOptionPane.OK_CANCEL_OPTION));
        }

        dialog = optionPane.createDialog("");
        TRANSLATIONS.translate(dialog, new TranslationTarget<>(Dialog::setTitle), "window.title", profile.getProfile());
        dialog.setIconImages(Images.getWindowIcons());
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setVisible(true);
        int result = (Integer) (optionPane.getValue() != null ? optionPane.getValue() : -1);
        if (result == JOptionPane.OK_OPTION)
        {
            ProgressFrame prog = new ProgressFrame(monitor, Thread.currentThread()::interrupt, "frame.installing", profile.getProfile(), profile.getVersion());
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
                    JOptionPane.showMessageDialog(null, TRANSLATIONS.translate(action.getSuccessMessage()), TRANSLATIONS.translate("installation.complete"), JOptionPane.INFORMATION_MESSAGE);
                }
            } catch (ActionCanceledException e) {
                JOptionPane.showMessageDialog(null, TRANSLATIONS.translate("installation.cancelled"), dialog.getTitle(), JOptionPane.WARNING_MESSAGE);
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

    private void openURL(String url)
    {
        try
        {
            Desktop.getDesktop().browse(new URI(url));
            EventQueue.invokeLater(new Runnable() {
                @Override
                public void run()
                {
                    InstallerPanel.this.dialog.toFront();
                    InstallerPanel.this.dialog.requestFocus();
                }
            });
        }
        catch (Exception ex)
        {
            JOptionPane.showMessageDialog(InstallerPanel.this, "An error occurred launching the browser", "Error launching browser", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static class OptionalListEntry
    {
        OptionalLibrary lib;
        private boolean enabled = false;

        OptionalListEntry(OptionalLibrary lib)
        {
            this.lib = lib;
            this.enabled = lib.getDefault();
        }

        public boolean isEnabled(){ return this.enabled; }
        public void setEnabled(boolean v){ this.enabled = v; }
    }
}