/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package name.dougmcneil.altarchivetypes;

import java.io.IOException;
import org.openide.filesystems.FileObject;
import org.openide.loaders.DataObjectExistsException;
import org.openide.loaders.MultiDataObject;
import org.openide.loaders.MultiFileLoader;
import org.openide.nodes.Node;
import org.openide.util.Lookup;

public class AltArchiveDataObject extends MultiDataObject {

    public AltArchiveDataObject(FileObject pf, MultiFileLoader loader) throws
            DataObjectExistsException, IOException {
        super(pf, loader);

    }

    @Override
    protected Node createNodeDelegate() {
        return new AltArchiveNode(this);
    }

    @Override
    public Lookup getLookup() {
        return getCookieSet().getLookup();
    }
}
