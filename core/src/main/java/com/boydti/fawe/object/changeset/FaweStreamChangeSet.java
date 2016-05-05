package com.boydti.fawe.object.changeset;

import com.boydti.fawe.config.Settings;
import com.boydti.fawe.object.change.MutableBlockChange;
import com.boydti.fawe.object.change.MutableEntityChange;
import com.boydti.fawe.object.change.MutableTileChange;
import com.google.common.collect.Iterators;
import com.sk89q.jnbt.CompoundTag;
import com.sk89q.jnbt.NBTInputStream;
import com.sk89q.jnbt.NBTOutputStream;
import com.sk89q.worldedit.history.change.Change;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Iterator;
import net.jpountz.lz4.LZ4Compressor;
import net.jpountz.lz4.LZ4Factory;
import net.jpountz.lz4.LZ4InputStream;
import net.jpountz.lz4.LZ4OutputStream;

public abstract class FaweStreamChangeSet extends FaweChangeSet {

    @Override
    public int size() {
        System.out.println("SIZE: " + blockSize);
        // Flush so we can accurately get the size
        flush();
        return blockSize;
    }

    public abstract int getCompressedSize();

    public abstract OutputStream getBlockOS(int x, int y, int z) throws IOException;
    public abstract NBTOutputStream getEntityCreateOS() throws IOException;
    public abstract NBTOutputStream getEntityRemoveOS() throws IOException;
    public abstract NBTOutputStream getTileCreateOS() throws IOException;
    public abstract NBTOutputStream getTileRemoveOS() throws IOException;

    public abstract InputStream getBlockIS() throws IOException;
    public abstract NBTInputStream getEntityCreateIS() throws IOException;
    public abstract NBTInputStream getEntityRemoveIS() throws IOException;
    public abstract NBTInputStream getTileCreateIS() throws IOException;
    public abstract NBTInputStream getTileRemoveIS() throws IOException;

    public int blockSize;
    public int entityCreateSize;
    public int entityRemoveSize;
    public int tileCreateSize;
    public int tileRemoveSize;

    private int originX;
    private int originZ;

    public void setOrigin(int x, int z) {
        originX = x;
        originZ = z;
    }

    public int getOriginX() {
        return originX;
    }

    public int getOriginZ() {
        return originZ;
    }

    public InputStream getCompressedIS(InputStream is) {
        if (Settings.COMPRESSION_LEVEL > 0) {
            is = new LZ4InputStream(new LZ4InputStream(is));
        } else {
            is = new LZ4InputStream(is);
        }
        return is;
    }

    public OutputStream getCompressedOS(OutputStream os) throws IOException {
        LZ4Factory factory = LZ4Factory.fastestInstance();
        LZ4Compressor compressor = factory.fastCompressor();
        os = new LZ4OutputStream(os, Settings.BUFFER_SIZE, factory.fastCompressor());
        if (Settings.COMPRESSION_LEVEL > 0) {
            os = new LZ4OutputStream(os, Settings.BUFFER_SIZE, factory.highCompressor());
        }
        return os;
    }

