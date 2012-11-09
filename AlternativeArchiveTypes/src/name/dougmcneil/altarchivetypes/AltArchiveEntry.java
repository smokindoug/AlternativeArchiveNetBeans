/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package name.dougmcneil.altarchivetypes;

/**
 *
 * @author doug
 */
public abstract class AltArchiveEntry {
    final String _name;
    private final AltArchiveFile outer;
    
    public AltArchiveEntry(String name) {
        _name = name;
        outer = null;
    }

    public AltArchiveEntry(String name, final AltArchiveFile outer) {
        this.outer = outer;
        _name = name;
    }

    public abstract long getSize();

    public abstract long getTime();

    public String getName() {
        return _name;
    }

    // equals based on unique name
    @Override
    public boolean equals(Object alien) {
        if (alien instanceof AltArchiveEntry) {
            return _name.equals(((AltArchiveEntry) alien)._name);
        }
        return false;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 97 * hash + this._name.hashCode();
        return hash;
    }
    
}
