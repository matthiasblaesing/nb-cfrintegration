/*
 * Copyright 2021 Matthias Bläsing
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package eu.doppelhelix.netbeans.plugin.cfrintegration;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collection;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.benf.cfr.reader.api.ClassFileSource;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.Pair;
import org.netbeans.api.java.classpath.ClassPath;
import org.openide.filesystems.FileObject;

/**
 * ClassFileSource based on the netbeans ClassPath data.
 */
public class ClasspathClassFileSource implements ClassFileSource {

    private static final Logger LOG = Logger.getLogger(ClasspathClassFileSource.class.getName());

    private final ClassPath cp;

    public ClasspathClassFileSource(ClassPath cp) {
        this.cp = cp;
    }

    @Override
    public void informAnalysisRelativePathDetail(String usePath, String classFilePath) {
        LOG.log(Level.INFO, "informAnalysisRelativePathDetail({0}, {1})",
            new Object[]{usePath, classFilePath});
    }

    @Override
    public Collection<String> addJar(String string) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public String getPossiblyRenamedPath(String string) {
        return string;
    }

    @Override
    public Pair<byte[], String> getClassFileContent(String name) throws IOException {
        FileObject fo = cp.findResource(name);
        if(fo == null) {
            throw new FileNotFoundException(name);
        }
        return Pair.make(fo.asBytes(), name);
    }

}