    public void add(int x, int y, int z, int combinedFrom, int combinedTo) {
        blockSize++;
        try {
            OutputStream stream = getBlockOS(x, y, z);
            //x
            x-=originX;
            stream.write((x) & 0xff);
            stream.write(((x) >> 8) & 0xff);
            //z
            z-=originZ;
            stream.write((z) & 0xff);
            stream.write(((z) >> 8) & 0xff);
            //y
            stream.write((byte) y);
            //from
            stream.write((combinedFrom) & 0xff);
            stream.write(((combinedFrom) >> 8) & 0xff);
            //to
            stream.write((combinedTo) & 0xff);
            stream.write(((combinedTo) >> 8) & 0xff);
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void addTileCreate(CompoundTag tag) {
        if (tag == null) {
            return;
        }
        try {
            NBTOutputStream nbtos = getTileCreateOS();
            nbtos.writeNamedTag(tileCreateSize++ + "", tag);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void addTileRemove(CompoundTag tag) {
        if (tag == null) {
            return;
        }
        try {
            NBTOutputStream nbtos = getTileRemoveOS();
            nbtos.writeNamedTag(tileRemoveSize++ + "", tag);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void addEntityRemove(CompoundTag tag) {
        if (tag == null) {
            return;
        }
        try {
            NBTOutputStream nbtos = getEntityRemoveOS();
            nbtos.writeNamedTag(entityRemoveSize++ + "", tag);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void addEntityCreate(CompoundTag tag) {
        if (tag == null) {
            return;
        }
        try {
            NBTOutputStream nbtos = getEntityCreateOS();
            nbtos.writeNamedTag(entityCreateSize++ + "", tag);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public Iterator<MutableBlockChange> getBlockIterator(final boolean dir) throws IOException {
        final InputStream is = getBlockIS();
        if (is == null) {
            return new ArrayList<MutableBlockChange>().iterator();
        }
        final MutableBlockChange change = new MutableBlockChange(0, 0, 0, (short) 0, (byte) 0);
        return new Iterator<MutableBlockChange>() {
            private MutableBlockChange last = read();
            public MutableBlockChange read() {
                try {
                    int read0 = is.read();
                    if (read0 == -1) {
                        return null;
                    }
                    System.out.println("r0: " + read0);
                    int x = ((byte) read0 & 0xFF) + ((byte) is.read() << 8) + originX;
                    int z = ((byte) is.read() & 0xFF) + ((byte) is.read() << 8) + originZ;
                    int y = is.read() & 0xff;
                    change.x = x;
                    change.y = y;
                    change.z = z;
                    if (dir) {
                        is.skip(2);
                        int to1 = is.read();
                        int to2 = is.read();
                        change.id = (short) ((to2 << 4) + (to1 >> 4));
                        change.data = (byte) (to1 & 0xf);
                    } else {
                        int from1 = is.read();
                        int from2 = is.read();
                        is.skip(2);
                        change.id = (short) ((from2 << 4) + (from1 >> 4));
                        change.data = (byte) (from1 & 0xf);
                    }
                    System.out.println("CHANGE: " + change.id);
                    return change;
                } catch (Exception ignoreEOF) {
                    ignoreEOF.printStackTrace();
                }
                return null;
            }

            @Override
            public boolean hasNext() {
                if (last == null) {
                    last = read();
                }
                if (last != null) {
                    System.out.println("HAS NEXT!");
                    return true;
                }
                System.out.println("NO NEXT");
                try {
                    is.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return false;
            }

            @Override
            public MutableBlockChange next() {
                MutableBlockChange tmp = last;
                last = null;
                return tmp;
            }

            @Override
            public void remove() {
                throw new IllegalArgumentException("CANNOT REMOVE");
            }
        };
    }

    public Iterator<MutableEntityChange> getEntityIterator(final NBTInputStream is, final boolean create, final boolean dir) {
        if (is == null) {
            return new ArrayList<MutableEntityChange>().iterator();
        }
        final MutableEntityChange change = new MutableEntityChange(null, create);
        try {
            return new Iterator<MutableEntityChange>() {
                private CompoundTag last = read();

                public CompoundTag read() {
                    try {
                        return (CompoundTag) is.readNamedTag().getTag();
                    } catch (Exception ignoreEOS) {}
                    return null;
                }

                @Override
                public boolean hasNext() {
                    if (last == null) {
                        last = read();
                    }
                    if (last != null) {
                        return true;
                    }
                    try {
                        is.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    return false;
                }

                @Override
                public MutableEntityChange next() {
                    change.tag = last;
                    last = null;
                    return change;
                }

                @Override
                public void remove() {
                    throw new IllegalArgumentException("CANNOT REMOVE");
                }
            };
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public Iterator<MutableTileChange> getTileIterator(final NBTInputStream is, final boolean create, final boolean dir) {
        if (is == null) {
            return new ArrayList<MutableTileChange>().iterator();
        }
        final MutableTileChange change = new MutableTileChange(null, create);
        try {
            return new Iterator<MutableTileChange>() {
                private CompoundTag last = read();

                public CompoundTag read() {
                    try {
                        return (CompoundTag) is.readNamedTag().getTag();
                    } catch (Exception ignoreEOS) {}
                    return null;
                }

                @Override
                public boolean hasNext() {
                    if (last == null) {
                        last = read();
                    }
                    if (last != null) {
                        return true;
                    }
                    try {
                        is.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    return false;
                }

                @Override
                public MutableTileChange next() {
                    change.tag = last;
                    last = null;
                    return change;
                }

                @Override
                public void remove() {
                    throw new IllegalArgumentException("CANNOT REMOVE");
                }
            };
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public Iterator<Change> getIterator(final boolean dir) {
        System.out.println("GET ITERATOR: " + dir);
        flush();
        try {
            Iterator<MutableTileChange> tileCreate = getTileIterator(getTileCreateIS(), true, dir);
            Iterator<MutableTileChange> tileRemove = getTileIterator(getTileRemoveIS(), false, dir);

            Iterator<MutableEntityChange> entityCreate = getEntityIterator(getEntityCreateIS(), true, dir);
            Iterator<MutableEntityChange> entityRemove = getEntityIterator(getEntityRemoveIS(), false, dir);

            Iterator<MutableBlockChange> blockChange = getBlockIterator(dir);

            return Iterators.concat(tileCreate, tileRemove, entityCreate, entityRemove, blockChange);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new ArrayList<Change>().iterator();
    }

    @Override
    public Iterator<Change> backwardIterator() {
        return getIterator(false);
    }

    @Override
    public Iterator<Change> forwardIterator() {
        return getIterator(true);
    }
}
