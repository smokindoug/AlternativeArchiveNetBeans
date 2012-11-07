/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package name.dougmcneil.altarchivetypes;

import java.beans.PropertyVetoException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipException;
import org.openide.filesystems.FileSystem;
import org.openide.filesystems.FileChangeAdapter;
import org.openide.filesystems.FileChangeListener;
import org.openide.filesystems.FileEvent;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileRenameEvent;
import org.openide.filesystems.FileUtil;
import org.openide.filesystems.JarFileSystem;
import org.openide.util.NbBundle;
import org.openide.util.RequestProcessor;
import org.openide.util.Utilities;

/**
 * Not sure subclass this is going to do any good...
 * @author doug
 */
public class AltArchiveFileSystem extends JarFileSystem {

    private static final Logger LOGGER = Logger.getLogger(JarFileSystem.class.getName());

    /** One request proccesor shared for all instances of JarFileSystem*/
    private static final RequestProcessor req = new RequestProcessor("AltArchive Fs - modification watcher", 1, false, false); // NOI18N

    /** maxsize for passing ByteArrayInputStream*/
    private static final long MEM_STREAM_SIZE = 100000;

    private transient Object closeSync = new Object();
    private int checkTime = 10000;

    private transient AltArchiveFile altArchive;

    /** Time of request for opening of jar. */
    private transient long openRequestTime = 0;

    /** Cached image of AltArchiveFile capable of answering queries on type and children.
     * There is a strong reference held while there is a living FileObject
     * and a SoftReference for caching after all FOs are freed.*/
    private transient Cache strongCache;

    private transient long lastModification = 0;
    /** Actual time for which closing of jar is postponed. */
    private transient int closeDelay = 300;

    /** The soft part of the cache reference. For simplicity never null*/
    private transient Reference<Cache> softCache = new SoftReference<Cache>(null);
    private transient FileObject foRoot;
    private transient FileChangeListener fcl;

    /** Watches modification on root file */
    private transient RequestProcessor.Task watcherTask = null;
    private transient RequestProcessor.Task closeTask = null;

    /** Archive file.1
    */
    private File root = new File("."); // NOI18N

    /** number of FileObjects in using. If no one is used then the cached data
     * is freed */
    private transient long aliveCount = 0;

    /**
    * Opened zip file of this filesystem is stored here or null.
    */
    private transient AltArchiveFile jar;

    @Override
    public void setJarFile(final File aRoot)
            throws IOException, PropertyVetoException {
        setAltArchiveFile(aRoot, true, true);
    }

    @SuppressWarnings("deprecation") // need to set it for compat
    private void _setSystemName(String s) throws PropertyVetoException {
        setSystemName(s);
    }

