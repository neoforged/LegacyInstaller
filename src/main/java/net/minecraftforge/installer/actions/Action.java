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
package net.minecraftforge.installer.actions;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import javax.swing.JOptionPane;
import net.minecraftforge.installer.DownloadUtils;
import net.minecraftforge.installer.SimpleInstaller;
import net.minecraftforge.installer.json.Artifact;
import net.minecraftforge.installer.json.InstallV1;
import net.minecraftforge.installer.json.Util;
import net.minecraftforge.installer.json.Version;
import net.minecraftforge.installer.json.Version.Library;
import net.minecraftforge.installer.json.Version.LibraryDownload;
import net.minecraftforge.installer.ui.TranslatedMessage;

public abstract class Action {
    protected final InstallV1 profile;
    protected final ProgressCallback monitor;
    protected final PostProcessors processors;
    protected final Version version;
    private List<Artifact> grabbed = new ArrayList<>();

    protected Action(InstallV1 profile, ProgressCallback monitor, boolean isClient) {
        this.profile = profile;
        this.monitor = monitor;
        this.processors = new PostProcessors(profile, isClient, monitor);
        this.version = Util.loadVersion(profile);
    }

    protected void error(String message) {
        if (!SimpleInstaller.headless)
            JOptionPane.showMessageDialog(null, message, "Error", JOptionPane.ERROR_MESSAGE);
        monitor.stage(message);
    }

    public abstract boolean run(File target, Predicate<String> optionals, File installer) throws ActionCanceledException;

    public abstract TargetValidator getTargetValidator();

    public abstract TranslatedMessage getSuccessMessage();

    public String getSponsorMessage() {
        return profile.getMirror() != null && profile.getMirror().isAdvertised() ? String.format(SimpleInstaller.headless ? "Data kindly mirrored by %2$s at %1$s" : "<html><a href=\'%s\'>Data kindly mirrored by %s</a></html>", profile.getMirror().getHomepage(), profile.getMirror().getName()) : null;
    }

    protected boolean downloadLibraries(File librariesDir, Predicate<String> optionals, List<File> additionalLibDirs) throws ActionCanceledException {
        monitor.start("Downloading libraries");
        String userHome = System.getProperty("user.home");
        if (userHome != null && !userHome.isEmpty()) {
            File mavenLocalHome = new File(userHome, ".m2/repository");
            if (mavenLocalHome.exists()) {
                additionalLibDirs.add(mavenLocalHome);
            }
        }
        monitor.message(String.format("Found %d additional library directories", additionalLibDirs.size()));

        List<Library> libraries = new ArrayList<>();
        libraries.addAll(Arrays.asList(version.getLibraries()));
        libraries.addAll(Arrays.asList(processors.getLibraries()));

        StringBuilder output = new StringBuilder();
        monitor.getStepProgress().setMaxProgress(libraries.size());
        int progress = 0;

        final ProgressCallback targetMonitor = monitor.withoutDownloadProgress();
        for (Library lib : libraries) {
            checkCancel();
            if (!DownloadUtils.downloadLibrary(targetMonitor, lib, librariesDir, optionals, grabbed, additionalLibDirs)) {
                LibraryDownload download = lib.getDownloads() == null ? null : lib.getDownloads().getArtifact();
                if (download != null && !download.getUrl().isEmpty()) // If it doesn't have a URL we can't download it, assume we install it later
                    output.append('\n').append(lib.getName());
            }
            monitor.getStepProgress().progress(++progress);
        }
        String bad = output.toString();
        if (!bad.isEmpty()) {
            error("These libraries failed to download. Try again.\n" + bad);
            return false;
        }
        return true;
    }

    protected int downloadedCount() {
        return grabbed.size();
    }

    protected int getTaskCount() {
        return profile.getLibraries().length + processors.getTaskCount();
    }

    protected void checkCancel() throws ActionCanceledException {
        try {
            Thread.sleep(1);
        } catch (InterruptedException e) {
            throw new ActionCanceledException(e);
        }
    }
}
