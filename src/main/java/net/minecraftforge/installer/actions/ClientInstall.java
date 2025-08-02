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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import net.minecraftforge.installer.json.InstallV1;
import net.minecraftforge.installer.json.Util;
import net.minecraftforge.installer.json.Version;
import net.minecraftforge.installer.json.Version.Download;
import net.minecraftforge.installer.ui.TranslatedMessage;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

public class ClientInstall extends Action {

    public static boolean skipLibrariesDownload;
    public static File librariesDir;
    public static File versionsDir;
    public static boolean skipLauncherProfile;
    public static File mergedVersionJsonFile;

    public ClientInstall(InstallV1 profile, ProgressCallback monitor) {
        super(profile, monitor, true);
    }

    @Override
    public boolean run(File target, Predicate<String> optionals, File installer) throws ActionCanceledException {
        if (!target.exists()) {
            error("There is no minecraft installation at: " + target);
            return false;
        }

        File launcherProfiles = new File(target, "launcher_profiles.json");
        File launcherProfilesMS = new File(target, "launcher_profiles_microsoft_store.json");
        if (!skipLauncherProfile && !launcherProfiles.exists() && !launcherProfilesMS.exists()) {
            error("There is no minecraft launcher profile in \"" + target + "\", you need to run the launcher first!");
            return false;
        }

        if (versionsDir == null) {
            versionsDir = new File(target, "versions");
        }
        if (librariesDir == null) {
            librariesDir = new File(target, "libraries");
        }
        librariesDir.mkdirs();
        versionsDir.mkdirs();

        checkCancel();

        // Extract version json
        if (!skipLauncherProfile) {
            monitor.stage("Extracting json");
            try (InputStream stream = Util.class.getResourceAsStream(profile.getJson())) {
                File json = new File(versionsDir, profile.getVersion() + '/' + profile.getVersion() + ".json");
                json.getParentFile().mkdirs();
                Files.copy(stream, json.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                error("  Failed to extract");
                e.printStackTrace();
                return false;
            }
            checkCancel();
        }

        // Download Vanilla main jar/json
        monitor.stage("Considering minecraft client jar");
        File versionVanilla = new File(versionsDir, profile.getMinecraft());
        if (!versionVanilla.mkdirs() && !versionVanilla.isDirectory()) {
            if (!versionVanilla.delete()) {
                error("There was a problem with the launcher version data. You will need to clear " + versionVanilla + " manually.");
                return false;
            } else
                versionVanilla.mkdirs();
        }
        checkCancel();

        File clientTarget = new File(versionVanilla, profile.getMinecraft() + ".jar");
        if (!clientTarget.exists()) {
            File versionJson = new File(versionVanilla, profile.getMinecraft() + ".json");
            Version vanilla = Util.getVanillaVersion(monitor, profile.getMinecraft(), versionJson);
            if (vanilla == null) {
                error("Failed to download version manifest, can not find client jar URL.");
                return false;
            }

            // Now is a perfect time to write the merged JSON file since we just downloaded the parent JSON
            if (mergedVersionJsonFile != null) {
                monitor.stage("Writing merged version JSON");
                writeMergedJson(versionsDir, mergedVersionJsonFile);
            }

            Download client = vanilla.getDownload("client");
            if (client == null) {
                error("Failed to download minecraft client, info missing from manifest: " + versionJson);
                return false;
            }

            if (!monitor.downloader(client.getUrl())
                    .sha(client.getSha1())
                    .localPath("minecraft/" + profile.getMinecraft() + "/client.jar")
                    .download(clientTarget)) {
                clientTarget.delete();
                error("Downloading minecraft client failed, invalid checksum.\n" +
                        "Try again, or use the vanilla launcher to install the vanilla version.");
                return false;
            }
        }

        // Download Libraries
        if (!downloadLibraries(librariesDir, optionals, new ArrayList<>(), skipLibrariesDownload ? EnumSet.of(LibraryCategory.INSTALLER) : EnumSet.allOf(LibraryCategory.class)))
            return false;
        checkCancel();

        /*
        String modListType = VersionInfo.getModListType();
        File modListFile = new File(target, "mods/mod_list.json");
        
        JsonRootNode versionJson = JsonNodeFactories.object(VersionInfo.getVersionInfo().getFields());
        
        if ("absolute".equals(modListType))
        {
            modListFile = new File(versionTarget, "mod_list.json");
            JsonStringNode node = (JsonStringNode)versionJson.getNode("minecraftArguments");
            try {
                Field value = JsonStringNode.class.getDeclaredField("value");
                value.setAccessible(true);
                String args = (String)value.get(node);
                value.set(node, args + " --modListFile \"absolute:"+modListFile.getAbsolutePath()+ "\"");
            } catch (Exception e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
        
        if (!"none".equals(modListType))
        {
            if (!OptionalLibrary.saveModListJson(librariesDir, modListFile, VersionInfo.getOptionals(), optionals))
            {
                JOptionPane.showMessageDialog(null, "Failed to write mod_list.json, optional mods may not be loaded.", "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
        */

        if (!processors.process(librariesDir, clientTarget, target, installer))
            return false;

        checkCancel();

        if (!skipLauncherProfile) {
            monitor.stage("Injecting profile");
            if (launcherProfiles.exists() && !injectProfile(launcherProfiles))
                return false;
            if (launcherProfilesMS.exists() && !injectProfile(launcherProfilesMS))
                return false;
        }

        return true;
    }

    private void writeMergedJson(File versionsDir, File mergedVersionJsonFile) {

        JsonObject nfManifest;
        try (InputStream stream = Util.class.getResourceAsStream(profile.getJson())) {
            nfManifest = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException e) {
            error("Failed to read embedded version JSON");
            e.printStackTrace();
            return;
        }

        // Merge extended version manifests into this one
        JsonPrimitive inheritsFrom = nfManifest.getAsJsonPrimitive("inheritsFrom");
        if (inheritsFrom != null) {
            List<JsonObject> manifests;
            try {
                manifests = loadVersionManifests(versionsDir, inheritsFrom.getAsString());
            } catch (IOException e) {
                error("Failed to read parent manifests");
                e.printStackTrace();
                return;
            }
            manifests.add(nfManifest);

            // Start with the first, then layer everything else on top. We consider these uncached and free to manipulate
            JsonObject baseManifest = manifests.get(0);
            Map<String, JsonObject> baseLibraryIndex = null;

            for (int i = 1; i < manifests.size(); i++) {
                JsonObject overlayManifest = manifests.get(i);
                for (Map.Entry<String, JsonElement> entry : overlayManifest.entrySet()) {
                    JsonElement baseValue = baseManifest.get(entry.getKey());
                    if (baseValue != null) {
                        // Special-case merge logic for arguments and libraries
                        switch (entry.getKey()) {
                            case "arguments":
                                mergeArguments(baseValue, entry.getValue());
                                continue;
                            case "libraries":
                                if (baseLibraryIndex == null) {
                                    JsonArray baseLibraries = baseValue.getAsJsonArray();
                                    baseLibraryIndex = new HashMap<>(baseLibraries.size());
                                    for (JsonElement baseLibrary : baseLibraries) {
                                        JsonObject baseLibraryObj = baseLibrary.getAsJsonObject();
                                        String libraryName = baseLibraryObj.getAsJsonPrimitive("name").getAsString();
                                        baseLibraryIndex.put(libraryName, baseLibraryObj);
                                    }
                                }
                                mergeLibraries(baseValue, entry.getValue(), baseLibraryIndex);
                                continue;
                        }
                    }

                    baseManifest.add(entry.getKey(), entry.getValue());
                }
            }
            nfManifest = baseManifest;
        }

        // Write the merged version manifest
        File parent = mergedVersionJsonFile.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        try (OutputStream out = new BufferedOutputStream(new FileOutputStream(mergedVersionJsonFile))) {
            Util.GSON.toJson(nfManifest, new OutputStreamWriter(out, StandardCharsets.UTF_8));
        } catch (IOException e) {
            error("Failed to read parent manifests");
            e.printStackTrace();
            return;
        }

    }

    private void mergeLibraries(JsonElement base, JsonElement overlay, Map<String, JsonObject> baseLibraryIndex) {
        JsonArray baseLibraries = base.getAsJsonArray();
        JsonArray overlayLibraries = overlay.getAsJsonArray();

        for (JsonElement overlayLibrary : overlayLibraries) {
            JsonObject overlayLibraryObj = overlayLibrary.getAsJsonObject();
            String name = overlayLibraryObj.getAsJsonPrimitive("name").getAsString();
            JsonObject baseLibraryObj = baseLibraryIndex.get(name);
            if (baseLibraryObj != null) {
                // Overrides an existing library
                for (Map.Entry<String, JsonElement> entry : overlayLibraryObj.entrySet()) {
                    baseLibraryObj.add(entry.getKey(), entry.getValue());
                }
            } else {
                // New library
                baseLibraries.add(overlayLibraryObj);
                baseLibraryIndex.put(name, overlayLibraryObj);
            }
        }
    }

    private void mergeArguments(JsonElement base, JsonElement overlay) {
        JsonObject baseObject = base.getAsJsonObject();

        // Really just "game" and "jvm" but the logic is the same (simple append)
        for (Map.Entry<String, JsonElement> entry : overlay.getAsJsonObject().entrySet()) {
            if (!baseObject.has(entry.getKey())) {
                baseObject.add(entry.getKey(), entry.getValue());
            } else {
                baseObject.getAsJsonArray(entry.getKey()).addAll(entry.getValue().getAsJsonArray());
            }
        }
    }

    // Returns the inherited manifests first
    private static List<JsonObject> loadVersionManifests(File versionsDir, String versionId) throws IOException {
        // Read back the version manifest and get the startup arguments
        File manifestPath = new File(versionsDir, versionId + "/" + versionId + ".json");
        JsonObject manifest;
        try {
            manifest = readJson(manifestPath);
        } catch (IOException e) {
            throw new IOException("Failed to read launcher profile " + manifestPath, e);
        }

        List<JsonObject> result = new ArrayList<>();
        JsonPrimitive inheritsFrom = manifest.getAsJsonPrimitive("inheritsFrom");
        if (inheritsFrom != null) {
            result.addAll(loadVersionManifests(versionsDir, inheritsFrom.getAsString()));
        }

        result.add(manifest);

        return result;
    }

    private static JsonObject readJson(File file) throws IOException {
        try (InputStream stream = new BufferedInputStream(new FileInputStream(file))) {
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private boolean injectProfile(File target) {
        try {
            JsonObject json;
            try {
                json = readJson(target);
            } catch (IOException e) {
                error("Failed to read " + target);
                e.printStackTrace();
                return false;
            }

            JsonObject _profiles = json.getAsJsonObject("profiles");
            if (_profiles == null) {
                _profiles = new JsonObject();
                json.add("profiles", _profiles);
            }

            JsonObject _profile = _profiles.getAsJsonObject(profile.getProfile());
            if (_profile == null) {
                _profile = new JsonObject();
                _profile.addProperty("name", profile.getProfile());
                _profile.addProperty("type", "custom");
                _profiles.add(profile.getProfile(), _profile);
            }
            _profile.addProperty("lastVersionId", profile.getVersion());
            String icon = profile.getIcon();
            if (icon != null)
                _profile.addProperty("icon", icon);
            String jstring = Util.GSON.toJson(json);
            Files.write(target.toPath(), jstring.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            error("There was a problem writing the launch profile,  is it write protected?");
            return false;
        }
        return true;
    }

    @Override
    public TargetValidator getTargetValidator() {
        return TargetValidator.shouldExist(true)
                .and(TargetValidator.isDirectory())
                .and(TargetValidator.isMCInstallationDirectory());
    }

    @Override
    public TranslatedMessage getSuccessMessage() {
        if (downloadedCount() > 0) {
            return new TranslatedMessage("installer.action.install.client.finished.withlibs", profile.getProfile(), profile.getVersion(), downloadedCount());
        }
        return new TranslatedMessage("installer.action.install.client.finished.withoutlibs", profile.getProfile(), profile.getVersion());
    }
}