    private void setAltArchiveFile(final File aRoot, boolean refreshRoot, boolean openAltArchive)
    throws IOException, PropertyVetoException {
        if (!aRoot.equals(FileUtil.normalizeFile(aRoot))) {
            throw new IllegalArgumentException(
                "Parameter aRoot was not " + // NOI18N
                "normalized. Was " + aRoot + " instead of " + FileUtil.normalizeFile(aRoot)
            ); // NOI18N
        }

        FileObject newRoot = null;
        String oldDisplayName = getDisplayName();

        if (getRefreshTime() > 0) {
            setRefreshTime(0);
        }

        if (aRoot == null) {
            throw new IOException(NbBundle.getMessage(JarFileSystem.class, "EXC_NotValidFile", aRoot));
        }

        if (!aRoot.exists()) {
            throw new IOException(NbBundle.getMessage(JarFileSystem.class, "EXC_FileNotExists", aRoot.getAbsolutePath()));
        }

        if (!aRoot.canRead()) {
            throw new IOException(NbBundle.getMessage(JarFileSystem.class, "EXC_CanntRead", aRoot.getAbsolutePath()));
        }

        if (!aRoot.isFile()) {
            throw new IOException(NbBundle.getMessage(JarFileSystem.class, "EXC_NotValidFile", aRoot.getAbsolutePath()));
        }

        String s;
        s = aRoot.getAbsolutePath();
        s = s.intern();

        AltArchiveFile tempAltArchive = null;

        if (openAltArchive) {
            try {
                tempAltArchive = new AltArchiveFile(s);
                LOGGER.log(Level.FINE, "opened: "+ System.currentTimeMillis()+ "   " + s);//NOI18N
            } catch (ZipException e) {
                throw new IOException(NbBundle.getMessage(JarFileSystem.class, "EXC_NotValidJarFile2", e.getLocalizedMessage(), s));
            }
        }

        synchronized (closeSync) {
            _setSystemName(s);

            closeCurrentRoot(false);
            setAltArchive(tempAltArchive);
            openRequestTime = System.currentTimeMillis();
            root = new File(s);

            if (refreshRoot) {
                strongCache = null;
                softCache.clear();
                aliveCount = 0;
                newRoot = refreshRoot();
                lastModification = 0;

                if (newRoot != null) {
                    firePropertyChange("root", null, newRoot); // NOI18N
                }
            }
        }

        firePropertyChange(PROP_DISPLAY_NAME, oldDisplayName, getDisplayName());

        foRoot = FileUtil.toFileObject(root);

        if ((foRoot != null) && (fcl == null)) {
            fcl = new FileChangeAdapter() {

                @Override
                public void fileChanged(FileEvent fe) {
                    if (watcherTask == null) {
                        parse(true);
                    }
                }

                @Override
                public void fileRenamed(FileRenameEvent fe) {
                    File f = FileUtil.toFile(fe.getFile());

                    if ((f != null) && !f.equals(aRoot)) {
                        try {
                            setAltArchiveFile(f, false, true);
                        } catch (IOException iex) {
                            LOGGER.log(Level.INFO, null, iex);
                        } catch (PropertyVetoException pvex) {
                            LOGGER.log(Level.OFF, null, pvex);
                        }
                    }
                }

                @Override
                public void fileDeleted(FileEvent fe) {
                    Enumeration<? extends FileObject> en = existingFileObjects(getRoot());

                    while (en.hasMoreElements()) {
                        // probably do not need delete event support
                        FileObject fo = (FileObject) en.nextElement();
                       // fo.validFlag = false;
                        //fo.fileDeleted0(new FileEvent(fo));
                    }

                    refreshRoot();
                }
            };

            if (refreshRoot) {
                foRoot.addFileChangeListener(FileUtil.weakFileChangeListener(fcl, foRoot));
            }
        }
    }

    /** Get the file path for the ZIP or JAR file.
    * @return the file path
    */
    @Override
    public File getJarFile() {
        return root;
    }

    /*
    * Provides name of the system that can be presented to the user.
    * @return user presentable name of the filesystem
    */
    @Override
    public String getDisplayName() {
        return root != null ? root.getAbsolutePath() : NbBundle.getMessage(JarFileSystem.class, "JAR_UnknownJar");
    }

    @Override
    protected InputStream inputStream(String name) throws java.io.FileNotFoundException {
        InputStream is = null;

        try {
            synchronized (closeSync) {
                AltArchiveFile j = reOpenAltArchiveFile();

                if (j != null) {
                    AltArchiveFile.AltArchiveEntry je = j.getAltArchiveEntry(name);

                    if (je != null) {
                        if (je.getSize() < MEM_STREAM_SIZE) {
                            is = getMemInputStream(j, je);
                        } else {
                            is = getTemporaryInputStream(j, je, (strongCache != null));
                        }
                    }
                }
            }
        } catch (java.io.FileNotFoundException e) {
            throw e;
        } catch (IOException e) {
            FileNotFoundException fnfe = new FileNotFoundException(root.getAbsolutePath());
            fnfe.initCause(e);
            throw fnfe;
        } catch (RuntimeException e) {
            FileNotFoundException fnfe = new FileNotFoundException(root.getAbsolutePath());
            fnfe.initCause(e);
            throw fnfe;
        } finally {
            closeCurrentRoot(false);
        }

        if (is == null) {
            throw new java.io.FileNotFoundException(name);
        }

        return is;
    }

