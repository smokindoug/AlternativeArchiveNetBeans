/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package name.dougmcneil.altarchivetypes;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileStateInvalidException;
import org.openide.filesystems.FileSystem;
import org.openide.filesystems.URLMapper;
import org.openide.util.Exceptions;
import org.openide.util.NbBundle;
import org.openide.util.Parameters;

/**
 *
 * @author doug
 */
public final class FileUtil  {

    private static final Logger LOG = Logger.getLogger(FileUtil.class.getName());

    /** Cache for {@link #isArchiveFile(FileObject)}. */
    private static final Map<FileObject, Boolean> archiveFileCache =
            new WeakHashMap<FileObject,Boolean>();

    /**
     * Get an appropriate display name for a file object.
     * If the file corresponds to a path on disk, this will be the disk path.
     * Otherwise the name will mention the filesystem name or archive name in case
     * the file comes from archive and relative path. Relative path will be mentioned
     * just in case that passed <code>FileObject</code> isn't root {@link FileObject#isRoot}.
     *
     * @param fo a file object
     * @return a display name indicating where the file is
     * @since 4.39
     */
    public static String getFileDisplayName(FileObject fo) {
        String displayName = null;
        File f = org.openide.filesystems.FileUtil.toFile(fo);

        if (f != null) {
            displayName = f.getAbsolutePath();
        } else {
            FileObject archiveFile = FileUtil.getAltArchiveFile(fo);

            if (archiveFile != null) {
                displayName = getAltArchiveDisplayName(fo, archiveFile);
            }
        }

        if (displayName == null) {
            try {
                if (fo.isRoot()) {
                    displayName = fo.getFileSystem().getDisplayName();
                } else {
                    displayName = NbBundle.getMessage(
                            org.openide.filesystems.FileUtil.class,
                            "LBL_file_in_filesystem",
                            fo.getPath(),
                            fo.getFileSystem().getDisplayName()
                        );
                }
            } catch (FileStateInvalidException e) {
                // Not relevant now, just use the simple path.
                displayName = fo.getPath();
            }
        }

        return displayName;
    }

    private static String getAltArchiveDisplayName(FileObject fo, FileObject archiveFile) {
        String displayName = null;

        File f = org.openide.filesystems.FileUtil.toFile(archiveFile);

        if (f != null) {
            String archivDisplayName = f.getAbsolutePath();

            if (fo.isRoot()) {
                displayName = archivDisplayName;
            } else {
                String entryPath = fo.getPath();
                displayName = NbBundle.getMessage(
                        org.openide.filesystems.FileUtil.class,
                        "LBL_file_in_filesystem", entryPath, archivDisplayName
                    );
            }
        }

        return displayName;
    }

    /**
     * Returns a FileObject representing the root folder of an archive.
     * Clients may need to first call {@link #isArchiveFile(FileObject)} to determine
     * if the file object refers to an archive file.
     * @param fo a ZIP- (or JAR-) format archive file
     * @return a virtual archive root folder, or null if the file is not actually an archive
     * @since 4.48
     */
    public static FileObject getAltArchiveRoot(FileObject fo) {
        URL archiveURL = URLMapper.findURL(fo, URLMapper.EXTERNAL);

        if (archiveURL == null) {
            return null;
        }

        return URLMapper.findFileObject(getAltArchiveRoot(archiveURL));
    }

    /**
     * Returns a URL representing the root of an archive.
     * Clients may need to first call {@link #isArchiveFile(URL)} to determine if the URL
     * refers to an archive file.
     * @param url of a ZIP- (or JAR-) format archive file
     * @return the <code>aar</code>-protocol URL of the root of the archive
     * @since 4.48
     */
    public static URL getAltArchiveRoot(URL url) {
        try {
            // XXX TBD whether the url should ever be escaped...
            URL aar = new URL(AltArchiveURLMapper.ALTARCHIVE_PROTOCOL, url.getHost(), url.getPort(), ((url.getProtocol().equals(AltArchiveURLMapper.ALTARCHIVE_PROTOCOL)) ? AltArchiveURLMapper.ALTARCHIVE_PROTOCOL + ":" : "file:") + url.getFile() + "!/",
                    new AARURLStreamHandler());
            return aar;
            //return new URL("aar:" + url + "!/"); // NOI18N
        } catch (MalformedURLException e) {
            throw new AssertionError(e);
        }
    }

    /**
     * Returns a FileObject representing an archive file containing the
     * FileObject given by the parameter.
     * <strong>Remember</strong> that any path within the archive is discarded
     * so you may need to check for non-root entries.
     * @param fo a file in a JAR filesystem
     * @return the file corresponding to the archive itself,
     *         or null if <code>fo</code> is not an archive entry
     * @since 4.48
     */
    public static FileObject getAltArchiveFile(FileObject fo) {
        Parameters.notNull("fo", fo);   //NOI18N
        try {
            FileSystem fs = fo.getFileSystem();

            if (fs instanceof AltArchiveFileSystem) {
                File aarFile = ((AltArchiveFileSystem) fs).getAltArchiveFile();

                return org.openide.filesystems.FileUtil.toFileObject(aarFile);
            }
        } catch (FileStateInvalidException e) {
            Exceptions.printStackTrace(e);
        }

        return null;
    }

