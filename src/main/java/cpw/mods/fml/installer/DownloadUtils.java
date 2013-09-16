package cpw.mods.fml.installer;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.List;
import java.util.Random;
import java.util.jar.JarOutputStream;
import java.util.jar.Pack200;

import javax.swing.ProgressMonitor;

import LZMA.LzmaInputStream;
import argo.jdom.JsonNode;

import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import com.google.common.io.Files;
import com.google.common.io.InputSupplier;

public class DownloadUtils {

    private static final String PACK_NAME = ".pack.lzma";
    public static int downloadInstalledLibraries(String jsonMarker, File librariesDir, IMonitor monitor, List<JsonNode> libraries, int progress, List<String> grabbed, List<String> bad)
    {
        Random r = new Random();
        for (JsonNode library : libraries)
        {
            String libName = library.getStringValue("name");
            String sha1Checksum = library.isStringValue("sha1Checksum") ? library.getStringValue("sha1Checksum") : null;
            monitor.setNote(String.format("Considering library %s", libName));
            if (library.isBooleanValue(jsonMarker) && library.getBooleanValue(jsonMarker))
            {
                String[] nameparts = Iterables.toArray(Splitter.on(':').split(libName), String.class);
                nameparts[0] = nameparts[0].replace('.', '/');
                String jarName = nameparts[1] + '-' + nameparts[2] + ".jar";
                String pathName = nameparts[0] + '/' + nameparts[1] + '/' + nameparts[2] + '/' + jarName;
                File libPath = new File(librariesDir, pathName.replace('/', File.separatorChar));
                String libURL = "https://s3.amazonaws.com/Minecraft.Download/libraries/";
                if (library.isArrayNode("urls"))
                {
                    List<JsonNode> urls = library.getArrayNode("urls");
                    int count = urls.size();
                    libURL = urls.get(r.nextInt(count)).getText();
                }
                else if (library.isStringValue("url"))
                {
                    libURL = library.getStringValue("url") + "/";
                }
                if (libPath.exists() && checksumValid(libPath, sha1Checksum))
                {
                    monitor.setProgress(progress++);
                    continue;
                }

                libPath.getParentFile().mkdirs();
                monitor.setNote(String.format("Downloading library %s", libName));
                libURL += pathName;
                File packFile = new File(libPath.getParentFile(), libName + PACK_NAME);
                if (!downloadFile(libName, packFile, libURL + PACK_NAME, null))
                {
                    monitor.setNote("Failed to locate packed library, trying unpacked");
                    if (!downloadFile(libName, libPath, libURL, sha1Checksum))
                    {
                        bad.add(libName);
                    }
                }
                else
                {
                    try
                    {
                        monitor.setNote(String.format("Unpacking packed file %s",packFile.getName()));
                        FileOutputStream jarBytes = new FileOutputStream(libPath);
                        JarOutputStream jos = new JarOutputStream(jarBytes);
                        LzmaInputStream decompressedPackFile = new LzmaInputStream(new FileInputStream(packFile));
                        Pack200.newUnpacker().unpack(decompressedPackFile, jos);
                        monitor.setNote(String.format("Successfully unpacked packed file %s",packFile.getName()));
                        packFile.delete();
                        if (checksumValid(libPath, sha1Checksum))
                        {
                            grabbed.add(libName);
                        }
                        else
                        {
                            bad.add(libName);
                        }
                    }
                    catch (Exception e)
                    {
                        bad.add(libName);
                    }
                }
            }
            monitor.setProgress(progress++);
        }
        return progress;
    }

    private static boolean checksumValid(File libPath, String sha1Checksum)
    {
        try
        {
            return sha1Checksum == null || Hashing.sha1().hashBytes(Files.toByteArray(libPath)).toString().equalsIgnoreCase(sha1Checksum);
        }
        catch (IOException e)
        {
            return false;
        }
    }

    public static boolean downloadFile(String libName, File libPath, String libURL, String checksum)
    {
        try
        {
            URL url = new URL(libURL);
            URLConnection connection = url.openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            InputSupplier<InputStream> urlSupplier = new URLISSupplier(connection);
            Files.copy(urlSupplier, libPath);
            if (checksumValid(libPath, checksum))
            {
                return true;
            }
            else
            {
                return false;
            }
        }
        catch (Exception e)
        {
            return false;
        }
    }
    static class URLISSupplier implements InputSupplier<InputStream> {
        private final URLConnection connection;

        private URLISSupplier(URLConnection connection)
        {
            this.connection = connection;
        }

        @Override
        public InputStream getInput() throws IOException
        {
            return connection.getInputStream();
        }
    }
    public static IMonitor buildMonitor()
    {
        if (ServerInstall.headless)
        {
            return new IMonitor()
            {

                @Override
                public void setMaximum(int max)
                {
                }

                @Override
                public void setNote(String note)
                {
                    System.out.println("MESSAGE: "+ note);
                }

                @Override
                public void setProgress(int progress)
                {

                }

                @Override
                public void close()
                {

                }

            };
        }
        else
        {
            return new IMonitor() {
                private ProgressMonitor monitor;
                {
                    monitor = new ProgressMonitor(null, "Downloading libraries", "Libraries are being analyzed", 0, 1);
                    monitor.setMillisToPopup(0);
                    monitor.setMillisToDecideToPopup(0);
                }
                @Override
                public void setMaximum(int max)
                {
                    monitor.setMaximum(max);
                }

                @Override
                public void setNote(String note)
                {
                    monitor.setNote(note);
                }

                @Override
                public void setProgress(int progress)
                {
                    monitor.setProgress(progress);
                }

                @Override
                public void close()
                {
                    monitor.close();
                }
            };
        }
    }
}