    /* Creates Reference. In FileSystem, which subclasses AbstractFileSystem, you can overload method
     * createReference(FileObject fo) to achieve another type of Reference (weak, strong etc.)
     * @param fo is FileObject. It`s reference yourequire to get.
     * @return Reference to FileObject
     */
    @Override
    protected <T extends FileObject> Reference<T> createReference(T fo) {
        aliveCount++;

        if ((checkTime > 0) && (watcherTask == null)) {
            watcherTask = req.post(watcherTask(), checkTime);
        }

        return new Ref<T>(fo);
    }

    private InputStream getMemInputStream(AltArchiveFile jf, AltArchiveFile.AltArchiveEntry je)
    throws IOException {
        InputStream is = jf.getInputStream(je);
        ByteArrayOutputStream os = new ByteArrayOutputStream(is.available());

        try {
            FileUtil.copy(is, os);
        } finally {
            os.close();
        }

        return new ByteArrayInputStream(os.toByteArray());
    }

    private void freeReference() {
        aliveCount--;

        // Nobody uses this JarFileSystem => stop watcher, close JarFile and throw away cache.
        if (aliveCount == 0) {
            if (watcherTask != null) {
                watcherTask.cancel();
                watcherTask = null;
            }

            strongCache = null; // no more active FO, keep only soft ref
            closeCurrentRoot(false);
        }
    }

    /** Use soft-references to not throw away the data that quickly.
     * JarFS if often queried for its FOs e.g. by java parser, which
     * leaves the references immediately.
     */
    private class Ref<T extends FileObject> extends WeakReference<T> implements Runnable {
        public Ref(T fo) {
            super(fo, Utilities.activeReferenceQueue());
        }

        // do the cleanup
        public void run() {
            freeReference();
        }
    }

    /** Initializes the root of FS.
    */
    private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
        ois.defaultReadObject();
        closeSync = new Object();
        strongCache = null;
        softCache = new SoftReference<Cache>(null);
        aliveCount = 0;

