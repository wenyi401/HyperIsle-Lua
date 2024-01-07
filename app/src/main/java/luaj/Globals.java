package luaj;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.Reader;

import luaj.lib.BaseLib;
import luaj.lib.DebugLib;
import luaj.lib.PackageLib;
import luaj.lib.ResourceFinder;
import luaj.lib.StringLib;
import luaj.lib.jse.LuajavaLib;

public class Globals extends LuaTable {

    public InputStream STDIN = null;

    public PrintStream STDOUT = System.out;

    public PrintStream STDERR = System.err;
    public ResourceFinder finder;

    public LuaThread running = new LuaThread(this);

    public BaseLib baselib;

    public PackageLib package_;

    public DebugLib debuglib;

    public StringLib stringlib;

    public LuajavaLib luajavaLib;
    public Loader loader;
    public Compiler compiler;
    public Undumper undumper;

    public Globals checkglobals() {
        return this;
    }

    public LuaValue loadfile(String filename) {
        try {
            InputStream is = finder.findResource(filename);
            if (is == null)
                return error("cannot load " + filename + ": No such file");
            return load(is, "@" + filename, "bt", this);
        } catch (Exception e) {

            e.printStackTrace();
            return error("load " + filename + ": " + e);
        }
    }

    public LuaValue loadfile(InputStream is, String filename) {
        try {
            if (is == null)
                return error("cannot load " + filename + ": No such file");
            return load(is, "@" + filename, "bt", this);
        } catch (Exception e) {
            e.printStackTrace();
            return error("load " + filename + ": " + e);
        }
    }

    public LuaValue loadfile(String filename, LuaValue env) {
        try {
            InputStream is = finder.findResource(filename);
            if (is == null)
                return error("cannot load " + filename + ": No such file");
            return load(is, "@" + filename, "bt", env);
        } catch (Exception e) {

            e.printStackTrace();
            return error("load " + filename + ": " + e);
        }
    }

    public LuaValue load(InputStream is, String chunkname, String mode, LuaValue environment) {
        try {
            Prototype p = loadPrototype(is, chunkname, mode);
            return loader.load(p, chunkname, this, environment);
        } catch (LuaError l) {
            throw l;
        } catch (Exception e) {
            e.printStackTrace();
            return error("load " + chunkname + ": " + e);
        }
    }

    public Prototype loadPrototype(InputStream is, String chunkname, String mode) throws IOException {
        if (mode.indexOf('b') >= 0) {
            if (undumper == null)
                error("No undumper.");
            if (!is.markSupported())
                is = new BufferedStream(is);
            is.mark(4);
            final Prototype p = undumper.undump(is, chunkname);
            if (p != null)
                return p;
            is.reset();
        }
        if (mode.indexOf('t') >= 0) {
            return compilePrototype(is, chunkname);
        }
        error("Failed to load prototype " + chunkname + " using mode '" + mode + "'");
        return null;
    }

    public Prototype compilePrototype(Reader reader, String chunkname) throws IOException {
        return compilePrototype(new UTF8Stream(reader), chunkname);
    }

    /**
     * Compile lua source from an InputStream into a Prototype.
     * The input is assumed to be UTf-8, but since bytes in the range 128-255 are passed along as
     * literal bytes, any ASCII-compatible encoding such as ISO 8859-1 may also be used.
     */
    public Prototype compilePrototype(InputStream stream, String chunkname) throws IOException {
        if (compiler == null)
            error("No compiler.");
        return compiler.compile(stream, chunkname, this);
    }

    /**
     * Function which yields the current thread.
     *
     * @param args Arguments to supply as return values in the resume function of the resuming thread.
     * @return Values supplied as arguments to the resume() call that reactivates this thread.
     */
    public Varargs yield(Varargs args) {
        if (running == null || running.isMainThread())
            throw new LuaError("cannot yield main thread");
        final LuaThread.State s = running.state;
        return s.lua_yield(args);
    }

    public interface Loader {
        LuaFunction load(Prototype prototype, String chunkname, Globals globals, LuaValue env) throws IOException;
    }

    public interface Compiler {
        Prototype compile(InputStream stream, String chunkname, Globals globals) throws IOException;
    }

    public interface Undumper {
        Prototype undump(InputStream stream, String chunkname) throws IOException;
    }

    /**
     * Reader implementation to read chars from a String in JME or JSE.
     */
    static class StrReader extends Reader {
        final String s;
        final int n;
        int i = 0;

