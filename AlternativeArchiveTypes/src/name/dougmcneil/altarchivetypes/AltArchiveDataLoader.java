/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package name.dougmcneil.altarchivetypes;

import java.io.IOException;
import org.openide.filesystems.FileObject;
import org.openide.loaders.DataObjectExistsException;
import org.openide.loaders.ExtensionList;
import org.openide.loaders.MultiDataObject;
import org.openide.loaders.UniFileLoader;
import org.openide.util.NbBundle;

/**
 *
 * @author doug
 */
public class AltArchiveDataLoader extends UniFileLoader {


    static final String ALTARCHIVE_MIME_TYPE = "application/x-tar";   //NOI18N

    private static final long serialVersionUID = 1L;

    public AltArchiveDataLoader() {
        super("name.dougmcneil.altarchivetypes.AltArchiveDataObject"); // NOI18N
    }

    protected String defaultDisplayName() {
        return NbBundle.getMessage(AltArchiveDataLoader.class, "LBL_AltArchive_loader_name");
    }

    protected void initialize() {
        super.initialize();
        ExtensionList extensions = new ExtensionList();
        extensions.addMimeType(ALTARCHIVE_MIME_TYPE);
        setExtensions(extensions);
    }

    @Override
    protected String actionsContext() {
       return "Loaders/application/x-tar/Actions/"; // NOI18N
    }

    protected MultiDataObject createMultiObject(FileObject primaryFile) throws DataObjectExistsException, IOException {
        return new AltArchiveDataObject(primaryFile, this);
    }
}
