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
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream;
import org.openide.util.Exceptions;

/**
 *
 * @author doug
 * @todo a generic approach should be used
 */
public class AltArchiveFile {

    private SourceHandler _source;
    public AltArchiveFile(InputStream is, String name) throws IOException {
        _source = new TarSourceHandler(null, is, name);
    }

    public AltArchiveFile(File file) throws ZipException, IOException {
        if (file.getName().endsWith(".tgz")) {
            _source = new TgzSourceHandler(file, new FileInputStream(file),  file.getName());
            return;
        } else if (file.getName().endsWith(".gz")) {
           _source = new GzSourceHandler(file,  new FileInputStream(file),  file.getName());
           return;
        } else if (file.getName().endsWith(".bz2") || file.getName().endsWith(".bz")) {
           _source = new Bz2SourceHandler(file,  new FileInputStream(file),  file.getName());
           return;
        } else if (file.getName().endsWith(".tbz") || file.getName().endsWith(".tbz2")) {
            _source = new TbzSourceHandler(file, new FileInputStream(file),  file.getName());
            return;
        } else if (file.getName().endsWith(".tar")) {
            _source = new TarSourceHandler(file,new FileInputStream(file),  file.getName());
        } else if (file.getName().endsWith(".xz")) {
            _source = new XZSourceHandler(file,new FileInputStream(file),  file.getName());
        } else if (file.getName().endsWith(".txz")) {
            _source = new TxzSourceHandler(file,new FileInputStream(file),  file.getName());
        }
    }

    public AltArchiveFile(String path) throws ZipException, IOException {
        this(new File(path));
    }

    public AltArchiveEntry getAltArchiveEntry(String name) throws ZipException,
            IOException {
        if (name == null) {
            throw new ZipException("Illegal name");
        }
        if (_source._entries.isEmpty()) {
            enumerateEntries();
        }
        AltArchiveEntry entry = _source._entries.get(name);
        if (entry == null) {
            name = name + "/";
            entry = _source._entries.get(name);
            if (entry == null) {
                entry = new AbsentDirTarEntry(name);
            }
        }
        return entry;
    }

    public boolean isAltArchive() {
        return _source._isAltArchive;
    }

    public String getName() {
        return _source._name;
    }

    public Enumeration<AltArchiveEntry> entries() throws IOException {
        enumerateEntries();
        return Collections.enumeration(_source._entries.values());
    }

    private void enumerateEntries() throws IOException {
        _source.enumerateEntries();
    }

    public void close() throws ZipException {
        try {
            _source._is.close();
        } catch (IOException ex) {
            Exceptions.printStackTrace(ex);
        }
    }

    public InputStream getInputStream(final AltArchiveEntry entry) throws
            IOException {
        return _source.getInputStream(entry);
    }

    /**
     * Support for tar entries
     */
    class TarEntry extends AltArchiveEntry {
        
        private final TarArchiveEntry _entry;

        public TarEntry(TarArchiveEntry entry, AltArchiveFile outer) {
            super(entry.getName(), outer);
            _entry = entry;
        }
        public TarEntry(String name, AltArchiveFile outer) {
            super(name, outer);
            _entry = null;
        }
        @Override
        public long getSize() {
            return _entry.getSize();
        }

        @Override
        public long getTime() {
            return _entry.getModTime().getTime();
        }

    }
    
    class AbsentDirTarEntry extends AltArchiveEntry {

        public AbsentDirTarEntry(String name) {
            super(name);
        }
        
        @Override
        public long getSize() {
           return 0;
        }

        @Override
        public long getTime() {
            return 0L;
        }
        
    }

    // keeps source info on archive
    private abstract class SourceHandler {
        final String _name;
        final File _file;
        final InputStream _is;
        boolean _hasEnumerated;
        TarArchiveInputStream _archiveStream;
        @SuppressWarnings( "unchecked" )
        Map<String, AltArchiveEntry> _entries = Collections.EMPTY_MAP;
        boolean _isAltArchive;

        SourceHandler(File file, InputStream is, String name) throws
                IOException {
            if (is == null) {
                throw new IllegalArgumentException("InputStream null");
            }
            _is = new BufferedInputStream(is);
            _name = name;
            _file = file;
        }

        abstract void enumerateEntries() throws IOException;
        abstract InputStream getInputStream(AltArchiveEntry entry)
                throws IOException;

    }