        try {
            setJarFile(root);
        } catch (PropertyVetoException ex) {
            throw new IOException(ex.getMessage());
        } catch (IOException iex) {
            LOGGER.log(Level.INFO, iex.getLocalizedMessage());
        }
    }

    /** Must be called from synchronized block*/
    private AltArchiveFile reOpenAltArchiveFile() throws IOException {
        synchronized (closeSync) {
            if (closeTask != null) {
                closeTask.cancel();
            }

            // #167527 - calculate adaptive delay before closing jar
            long now = System.currentTimeMillis();
            long requestPeriod = now - openRequestTime;
            openRequestTime = now;
            // 150% of time from last open request, but between CLOSE_DELAY_MIN and CLOSE_DELAY_MAX
            closeDelay = (int) Math.min(500, Math.max(300, (1.5 * requestPeriod)));

            return getAltArchive(true);
        }
    }

    /** Performs a clean-up
     * After close of JarFile must be always reference to JarFile set to null
     */
    private void closeCurrentRoot(boolean isRealClose) {
        synchronized (closeSync) {
            if (closeTask != null) {
                closeTask.cancel();
            }

            if (isRealClose) {
                realClose().run();
            } else {
                closeTask = req.post(realClose(), closeDelay);
            }
        }
    }

    private Runnable realClose() {
        return new Runnable() {
                public void run() {
                    synchronized (closeSync) {
                    final AltArchiveFile jarFile = getAltArchive(false);
                        if (jarFile != null) {
                            try {
                                jarFile.close();
                                LOGGER.log(Level.FINE, "closed: "+ System.currentTimeMillis()+ "   " + root.getAbsolutePath());//NOI18N
                            } catch (Exception exc) {
                                // ignore exception during closing, just log it
                                LOGGER.log(Level.OFF, null, exc);
                            } finally {
                                setAltArchive(null);
                                closeTask = null;
                            }
                        }
                    }
                }
            };
    }

    /** Getter for entry.
    */
    private final AltArchiveFile.AltArchiveEntry getEntry(String file) {
        AltArchiveFile j = null;

        try {
            synchronized (closeSync) {
                j = reOpenAltArchiveFile();

                AltArchiveFile.AltArchiveEntry je = null;
                if (j != null) {
                    je = j.getAltArchiveEntry(file);
                }

                if (je != null) {
                    return je;
                }
            }
        } catch (IOException iox) {
        }

        return getAltArchive(false).newAltArchiveEntry(file);
    }

    /**
     * @return the jar
     */
    private AltArchiveFile getAltArchive(boolean create) {
        assert Thread.holdsLock(closeSync);
        if (jar == null && create) {
            try {
                if (root.canRead()) {
                    jar = new AltArchiveFile(root);
                    LOGGER.log(Level.FINE, "opened: {0} {1}", new Object[]{root.getAbsolutePath(), System.currentTimeMillis()}); //NOI18N
                    return jar;
                }
            } catch (ZipException ex) {
                LOGGER.log(Level.INFO, ex.getMessage(), ex);
            } catch (IOException ex) {
                LOGGER.log(Level.INFO, ex.getMessage(), ex);
            }
            LOGGER.log(Level.WARNING, "cannot open {0}", root.getAbsolutePath()); // NOI18N
        }
        return jar;
    }

    private InputStream getTemporaryInputStream(AltArchiveFile jf, AltArchiveFile.AltArchiveEntry je, boolean forceRecreate)
    throws IOException {
        String filePath = jf.getName();
        String entryPath = je.getName();
        StringBuffer jarCacheFolder = new StringBuffer("jarfscache"); //NOI18N
        jarCacheFolder.append(System.getProperty("user.name")).append("/"); //NOI18N

        File jarfscache = new File(System.getProperty("java.io.tmpdir"), jarCacheFolder.toString()); //NOI18N

        if (!jarfscache.exists()) {
            jarfscache.mkdirs();
        }

        File f = new File(jarfscache, temporaryName(filePath, entryPath));

        boolean createContent = !f.exists();

        if (createContent) {
            f.createNewFile();
        } else {
            forceRecreate |= (Math.abs((System.currentTimeMillis() - f.lastModified())) > 10000);
        }

        if (createContent || forceRecreate) {
            // JDK 1.3 contains bug #4336753
            //is = j.getInputStream (je);
            InputStream is = jf.getInputStream(je);

            try {
                OutputStream os = new FileOutputStream(f);

                try {
                    FileUtil.copy(is, os);
                } finally {
                    os.close();
                }
            } finally {
                is.close();
            }
        }

        f.deleteOnExit();

        return new FileInputStream(f);
    }

    private static String temporaryName(String filePath, String entryPath) {
        String fileHash = String.valueOf(filePath.hashCode());
        String entryHash = String.valueOf(entryPath.hashCode());

        StringBuffer sb = new StringBuffer();
        sb.append("f").append(fileHash).append("e").append(entryHash);

        return sb.toString().replace('-', 'x'); //NOI18N
    }

    private Cache getCache() {
        Cache ret = strongCache;

        if (ret == null) {
            ret = softCache.get();
        }

        if (ret == null) {
            ret = parse(false);
        }

        assert ret != null;

        return ret;
    }

    /** refreshes children recursively.*/
    private void refreshExistingFileObjects() {
        Cache cache = getCache();
        String[] empty = new String[0];

        Enumeration<? extends FileObject> en = existingFileObjects(getRoot());

        while (en.hasMoreElements()) {
            // AbstractFolder is package...might have to resort to reflection...
            /*AbstractFolder fo = (AbstractFolder) en.nextElement();
            assert fo != null;

            if (fo.isFolder() && !fo.isInitialized()) {
                continue;
            }

            String[] children = cache.getChildrenOf(fo.getPath());

            if (children == null) {
                children = empty;
            }

            fo.refresh(null, null, true, true, children);*/
        }
    }

    /**parses entries of JarFile into EntryCache hierarchical structure and sets
     * lastModified to actual value.
     */
    private Cache parse(boolean refresh) {
        // force watcher to reschedule us if not succesfull
        AltArchiveFile j = null;
        long start;

        //beginAtomicAction();


        try {
            synchronized (closeSync) {
                start = System.currentTimeMillis();

                lastModification = 0;
                closeCurrentRoot(false);

                for (int i = 0; i <= 2; i++) {
                    try {
                        j = reOpenAltArchiveFile();

                        break;
                    } catch (IOException ex) {
                        if (i >= 2) {
                            return Cache.INVALID;
                        }

                        continue;
                    }
                }

                try {
                    Enumeration<AltArchiveFile.AltArchiveEntry> en = j.entries();
                    // #144166 - If duplicate entries found in jar, it is logged
                    // and only unique entries are show. It can happen because
                    // Ant's jar task can produce such jars.
                    Set<String> duplicateCheck = new HashSet<String>();
                    Set<AltArchiveFile.AltArchiveEntry> uniqueEntries = new HashSet<AltArchiveFile.AltArchiveEntry>();
                    boolean duplicateReported = false;
                    while (en.hasMoreElements()) {
                        AltArchiveFile.AltArchiveEntry entry = en.nextElement();
                        String name = entry.getName();
                        if (duplicateCheck.add(name)) {
                            uniqueEntries.add(entry);
                        } else {
                            if (!duplicateReported) {
                                LOGGER.warning("Duplicate entries in " + getJarFile() + ": " + name + "; please report to JAR creator.");
                                // report just once
                                duplicateReported = true;
                            }
                        }
                    }
                    Cache newCache = new Cache(uniqueEntries);
                    lastModification = root.lastModified();
                    strongCache = newCache;
                    softCache = new SoftReference<Cache>(newCache);

                    return newCache;
                } catch (Throwable t) {
                    // jar is invalid; perhaps it's being rebuilt
                    // don't touch filesystem
                    return Cache.INVALID;
                }
            }
        } finally {
            closeCurrentRoot(false);

            if (refresh) {
                refreshExistingFileObjects();
            }

            if ((checkTime > 0) && (watcherTask == null)) {
                watcherTask = req.post(watcherTask(), checkTime);
            }

            //finishAtomicAction();
        }
    }

    /* Anonymous Runnable class - responsible for checking whether JarFile was modified => standalone thread.
     * If JarFile was modified, parsing is invoked.
     */
    private Runnable watcherTask() {
        return new Runnable() {
                public void run() {
                    try {
                        if (root == null) {
                            return;
                        }

                        /** JarFile was modified => parse it and refresh existing FileObjects*/
                        if (root.lastModified() != lastModification) {
                            parse(true);
                        }
                    } finally {
                        /** reschedule watcherTask*/
                        if (watcherTask != null) {
                            watcherTask.schedule(checkTime);
                        }
                    }
                }
            };
    }

    private void setAltArchive(AltArchiveFile tempAltArchive) {
        this.altArchive = tempAltArchive;
    }

    private static class Cache {
        private static final Set<AltArchiveFile.AltArchiveEntry> EMPTY_SET = Collections.emptySet();
        static final Cache INVALID = new Cache(EMPTY_SET);
        byte[] names = new byte[1000];
        private int nameOffset = 0;
        int[] EMPTY = new int[0];
        private Map<String, Folder> folders = new HashMap<String, Folder>();

        public Cache(Set<AltArchiveFile.AltArchiveEntry> entries) {
            parse(entries);
            trunc();
        }

        public boolean isFolder(String name) {
            return folders.get(name) != null;
        }

        public String[] getChildrenOf(String folder) {
            Folder fol = folders.get(folder);

            if (fol != null) {
                return fol.getNames();
            }

            return new String[] {  };
        }

        private void parse(Set<AltArchiveFile.AltArchiveEntry> entries) {
            folders.put("", new Folder()); // root folder

            for (AltArchiveFile.AltArchiveEntry entry : entries) {
                String name = entry.getName();
                boolean isFolder = false;

                // work only with slashes
                name = name.replace('\\', '/');

                if (name.startsWith("/")) {
                    name = name.substring(1); // NOI18N
                }

                if (name.endsWith("/")) {
                    name = name.substring(0, name.length() - 1); // NOI18N
                    isFolder = true;
                }

                int lastSlash = name.lastIndexOf('/');
                String dirName = ""; // root
                String realName = name;

                if (lastSlash > 0) {
                    dirName = name.substring(0, lastSlash); // or folder
                    realName = name.substring(lastSlash + 1);
                }

                if (isFolder) {
                    getFolder(name); // will create the folder item
                } else {
                    Folder fl = getFolder(dirName);
                    fl.addChild(realName);
                }
            }
        }

        private Folder getFolder(String name) {
            Folder fl = folders.get(name);

            if (fl == null) {
                // add all the superfolders on the way to the root
                int lastSlash = name.lastIndexOf('/');
                String dirName = ""; // root
                String realName = name;

                if (lastSlash > 0) {
                    dirName = name.substring(0, lastSlash); // or folder
                    realName = name.substring(lastSlash + 1);
                }

                getFolder(dirName).addChild(realName);

                fl = new Folder();
                folders.put(name, fl);
            }

            return fl;
        }

        private void trunc() {
            // strip the name array:
            byte[] newNames = new byte[nameOffset];
            System.arraycopy(names, 0, newNames, 0, nameOffset);
            names = newNames;

            // strip all the indices arrays:
            for (Iterator it = folders.values().iterator(); it.hasNext();) {
                ((Folder) it.next()).trunc();
            }
        }

        private int putName(byte[] name) {
            int start = nameOffset;

            if ((start + name.length) > names.length) {
                byte[] newNames = new byte[(names.length * 2) + name.length];
                System.arraycopy(names, 0, newNames, 0, start);
                names = newNames;
            }

            System.arraycopy(name, 0, names, start, name.length);
            nameOffset += name.length;

            return start;
        }

        private class Folder {
            private int[] indices = EMPTY;
            private int idx = 0;

            public Folder() {
            }

            public String[] getNames() {
                String[] ret = new String[idx / 2];

                for (int i = 0; i < ret.length; i++) {
                    byte[] name = new byte[indices[(2 * i) + 1]];
                    System.arraycopy(names, indices[2 * i], name, 0, name.length);

                    try {
                        ret[i] = new String(name, "UTF-8");
                    } catch (UnsupportedEncodingException e) {
                        throw new InternalError("No UTF-8");
                    }
                }

                return ret;
            }

            void addChild(String name) {
                // ensure enough space
                if ((idx + 2) > indices.length) {
                    int[] newInd = new int[(2 * indices.length) + 2];
                    System.arraycopy(indices, 0, newInd, 0, idx);
                    indices = newInd;
                }

                try {
                    byte[] bytes = name.getBytes("UTF-8");
                    indices[idx++] = putName(bytes);
                    indices[idx++] = bytes.length;
                } catch (UnsupportedEncodingException e) {
                    throw new InternalError("No UTF-8");
                }
            }

            void trunc() {
                if (indices.length > idx) {
                    int[] newInd = new int[idx];
                    System.arraycopy(indices, 0, newInd, 0, idx);
                    indices = newInd;
                }
            }
        }
    }
}
