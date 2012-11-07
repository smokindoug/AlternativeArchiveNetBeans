/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package name.dougmcneil.altarchivetypes;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipException;

import org.apache.commons.compress.archivers.tar.*;

/**
 *
 * @author doug
 */
public class AltArchiveFile {

    private final String _name;
    private final InputStream _is;
    private boolean _hasEnumerated;
    private TarArchiveInputStream _archiveStream;
    private Map<String, AltArchiveEntry> _entries = Collections.EMPTY_MAP;
    private boolean _isAltArchive;

    public AltArchiveFile(InputStream is, String name) throws IOException {
        if (is == null) {
            throw new IllegalArgumentException("InputStream cannot be null");
        }
        _is = new BufferedInputStream(is);
        _name = name;
        int available = _is.available();
        if (available > 0) {
            _is.mark(available);
            byte[] buffer = new byte[available];
            int read = _is.read(buffer);
            if (read > 0) {
                _isAltArchive = TarArchiveInputStream.matches(buffer, read);
            }
            _is.reset();
        }
    }

    public AltArchiveFile(File file) throws ZipException, IOException {
        this(new FileInputStream(file), file.getName());
    }

    public AltArchiveFile(String path) throws ZipException, IOException {
        this(new File(path));
    }

    public AltArchiveEntry getAltArchiveEntry(String name) throws ZipException, IOException {
        if (name == null) {
            throw new ZipException("Illegal name");
        }
        if (_entries.isEmpty()) {
            enumerateEntries();
        }
        return _entries.get(name);
    }

    public boolean isAltArchive() {
        return _isAltArchive;
    }

    public String getName() {
        return _name;
    }

    public Enumeration<AltArchiveEntry> entries() {
        return Collections.enumeration(_entries.values());
    }

    private void enumerateEntries() throws IOException {
        if (_hasEnumerated) {
            return;
        }
        _hasEnumerated = true;
        if (!_isAltArchive) {
            return;
        }
        _archiveStream = new TarArchiveInputStream(_is);
        _entries = new HashMap<String, AltArchiveEntry>();
        TarArchiveEntry entry;
        while ((entry = _archiveStream.getNextTarEntry()) != null) {
            _entries.put(entry.getName(), new AltArchiveEntry(entry));
        }

    }

    public void close() throws ZipException {
    }

    public AltArchiveEntry newAltArchiveEntry(String name) {
        return new AltArchiveEntry(name);
    }

    public InputStream getInputStream(AltArchiveEntry entry) throws IOException {
        return null;
    }

    public class AltArchiveEntry {

        private final String _name;
        private final TarArchiveEntry _entry;

        public AltArchiveEntry(TarArchiveEntry entry) {
            _entry = entry;
            _name = entry.getName();
        }
        public AltArchiveEntry(String name) {
            _name = name;
            _entry = null;
        }
        public long getSize() {
            return 0;
        }

        public String getName() {
            return _entry.getName();
        }
    }

}