    /**
     * Returns the URL of the archive file containing the file
     * referred to by a <code>aar</code>-protocol URL.
     * <strong>Remember</strong> that any path within the archive is discarded
     * so you may need to check for non-root entries.
     * @param url a URL
     * @return the embedded archive URL, or null if the URL is not a
     *         <code>aar</code>-protocol URL containing <code>!/</code>
     * @since 4.48
     */
    public static URL getAltArchiveFile(URL url) {
        String protocol = url.getProtocol();

        if (AltArchiveURLMapper.ALTARCHIVE_PROTOCOL.equals(protocol)) { //NOI18N

            String path = url.getPath();
            // secret in path
            int index = path.indexOf("!/"); //NOI18N

            if (index >= 0) {
                String aarPath = null;
                try {
                    aarPath = path.substring(0, index);
                    if (aarPath.indexOf("file://") > -1 && aarPath.indexOf("file:////") == -1) {  //NOI18N
                        /* Replace because JDK application classloader wrongly recognizes UNC paths. */
                        aarPath = aarPath.replaceFirst("file://", "file:////");  //NOI18N
                    }
                    return new URL(aarPath);

                } catch (MalformedURLException mue) {
                    Exceptions.printStackTrace(Exceptions.attachMessage(mue,
                    "URL: " + url.toExternalForm() +" aarPath: " + aarPath));   //NOI18N
                }
            }
        }

        return null;
    }

    /**
     * Tests if a file represents a JAR or ZIP archive.
     * @param fo the file to be tested
     * @return true if the file looks like a ZIP-format archive
     * @since 4.48
     */
    public static boolean isAltArchiveFile(FileObject fo) {
        Parameters.notNull("fileObject", fo);  //NOI18N

        if (!fo.isValid()) {
            return isAltArchiveFile(fo.getPath());
        }
        // XXX Special handling of virtual file objects: try to determine it using its name, but don't cache the
        // result; when the file is checked out the more correct method can be used
        if (fo.isVirtual()) {
            return isAltArchiveFile(fo.getPath());
        }

        if (fo.isFolder()) {
            return false;
        }

        // First check the cache.
        Boolean b = archiveFileCache.get(fo);

        if (b == null) {
            try {
                b = Boolean.valueOf(new AltArchiveFile(fo.getInputStream(),
                            "#tmp#"
                        ).isAltArchive());
            } catch (IOException ex) {
                Exceptions.printStackTrace(ex);
            }
            if (b == null) {
                b = isAltArchiveFile(fo.getPath());
            }

            archiveFileCache.put(fo, b);
        }

        return b.booleanValue();
    }

    /**
     * Tests if a URL represents a JAR or ZIP archive.
     * If there is no such file object, the test is done by heuristic: any URL with an extension is
     * treated as an archive.
     * @param url a URL to a file
     * @return true if the URL seems to represent a ZIP-format archive
     * @since 4.48
     */
    public static boolean isAltArchiveFile(URL url) {
        Parameters.notNull("url", url);  //NOI18N

        if (AltArchiveURLMapper.ALTARCHIVE_PROTOCOL.equals(url.getProtocol())) { //NOI18N

            //Already inside archive, return false
            return false;
        }

        FileObject fo = URLMapper.findFileObject(url);

        if ((fo != null) && !fo.isVirtual()) {
            if (LOG.isLoggable(Level.FINEST)) {
                LOG.log(Level.FINEST, "isArchiveFile_FILE_RESOLVED", fo); //NOI18N, used by FileUtilTest.testIsArchiveFileRace
            }
            return isAltArchiveFile(fo);
        } else {
            return isAltArchiveFile(url.getPath());
        }
    }

    /**
     * Convert a file such as would be shown in a classpath entry into a proper folder URL.
     * If the file looks to represent a directory, a <code>file</code> URL will be created.
     * If it looks to represent a ZIP archive, a <code>aar</code> URL will be created.
     * @param entry a file or directory name
     * @return an appropriate classpath URL which will always end in a slash (<samp>/</samp>),
     *         or null for an existing file which does not look like a valid archive
     * @since org.openide.filesystems 7.8
     */
    public static URL urlForArchiveOrDir(File entry) {
        try {
            URL u = entry.toURI().toURL();
            if (isAltArchiveFile(u) || entry.isFile() && entry.length() < 4) {
                return getAltArchiveRoot(u);
            } else if (entry.isDirectory()) {
                return u;
            } else if (!entry.exists()) {
                if (!u.toString().endsWith("/")) {
                    u = new URL(u + "/"); // NOI18N
                }
                return u;
            } else {
                return null;
            }
        } catch (MalformedURLException x) {
            assert false : x;
            return null;
        }
    }

    /**
     * Convert a classpath-type URL to a corresponding file.
     * If it is a <code>aar</code> URL representing the root folder of a local disk archive,
     * that archive file will be returned.
     * If it is a <code>file</code> URL representing a local disk folder,
     * that folder will be returned.
     * @param entry a classpath entry or similar URL
     * @return a corresponding file, or null for e.g. a network URL or non-root JAR folder entry
     * @since org.openide.filesystems 7.8
     */
    public static File archiveOrDirForURL(URL entry) {
        String u = entry.toString();
        if (u.startsWith(AltArchiveURLMapper.ALTARCHIVE_PROTOCOL + ":file:") && u.endsWith("!/")) { // NOI18N
            return new File(URI.create(u.substring(4, u.length() - 2)));
        } else if (u.startsWith("file:")) { // NOI18N
            return new File(URI.create(u));
        } else {
            return null;
        }
    }

    /**
     * Tests if a non existent path represents a file.
     * @param path to be tested, separated by '/'.
     * @return true if the file has '.' after last '/'.
     */
    private static boolean isAltArchiveFile (final String path) {
        int index = path.lastIndexOf('.');  //NOI18N
        return (index != -1) && (index > path.lastIndexOf('/') + 1);    //NOI18N
    }
}
