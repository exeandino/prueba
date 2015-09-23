package org.apache.cordova.file;

import android.net.Uri;
import android.util.Base64;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.file.Filesystem.ReadFileCallback;
import org.apache.cordova.globalization.Globalization;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class LocalFilesystem extends Filesystem {
    private CordovaInterface cordova;
    private String fsRoot;

    public LocalFilesystem(String name, CordovaInterface cordova, String fsRoot) {
        this.name = name;
        this.fsRoot = fsRoot;
        this.cordova = cordova;
    }

    public String filesystemPathForFullPath(String fullPath) {
        String path = new File(this.fsRoot, fullPath).toString();
        int questionMark = path.indexOf("?");
        if (questionMark >= 0) {
            path = path.substring(0, questionMark);
        }
        if (path.length() <= 1 || !path.endsWith("/")) {
            return path;
        }
        return path.substring(0, path.length() - 1);
    }

    public String filesystemPathForURL(LocalFilesystemURL url) {
        return filesystemPathForFullPath(url.fullPath);
    }

    private String fullPathForFilesystemPath(String absolutePath) {
        if (absolutePath == null || !absolutePath.startsWith(this.fsRoot)) {
            return null;
        }
        return absolutePath.substring(this.fsRoot.length());
    }

    protected LocalFilesystemURL URLforFullPath(String fullPath) {
        if (fullPath == null) {
            return null;
        }
        if (fullPath.startsWith("/")) {
            return new LocalFilesystemURL("cdvfile://localhost/" + this.name + fullPath);
        }
        return new LocalFilesystemURL("cdvfile://localhost/" + this.name + "/" + fullPath);
    }

    public LocalFilesystemURL URLforFilesystemPath(String path) {
        return URLforFullPath(fullPathForFilesystemPath(path));
    }

    protected String normalizePath(String rawPath) {
        boolean isAbsolutePath = rawPath.startsWith("/");
        if (isAbsolutePath) {
            rawPath = rawPath.substring(1);
        }
        ArrayList<String> components = new ArrayList(Arrays.asList(rawPath.split("/+")));
        int index = 0;
        while (index < components.size()) {
            if (((String) components.get(index)).equals("..")) {
                components.remove(index);
                if (index > 0) {
                    components.remove(index - 1);
                    index--;
                }
            }
            index++;
        }
        StringBuilder normalizedPath = new StringBuilder();
        Iterator i$ = components.iterator();
        while (i$.hasNext()) {
            String component = (String) i$.next();
            normalizedPath.append("/");
            normalizedPath.append(component);
        }
        if (isAbsolutePath) {
            return normalizedPath.toString();
        }
        return normalizedPath.toString().substring(1);
    }

    public JSONObject makeEntryForFile(File file) throws JSONException {
        String path = fullPathForFilesystemPath(file.getAbsolutePath());
        if (path != null) {
            return Filesystem.makeEntryForPath(path, this.name, Boolean.valueOf(file.isDirectory()), Uri.fromFile(file).toString());
        }
        return null;
    }

    public JSONObject getEntryForLocalURL(LocalFilesystemURL inputURL) throws IOException {
        File fp = new File(filesystemPathForURL(inputURL));
        if (!fp.exists()) {
            throw new FileNotFoundException();
        } else if (fp.canRead()) {
            try {
                return Filesystem.makeEntryForURL(inputURL, Boolean.valueOf(fp.isDirectory()), Uri.fromFile(fp).toString());
            } catch (JSONException e) {
                throw new IOException();
            }
        } else {
            throw new IOException();
        }
    }

    public JSONObject getFileForLocalURL(LocalFilesystemURL inputURL, String path, JSONObject options, boolean directory) throws FileExistsException, IOException, TypeMismatchException, EncodingException, JSONException {
        boolean create = false;
        boolean exclusive = false;
        if (options != null) {
            create = options.optBoolean("create");
            if (create) {
                exclusive = options.optBoolean("exclusive");
            }
        }
        if (path.contains(":")) {
            throw new EncodingException("This path has an invalid \":\" in it.");
        }
        LocalFilesystemURL requestedURL;
        if (path.startsWith("/")) {
            requestedURL = URLforFilesystemPath(path);
        } else {
            requestedURL = URLforFullPath(normalizePath(inputURL.fullPath + "/" + path));
        }
        File fp = new File(filesystemPathForURL(requestedURL));
        if (create) {
            if (exclusive && fp.exists()) {
                throw new FileExistsException("create/exclusive fails");
            }
            if (directory) {
                fp.mkdir();
            } else {
                fp.createNewFile();
            }
            if (!fp.exists()) {
                throw new FileExistsException("create fails");
            }
        } else if (!fp.exists()) {
            throw new FileNotFoundException("path does not exist");
        } else if (directory) {
            if (fp.isFile()) {
                throw new TypeMismatchException("path doesn't exist or is file");
            }
        } else if (fp.isDirectory()) {
            throw new TypeMismatchException("path doesn't exist or is directory");
        }
        return Filesystem.makeEntryForPath(requestedURL.fullPath, requestedURL.filesystemName, Boolean.valueOf(directory), Uri.fromFile(fp).toString());
    }

    public boolean removeFileAtLocalURL(LocalFilesystemURL inputURL) throws InvalidModificationException {
        File fp = new File(filesystemPathForURL(inputURL));
        if (!fp.isDirectory() || fp.list().length <= 0) {
            return fp.delete();
        }
        throw new InvalidModificationException("You can't delete a directory that is not empty.");
    }

    public boolean recursiveRemoveFileAtLocalURL(LocalFilesystemURL inputURL) throws FileExistsException {
        return removeDirRecursively(new File(filesystemPathForURL(inputURL)));
    }

    protected boolean removeDirRecursively(File directory) throws FileExistsException {
        if (directory.isDirectory()) {
            for (File file : directory.listFiles()) {
                removeDirRecursively(file);
            }
        }
        if (directory.delete()) {
            return true;
        }
        throw new FileExistsException("could not delete: " + directory.getName());
    }

    public JSONArray readEntriesAtLocalURL(LocalFilesystemURL inputURL) throws FileNotFoundException {
        File fp = new File(filesystemPathForURL(inputURL));
        if (fp.exists()) {
            JSONArray entries = new JSONArray();
            if (fp.isDirectory()) {
                File[] files = fp.listFiles();
                for (int i = 0; i < files.length; i++) {
                    if (files[i].canRead()) {
                        try {
                            entries.put(Filesystem.makeEntryForPath(fullPathForFilesystemPath(files[i].getAbsolutePath()), inputURL.filesystemName, Boolean.valueOf(files[i].isDirectory()), Uri.fromFile(files[i]).toString()));
                        } catch (JSONException e) {
                        }
                    }
                }
            }
            return entries;
        }
        throw new FileNotFoundException();
    }

    public JSONObject getFileMetadataForLocalURL(LocalFilesystemURL inputURL) throws FileNotFoundException {
        File file = new File(filesystemPathForURL(inputURL));
        if (file.exists()) {
            JSONObject metadata = new JSONObject();
            try {
                metadata.put("size", file.isDirectory() ? 0 : file.length());
                metadata.put(Globalization.TYPE, FileHelper.getMimeType(file.getAbsolutePath(), this.cordova));
                metadata.put("name", file.getName());
                metadata.put("fullPath", inputURL.fullPath);
                metadata.put("lastModifiedDate", file.lastModified());
                return metadata;
            } catch (JSONException e) {
                return null;
            }
        }
        throw new FileNotFoundException("File at " + inputURL.URL + " does not exist.");
    }

    private boolean isCopyOnItself(String src, String dest) {
        if (!dest.startsWith(src) || dest.indexOf(File.separator, src.length() - 1) == -1) {
            return false;
        }
        return true;
    }

    private JSONObject copyFile(File srcFile, File destFile) throws IOException, InvalidModificationException, JSONException {
        if (destFile.exists() && destFile.isDirectory()) {
            throw new InvalidModificationException("Can't rename a file to a directory");
        }
        copyAction(srcFile, destFile);
        return makeEntryForFile(destFile);
    }

    private void copyAction(File srcFile, File destFile) throws FileNotFoundException, IOException {
        FileInputStream istream = new FileInputStream(srcFile);
        FileOutputStream ostream = new FileOutputStream(destFile);
        FileChannel input = istream.getChannel();
        FileChannel output = ostream.getChannel();
        try {
            input.transferTo(0, input.size(), output);
        } finally {
            istream.close();
            ostream.close();
            input.close();
            output.close();
        }
    }

    private JSONObject copyDirectory(File srcDir, File destinationDir) throws JSONException, IOException, NoModificationAllowedException, InvalidModificationException {
        if (destinationDir.exists() && destinationDir.isFile()) {
            throw new InvalidModificationException("Can't rename a file to a directory");
        } else if (isCopyOnItself(srcDir.getAbsolutePath(), destinationDir.getAbsolutePath())) {
            throw new InvalidModificationException("Can't copy itself into itself");
        } else if (destinationDir.exists() || destinationDir.mkdir()) {
            for (File file : srcDir.listFiles()) {
                File destination = new File(destinationDir.getAbsoluteFile() + File.separator + file.getName());
                if (file.isDirectory()) {
                    copyDirectory(file, destination);
                } else {
                    copyFile(file, destination);
                }
            }
            return makeEntryForFile(destinationDir);
        } else {
            throw new NoModificationAllowedException("Couldn't create the destination directory");
        }
    }

    private JSONObject moveFile(File srcFile, File destFile) throws IOException, JSONException, InvalidModificationException {
        if (destFile.exists() && destFile.isDirectory()) {
            throw new InvalidModificationException("Can't rename a file to a directory");
        }
        if (!srcFile.renameTo(destFile)) {
            copyAction(srcFile, destFile);
            if (destFile.exists()) {
                srcFile.delete();
            } else {
                throw new IOException("moved failed");
            }
        }
        return makeEntryForFile(destFile);
    }

    private JSONObject moveDirectory(File srcDir, File destinationDir) throws IOException, JSONException, InvalidModificationException, NoModificationAllowedException, FileExistsException {
        if (destinationDir.exists() && destinationDir.isFile()) {
            throw new InvalidModificationException("Can't rename a file to a directory");
        } else if (isCopyOnItself(srcDir.getAbsolutePath(), destinationDir.getAbsolutePath())) {
            throw new InvalidModificationException("Can't move itself into itself");
        } else if (!destinationDir.exists() || destinationDir.list().length <= 0) {
            if (!srcDir.renameTo(destinationDir)) {
                copyDirectory(srcDir, destinationDir);
                if (destinationDir.exists()) {
                    removeDirRecursively(srcDir);
                } else {
                    throw new IOException("moved failed");
                }
            }
            return makeEntryForFile(destinationDir);
        } else {
            throw new InvalidModificationException("directory is not empty");
        }
    }

    public JSONObject copyFileToURL(LocalFilesystemURL destURL, String newName, Filesystem srcFs, LocalFilesystemURL srcURL, boolean move) throws IOException, InvalidModificationException, JSONException, NoModificationAllowedException, FileExistsException {
        if (!new File(filesystemPathForURL(destURL)).exists()) {
            throw new FileNotFoundException("The source does not exist");
        } else if (!LocalFilesystem.class.isInstance(srcFs)) {
            return super.copyFileToURL(destURL, newName, srcFs, srcURL, move);
        } else {
            LocalFilesystemURL destinationURL = makeDestinationURL(newName, srcURL, destURL);
            File sourceFile = new File(srcFs.filesystemPathForURL(srcURL));
            File destinationFile = new File(filesystemPathForURL(destinationURL));
            if (!sourceFile.exists()) {
                throw new FileNotFoundException("The source does not exist");
            } else if (sourceFile.getAbsolutePath().equals(destinationFile.getAbsolutePath())) {
                throw new InvalidModificationException("Can't copy a file onto itself");
            } else if (sourceFile.isDirectory()) {
                if (move) {
                    return moveDirectory(sourceFile, destinationFile);
                }
                return copyDirectory(sourceFile, destinationFile);
            } else if (move) {
                return moveFile(sourceFile, destinationFile);
            } else {
                return copyFile(sourceFile, destinationFile);
            }
        }
    }

    public void readFileAtURL(LocalFilesystemURL inputURL, long start, long end, ReadFileCallback readFileCallback) throws IOException {
        File file = new File(filesystemPathForURL(inputURL));
        String contentType = FileHelper.getMimeTypeForExtension(file.getAbsolutePath());
        if (end < 0) {
            end = file.length();
        }
        long numBytesToRead = end - start;
        InputStream rawInputStream = new FileInputStream(file);
        if (start > 0) {
            try {
                rawInputStream.skip(start);
            } catch (Throwable th) {
                rawInputStream.close();
            }
        }
        readFileCallback.handleData(new LimitedInputStream(rawInputStream, numBytesToRead), contentType);
        rawInputStream.close();
    }

    public long writeToFileAtURL(LocalFilesystemURL inputURL, String data, int offset, boolean isBinary) throws IOException, NoModificationAllowedException {
        byte[] rawData;
        FileOutputStream out;
        boolean append = false;
        if (offset > 0) {
            truncateFileAtURL(inputURL, (long) offset);
            append = true;
        }
        if (isBinary) {
            rawData = Base64.decode(data, 0);
        } else {
            rawData = data.getBytes();
        }
        ByteArrayInputStream in = new ByteArrayInputStream(rawData);
        try {
            byte[] buff = new byte[rawData.length];
            out = new FileOutputStream(filesystemPathForURL(inputURL), append);
            in.read(buff, 0, buff.length);
            out.write(buff, 0, rawData.length);
            out.flush();
            out.close();
            return (long) rawData.length;
        } catch (NullPointerException e) {
            throw new NoModificationAllowedException(inputURL.toString());
        } catch (Throwable th) {
            out.close();
        }
    }

    public long truncateFileAtURL(LocalFilesystemURL inputURL, long size) throws IOException {
        if (new File(filesystemPathForURL(inputURL)).exists()) {
            RandomAccessFile raf = new RandomAccessFile(filesystemPathForURL(inputURL), "rw");
            try {
                if (raf.length() >= size) {
                    raf.getChannel().truncate(size);
                } else {
                    size = raf.length();
                    raf.close();
                }
                return size;
            } finally {
                raf.close();
            }
        } else {
            throw new FileNotFoundException("File at " + inputURL.URL + " does not exist.");
        }
    }

    public boolean canRemoveFileAtLocalURL(LocalFilesystemURL inputURL) {
        return new File(filesystemPathForURL(inputURL)).exists();
    }

    OutputStream getOutputStreamForURL(LocalFilesystemURL inputURL) throws FileNotFoundException {
        return new FileOutputStream(new File(filesystemPathForURL(inputURL)));
    }
}