        StrReader(String s) {
            this.s = s;
            n = s.length();
        }

        public void close() throws IOException {
            i = n;
        }

        public int read() throws IOException {
            return i < n ? s.charAt(i++) : -1;
        }

        public int read(char[] cbuf, int off, int len) throws IOException {
            int j = 0;
            for (; j < len && i < n; ++j, ++i)
                cbuf[off + j] = s.charAt(i);
            return j > 0 || len == 0 ? j : -1;
        }
    }

    static class ByteReader extends Reader {
        final byte[] s;
        final int n;
        int i = 0;

        ByteReader(byte[] s) {
            this.s = s;
            n = s.length;
        }

        public void close() throws IOException {
            i = n;
        }

        public int read() throws IOException {
            return i < n ? s[i++] : -1;
        }

        public int read(char[] cbuf, int off, int len) throws IOException {
            int j = 0;
            for (; j < len && i < n; ++j, ++i)
                cbuf[off + j] = (char) s[i];
            return j > 0 || len == 0 ? j : -1;
        }
    }

    /* Abstract base class to provide basic buffered input storage and delivery.
     * This class may be moved to its own package in the future.
     */
    abstract static class AbstractBufferedStream extends InputStream {
        protected byte[] b;
        protected int i = 0, j = 0;

        protected AbstractBufferedStream(int buflen) {
            this.b = new byte[buflen];
        }

        abstract protected int avail() throws IOException;

        public int read() throws IOException {
            int a = avail();
            return (a <= 0 ? -1 : 0xff & b[i++]);
        }

        public int read(byte[] b) throws IOException {
            return read(b, 0, b.length);
        }

        public int read(byte[] b, int i0, int n) throws IOException {
            int a = avail();
            if (a <= 0) return -1;
            final int n_read = Math.min(a, n);
            System.arraycopy(this.b, i, b, i0, n_read);
            i += n_read;
            return n_read;
        }

        public long skip(long n) throws IOException {
            final long k = Math.min(n, j - i);
            i += k;
            return k;
        }

        public int available() throws IOException {
            return j - i;
        }
    }

    /**
     * Simple converter from Reader to InputStream using UTF8 encoding that will work
     * on both JME and JSE.
     * This class may be moved to its own package in the future.
     */
    static class UTF8Stream extends AbstractBufferedStream {
        private final char[] c = new char[32];
        private final Reader r;

        UTF8Stream(Reader r) {
            super(96);
            this.r = r;
        }

        protected int avail() throws IOException {
            if (i < j) return j - i;
            int n = r.read(c);
            if (n < 0)
                return -1;
            if (n == 0) {
                int u = r.read();
                if (u < 0)
                    return -1;
                c[0] = (char) u;
                n = 1;
            }
            j = LuaString.encodeToUtf8(c, n, b, i = 0);
            return j;
        }

        public void close() throws IOException {
            r.close();
        }
    }

    /**
     * Simple buffered InputStream that supports mark.
     * Used to examine an InputStream for a 4-byte binary lua signature,
     * and fall back to text input when the signature is not found,
     * as well as speed up normal compilation and reading of lua scripts.
     * This class may be moved to its own package in the future.
     */
    static class BufferedStream extends AbstractBufferedStream {
        private final InputStream s;

        public BufferedStream(InputStream s) {
            this(128, s);
        }

        BufferedStream(int buflen, InputStream s) {
            super(buflen);
            this.s = s;
        }

        protected int avail() throws IOException {
            if (i < j) return j - i;
            if (j >= b.length) i = j = 0;
            // leave previous bytes in place to implement mark()/reset().
            int n = s.read(b, j, b.length - j);
            if (n < 0)
                return -1;
            if (n == 0) {
                int u = s.read();
                if (u < 0)
                    return -1;
                b[j] = (byte) u;
                n = 1;
            }
            j += n;
            return n;
        }

        public void close() throws IOException {
            s.close();
        }

        public synchronized void mark(int n) {
            if (i > 0 || n > b.length) {
                byte[] dest = n > b.length ? new byte[n] : b;
                System.arraycopy(b, i, dest, 0, j - i);
                j -= i;
                i = 0;
                b = dest;
            }
        }

        public boolean markSupported() {
            return true;
        }

        public synchronized void reset() throws IOException {
            i = 0;
        }
    }
}
