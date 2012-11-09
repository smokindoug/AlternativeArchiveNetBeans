/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package name.dougmcneil.altarchivetypes;

import javax.swing.Action;
import org.openide.filesystems.FileObject;
import org.openide.loaders.DataFilter;
import org.openide.loaders.DataFolder;
import org.openide.loaders.DataNode;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.util.RequestProcessor;

/**
 * A node to represent a AltArchive (tar) file.
 * @author doug
 */
final class AltArchiveNode extends DataNode {
    private static final RequestProcessor RP = new RequestProcessor(AltArchiveNode.class.getName(), 1, false, false);

    public AltArchiveNode(AltArchiveDataObject obj) {
        this(obj, new DummyChildren());
    }

    private AltArchiveNode(AltArchiveDataObject obj, DummyChildren c) {
        super(obj, c);
        c.attachAltArchiveNode(this);
        setIconBaseWithExtension("name/dougmcneil/altarchivetypes/tar_16.png"); // NOI18N
    }

    public Action getPreferredAction() {
        return null;
    }

    private static Children childrenFor(FileObject aar) {
//        if (!FileUtil.isArchiveFile(aar)) {
            // Maybe corrupt, etc.
  //          return Children.LEAF;
 //       }
        FileObject root = FileUtil.getAltArchiveRoot(aar);
        if (root != null) {
            DataFolder df = DataFolder.findFolder(root);
            return df.createNodeChildren(DataFilter.ALL);
        } else {
            return Children.LEAF;
        }
    }

    /**
     * There is no nice way to lazy create delegating node's children.
     * So, in order to fix #83595, here is a little hack that schedules
     * replacement of this dummy children on addNotify call.
     */
    final static class DummyChildren extends Children implements Runnable {

        private AltArchiveNode node;

        protected void addNotify() {
            super.addNotify();
            assert node != null;
            RP.post(this);
        }

        private void attachAltArchiveNode(AltArchiveNode altArchiveNode) {
            this.node = altArchiveNode;
        }

        public void run() {
            node.setChildren(childrenFor(node.getDataObject().getPrimaryFile()));
        }

        public boolean add(final Node[] nodes) {
            // no-op
            return false;
        }

        public boolean remove(final Node[] nodes) {
            // no-op
            return false;
        }

    }

}
