package org.robolectric.res;

import org.robolectric.util.Join;
import org.robolectric.util.Util;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.TreeMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import static java.util.Arrays.asList;

abstract public class Fs {
    public static Fs fromJar(URL url) {
        return new JarFs(new File(url.getFile()));
    }

    public static FsFile fileFromJar(String urlString) {
        URI uri = URI.create(urlString);
        if (uri.getScheme().equals("jar")) {
            String[] parts = uri.getPath().split("!");
            try {
                Fs fs = fromJar(URI.create("file:" + parts[0]).toURL());
                return fs.join(parts[1].substring(1));
            } catch (MalformedURLException e) {
                throw new RuntimeException(e);
            }
        } else {
            throw new RuntimeException("not sure what to do with " + urlString);
        }
    }

    public static FsFile newFile(File file) {
        return new FileFsFile(file);
    }

    private static class JarFs extends Fs {
        private final JarFile jarFile;
        private final NavigableMap<String, JarEntry> jarEntryMap = new TreeMap<String, JarEntry>();

        public JarFs(File file) {
            try {
                jarFile = new JarFile(file);
                Enumeration<JarEntry> entries = jarFile.entries();
                while (entries.hasMoreElements()) {
                    JarEntry jarEntry = entries.nextElement();
                    jarEntryMap.put(jarEntry.getName(), jarEntry);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override public FsFile join(String folderBaseName) {
            return new JarFsFile(folderBaseName);
        }

        private class JarFsFile implements FsFile {
            private final String path;

            public JarFsFile(String path) {
                this.path = path;
            }

            @Override public boolean exists() {
                return isFile() || isDirectory();
            }

            @Override public boolean isDirectory() {
                return jarEntryMap.containsKey(path + "/");
            }

            @Override public boolean isFile() {
                return jarEntryMap.containsKey(path);
            }

            @Override public FsFile[] listFiles() {
                if (!isDirectory()) return null;
                NavigableSet<String> strings = jarEntryMap.navigableKeySet().subSet(path + "/", false, path + "0", false);
                List<FsFile> fsFiles = new ArrayList<FsFile>();
                int startOfFilename = path.length() + 2;
                for (String string : strings) {
                    int nextSlash = string.indexOf('/', startOfFilename);
                    if (nextSlash == string.length() - 1) {
                        // directory entry
                        fsFiles.add(new JarFsFile(string.substring(0, string.length() - 1)));
                    } else if (nextSlash == -1) {
                        // file entry
                        fsFiles.add(new JarFsFile(string));
                    }
                }
                return fsFiles.toArray(new FsFile[fsFiles.size()]);
            }

            @Override public FsFile[] listFiles(Filter filter) {
                List<FsFile> filteredFsFiles = new ArrayList<FsFile>();
                for (FsFile fsFile : listFiles()) {
                    if (filter.accept(fsFile)) {
                        filteredFsFiles.add(fsFile);
                    }
                }
                return filteredFsFiles.toArray(new FsFile[filteredFsFiles.size()]);
            }

            @Override public String[] listFileNames() {
                List<String> fileNames = new ArrayList<String>();
                for (FsFile fsFile : listFiles()) {
                    fileNames.add(fsFile.getName());
                }
                return fileNames.toArray(new String[fileNames.size()]);
            }

            @Override public FsFile getParent() {
                String[] parts = path.split("\\/");
                return new JarFsFile(Join.join("/", asList(parts).subList(0, parts.length - 1)));
            }

            @Override public String getName() {
                String[] parts = path.split("\\/");
                return parts[parts.length - 1];
            }

            @Override public InputStream getInputStream() throws IOException {
                return jarFile.getInputStream(jarEntryMap.get(path));
            }

            @Override public byte[] getBytes() throws IOException {
                return Util.readBytes(getInputStream());
            }

            @Override public FsFile join(String... pathParts) {
                return new JarFsFile(path + "/" + Join.join("/", asList(pathParts)));
            }

            @Override public String getBaseName() {
                String name = getName();
                int dotIndex = name.indexOf(".");
                return dotIndex >= 0 ? name.substring(0, dotIndex) : name;
            }

            @Override public String getPath() {
                return "jar:" + getJarFileName() + "!/" + path;
            }

            @Override
            public boolean equals(Object o) {
                if (this == o) return true;
                if (o == null || getClass() != o.getClass()) return false;

                JarFsFile jarFsFile = (JarFsFile) o;

                if (!getJarFileName().equals(jarFsFile.getJarFileName())) return false;
                if (!path.equals(jarFsFile.path)) return false;

                return true;
            }

            private String getJarFileName() {
                return jarFile.getName();
            }

            @Override
            public int hashCode() {
                return getJarFileName().hashCode() * 31 + path.hashCode();
            }

            @Override public String toString() {
                return getPath();
            }
        }
    }

    abstract public FsFile join(String folderBaseName);
}