    private final class TarSourceHandler extends SourceHandler {
        TarSourceHandler(File file, InputStream is, String name)
                throws IOException {
            super(file, is, name);
            // test file for archiveness
            _is.mark(1024);
            byte[] buffer = new byte[1024];
            int read = _is.read(buffer);
            if (read > 0) {
                _isAltArchive = TarArchiveInputStream.matches(buffer, read);
                _is.reset();
                if (!_isAltArchive && (read > 262)) {
                    // try 7-zip all 0 test
                    for (int i = 257; i < 261; i++) {
                        if (buffer[i] != 0) {
                            return;
                        }
                    }
                    _isAltArchive = true;
                }
            }
        }

        @Override
        void enumerateEntries() throws IOException {
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
                _entries.put(entry.getName(), new TarEntry(entry, name.dougmcneil.altarchivetypes.AltArchiveFile.this));
            }
            _is.close();

        }

        @Override
        public InputStream getInputStream(AltArchiveEntry entry) throws IOException {
            if (entry == null) {
                return null;
            }
            if  (_file == null) {
                return null;
            }
            _archiveStream.close();
            _archiveStream = new TarArchiveInputStream(
                    new BufferedInputStream(new FileInputStream(_file)));
            TarArchiveEntry target;
            while ((target = _archiveStream.getNextTarEntry()) != null) {
                if (target.getName().equals(entry._name)) {
                    return _archiveStream;
                }
            }
            return null;
        }

    }

    private final class TgzSourceHandler extends SourceHandler {
        TgzSourceHandler(File file, InputStream is, String name)
                throws IOException {
            super(file, new GzipCompressorInputStream(is), name);
            _is.mark(1024);
            byte[] buffer = new byte[1024];
            int read = _is.read(buffer);
            if (read > 0) {
                _isAltArchive = TarArchiveInputStream.matches(buffer, read);
                _is.reset();
                if (!_isAltArchive && (read > 262)) {
                    // try 7-zip all 0 test
                    for (int i = 257; i < 261; i++) {
                        if (buffer[i] != 0) {
                            return;
                        }
                    }
                    _isAltArchive = true;
                }
            }
        }

        @Override
        void enumerateEntries() throws IOException {
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
                _entries.put(entry.getName(), new TarEntry(entry, name.dougmcneil.altarchivetypes.AltArchiveFile.this));
            }
            _is.close();

        }

        @Override
        InputStream getInputStream(AltArchiveEntry entry) throws IOException {
            if (entry == null) {
                return null;
            }
            if  (_file == null) {
                return null;
            }
            _archiveStream.close();
            _archiveStream = new TarArchiveInputStream(
                    new BufferedInputStream(new GzipCompressorInputStream(
                    new FileInputStream(_file))));
            TarArchiveEntry target;
            while ((target = _archiveStream.getNextTarEntry()) != null) {
                if (target.getName().equals(entry._name)) {
                    return _archiveStream;
                }
            }
            return null;
        }
    }

    private abstract class CompressSourceHandler extends SourceHandler {
        CompressSourceHandler(File file, InputStream is, String name) 
                throws IOException {
            super(file, is, name);
            InputStream x = new FileInputStream(_file);
            x.mark(1024);
            byte[] buffer = new byte[1024];
            int read = x.read(buffer);
            if (read > 0) {
                _isAltArchive = streamMatches(buffer, read);
            }
            x.close();
        }
        
        @Override
        void enumerateEntries() throws IOException {
            if (_hasEnumerated) {
                return;
            }
            _hasEnumerated = true;
            if (!_isAltArchive) {
                return;
            }
            _is.close();
            _entries = new HashMap<String, AltArchiveEntry>(1);
            Entry entry = new Entry(_file, fileAdjustment(), name.dougmcneil.altarchivetypes.AltArchiveFile.this);
            _entries.put(entry.getName(), entry);
        }

        @Override
        InputStream getInputStream(AltArchiveEntry entry) throws IOException {
            if (entry == null) {
                return null;
            }
            if  (_file == null) {
                return null;
            }
            return new BufferedInputStream(compressStreamForFile());
        }

        abstract protected boolean streamMatches(byte[] buffer, int read);
        abstract protected int fileAdjustment();
        abstract protected InputStream compressStreamForFile() throws IOException;
    }
    private final class GzSourceHandler extends CompressSourceHandler {
        private Entry _entry;

        GzSourceHandler(File file, InputStream is, String name)
                throws IOException {
            super(file, new GzipCompressorInputStream(is), name);
        }
        
        @Override
        protected boolean streamMatches(byte[] buffer, int read) {
            return GzipCompressorInputStream.matches(buffer, read);
        }
        
        @Override
        protected int fileAdjustment() {
            return 3;
        }
        
        @Override
        protected InputStream compressStreamForFile() throws IOException {
            return new GzipCompressorInputStream(new FileInputStream(_file));
        }

    }
    
    private final class TbzSourceHandler extends SourceHandler {
        TbzSourceHandler(File file, InputStream is, String name)
                throws IOException {
            super(file, new BZip2CompressorInputStream(is), name);
            _is.mark(1024);
            byte[] buffer = new byte[1024];
            int read = _is.read(buffer);
            if (read > 0) {
                _isAltArchive = TarArchiveInputStream.matches(buffer, read);
                _is.reset();
                if (!_isAltArchive && (read > 262)) {
                    // try 7-zip all 0 test
                    for (int i = 257; i < 261; i++) {
                        if (buffer[i] != 0) {
                            return;
                        }
                    }
                    _isAltArchive = true;
                }
            }
        }

        @Override
        void enumerateEntries() throws IOException {
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
                _entries.put(entry.getName(), new TarEntry(entry, name.dougmcneil.altarchivetypes.AltArchiveFile.this));
            }
            _is.close();

        }

        @Override
        InputStream getInputStream(AltArchiveEntry entry) throws IOException {
            if (entry == null) {
                return null;
            }
            if  (_file == null) {
                return null;
            }
            _archiveStream.close();
            _archiveStream = new TarArchiveInputStream(
                    new BufferedInputStream(new BZip2CompressorInputStream(
                    new FileInputStream(_file))));
            TarArchiveEntry target;
            while ((target = _archiveStream.getNextTarEntry()) != null) {
                if (target.getName().equals(entry._name)) {
                    return _archiveStream;
                }
            }
            return null;
        }
    }

    private final class Bz2SourceHandler extends CompressSourceHandler {
        private Entry _entry;

        Bz2SourceHandler(File file, InputStream is, String name)
                throws IOException {
            super(file, new BZip2CompressorInputStream(is), name);
        }

        @Override
        protected boolean streamMatches(byte[] buffer, int read) {
            return BZip2CompressorInputStream.matches(buffer, read);
        }
        
        @Override
        protected int fileAdjustment() {
            return 4;
        }
        
        @Override
        protected InputStream compressStreamForFile() throws IOException {
            return new BZip2CompressorInputStream(new FileInputStream(_file));
        }

    }

    private final class TxzSourceHandler extends SourceHandler {
        TxzSourceHandler(File file, InputStream is, String name)
                throws IOException {
            super(file, new XZCompressorInputStream(is), name);
            _is.mark(1024);
            byte[] buffer = new byte[1024];
            int read = _is.read(buffer);
            if (read > 0) {
                _isAltArchive = TarArchiveInputStream.matches(buffer, read);
                _is.reset();
                if (!_isAltArchive && (read > 262)) {
                    // try 7-zip all 0 test
                    for (int i = 257; i < 261; i++) {
                        if (buffer[i] != 0) {
                            return;
                        }
                    }
                    _isAltArchive = true;
                }
            }
        }

        @Override
        void enumerateEntries() throws IOException {
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
                _entries.put(entry.getName(), new TarEntry(entry, name.dougmcneil.altarchivetypes.AltArchiveFile.this));
            }
            _is.close();

        }

        @Override
        InputStream getInputStream(AltArchiveEntry entry) throws IOException {
            if (entry == null) {
                return null;
            }
            if  (_file == null) {
                return null;
            }
            _archiveStream.close();
            _archiveStream = new TarArchiveInputStream(
                    new BufferedInputStream(new XZCompressorInputStream(
                    new FileInputStream(_file))));
            TarArchiveEntry target;
            while ((target = _archiveStream.getNextTarEntry()) != null) {
                if (target.getName().equals(entry._name)) {
                    return _archiveStream;
                }
            }
            return null;
        }
    }

    private final class XZSourceHandler extends CompressSourceHandler {
        private Entry _entry;

        XZSourceHandler(File file, InputStream is, String name)
                throws IOException {
            super(file, new XZCompressorInputStream(is), name);
        }

        @Override
        protected boolean streamMatches(byte[] buffer, int read) {
            return XZCompressorInputStream.matches(buffer, read);
        }
        
        @Override
        protected int fileAdjustment() {
            return 3;
        }
        
        @Override
        protected InputStream compressStreamForFile() throws IOException {
            return new XZCompressorInputStream(new FileInputStream(_file));
        }

    }

    static class Entry extends AltArchiveEntry {
        private final File _file;
        public Entry(File file, int adjust, AltArchiveFile outer) {
            super(file.getName().substring(0,
                    file.getName().length() - adjust), outer);
            _file = file;
        }
        @Override
        public long getSize() {
            return _file.length();
        }

        @Override
        public String getName() {
            return _name;
        }

        @Override
        public long getTime() {
            return _file.lastModified();
        }

    }
}


