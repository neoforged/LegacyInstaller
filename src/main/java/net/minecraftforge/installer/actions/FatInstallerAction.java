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

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import net.minecraftforge.installer.DownloadUtils;
import net.minecraftforge.installer.json.InstallV1;
import net.minecraftforge.installer.json.Util;
import net.minecraftforge.installer.json.Version;
import net.minecraftforge.installer.ui.TranslatedMessage;
import org.jetbrains.annotations.Nullable;

public class FatInstallerAction extends Action {
    public static final EnumSet<Options> OPTIONS = EnumSet.noneOf(Options.class);

    protected FatInstallerAction(InstallV1 profile, ProgressCallback monitor) {
        super(profile, monitor, true);
    }

    @Override
    public boolean run(File target, Predicate<String> optionals, File installer) throws ActionCanceledException {
        try (final JarFile in = new JarFile(installer);
                final JarOutputStream out = new JarOutputStream(new BufferedOutputStream(new FileOutputStream(target)), newManifest(in.getManifest()))) {
            Enumeration<JarEntry> entries = in.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.getName().equals("META-INF/MANIFEST.MF")) continue;
                ZipEntry ze = new ZipEntry(entry.getName());
                out.putNextEntry(ze);
                copy(in.getInputStream(entry), out);
                out.closeEntry();
            }

            monitor.stage("Downloading metadata");
            writeFromUrl(out, "version_manifest.json", DownloadUtils.MANIFEST_URL);
            net.minecraftforge.installer.json.Manifest.Info man = DownloadUtils.downloadManifest(monitor).get(profile.getMinecraft());
            writeFromUrl(out, "minecraft/" + profile.getMinecraft() + ".json", man.getUrl());
            Version version = Util.getVersionUncached(monitor, man.getUrl());
            if (OPTIONS.contains(Options.MC_JAR)) {
                monitor.stage("Downloading client jar");
                writeFromUrl(out, "minecraft/" + profile.getMinecraft() + "/client.jar", version.getDownload("client").getUrl());
                monitor.stage("Downloading server jar");
                writeFromUrl(out, "minecraft/" + profile.getMinecraft() + "/server.jar", version.getDownload("server").getUrl());

                monitor.stage("Downloading client mappings");
                writeFromUrl(out, "minecraft/" + profile.getMinecraft() + "/client_mappings.txt", version.getDownload("client_mappings").getUrl());
                monitor.stage("Downloading server mappings");
                writeFromUrl(out, "minecraft/" + profile.getMinecraft() + "/server_mappings.txt", version.getDownload("server_mappings").getUrl());
            }

            if (OPTIONS.contains(Options.MC_LIBS) || OPTIONS.contains(Options.INSTALLER_LIBS)) {
                final List<Version.Library> libraries = new ArrayList<>();
                if (OPTIONS.contains(Options.MC_LIBS)) {
                    libraries.addAll(Arrays.asList(version.getLibraries()));
                }
                if (OPTIONS.contains(Options.INSTALLER_LIBS)) {
                    libraries.addAll(Arrays.asList(processors.getLibraries()));

                    monitor.stage("Downloading server starter jar");
                    writeFromUrl(out, "serverstarter.jar", DownloadUtils.SERVER_STARTER_JAR);
                }

                Set<String> duplicates = new HashSet<>();
                libraries.removeIf(library -> !duplicates.add(library.getDownloads() == null ? null : library.getDownloads().getArtifact().getPath()));
                monitor.stage("Downloading libraries");
                monitor.getGlobalProgress().setMaxProgress(libraries.size());
                int progress = 0;
                for (Version.Library library : libraries) {
                    Version.LibraryDownload download = library.getDownloads() == null ? null : library.getDownloads().getArtifact();
                    if (download != null) {
                        monitor.message("Downloading " + download.getPath());
                        writeFromUrl(out, download.getPath(), download.getUrl(), download.getPath());
                    }
                    monitor.getGlobalProgress().progress(++progress);
                }
            }

            return true;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void writeFromUrl(JarOutputStream jos, String name, String url) throws IOException {
        writeFromUrl(jos, name, url, null);
    }

    private void writeFromUrl(JarOutputStream jos, String name, String url, @Nullable String localPath) throws IOException {
        JarEntry entry = new JarEntry("maven/" + name);
        jos.putNextEntry(entry);
        try (InputStream stream = monitor.downloader(url)
                .localPath(localPath)
                .openStream()) {
            copy(stream, jos);
        }
        jos.closeEntry();
    }

    private Manifest newManifest(Manifest input) {
        Manifest man = new Manifest(input);
        if (OPTIONS.size() == 3) {
            man.getMainAttributes().putValue("Offline", "true");
        }
        return man;
    }

    @Override
    public TargetValidator getTargetValidator() {
        return file -> TargetValidator.ValidationResult.valid();
    }

    @Override
    public TranslatedMessage getSuccessMessage() {
        return new TranslatedMessage("installer.action.install.fat.finished", profile.getVersion());
    }

    public enum Options {
        MC_JAR,
        MC_LIBS,
        INSTALLER_LIBS
    }

    private static void copy(InputStream source, OutputStream target) throws IOException {
        byte[] buf = new byte[8192];
        int length;
        while ((length = source.read(buf)) != -1) {
            target.write(buf, 0, length);
        }
    }
}
