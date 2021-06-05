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

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import org.benf.cfr.reader.api.CfrDriver;
import org.netbeans.api.java.classpath.ClassPath;
import org.netbeans.api.java.source.ClasspathInfo;
import org.netbeans.api.java.source.ClasspathInfo.PathKind;
import org.netbeans.api.java.source.CompilationController;
import org.netbeans.api.java.source.ElementHandle;
import org.netbeans.api.java.source.JavaSource;
import org.netbeans.api.java.source.JavaSource.Phase;
import org.netbeans.api.java.source.Task;
import org.netbeans.spi.java.classpath.support.ClassPathSupport;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.modules.Places;
import org.openide.util.Exceptions;

import static java.util.Arrays.asList;


public class CfrCodeGenerator {
    private static final Logger LOG = Logger.getLogger(CfrCodeGenerator.class.getName());
    private static final Set<ElementKind> UNUSABLE_KINDS = EnumSet.of(ElementKind.PACKAGE);
    private static final byte[] VERSION = new byte[]{-1, 1};
    private static final File SOURCE_CACHE = Places.getCacheSubdirectory("eu.doppel.helix.netbeans.plugin.cfrintegration.CFRIntegration");
    private static final String HASH_ATTRIBUTE_NAME = "origin-hash";
    private static final String DISABLE_ERRORS = "disable-java-errors";
    private static final String CLASSFILE_ROOT = "classfile-root";
    private static final String CLASSFILE_BINNAME = "classfile-binaryName";

    public static FileObject generateCode(final ClasspathInfo cpInfo, final ElementHandle<? extends Element> toOpenHandle) {
	if (UNUSABLE_KINDS.contains(toOpenHandle.getKind())) {
	    return null;
	}

        try {
            final FileObject[] result = new FileObject[1];

            // A JavaSource is needed to be able to resulve the toOpenHandle
            JavaSource.create(cpInfo, Collections.EMPTY_LIST).runUserActionTask(new Task<CompilationController>() {
                @Override
                public void run(CompilationController cc) throws Exception {
                    // Move compiler to right state
                    cc.toPhase(Phase.ELEMENTS_RESOLVED);

                    // Resolve the handle
                    Element toOpen = toOpenHandle.resolve(cc);

                    // Create a classpath from the classpath info, COMPILE path
                    // is placed first so that classes from the binary are
                    // evaluated first
                    final ClassPath cp = ClassPathSupport.createProxyClassPath(
                        cpInfo.getClassPath(PathKind.COMPILE),
                        cpInfo.getClassPath(PathKind.BOOT),
                        cpInfo.getClassPath(PathKind.SOURCE));

                    // Find the containing class for a given handle
                    final String binName;
                    if (toOpen != null && toOpen.getKind() == ElementKind.MODULE) {
                        binName = "module-info"; //NOI18N
                    } else {
                        final TypeElement te = toOpen != null ? cc.getElementUtilities().outermostTypeElement(toOpen) : null;

                        if (te == null) {
                            LOG.log(Level.INFO, "Cannot resolve element: {0} on classpath: {1}", new Object[]{toOpenHandle.toString(), cp.toString()}); //NOI18N
                            return;
                        }

                        binName = te.getQualifiedName().toString().replace('.', '/');  //NOI18N
                    }

                    final String resourceName = binName + ".class";  //NOI18N
                    final FileObject resource = cp.findResource(resourceName);
                    if (resource == null) {
                        LOG.log(Level.INFO, "Cannot find resource: {0} on classpath: {1}", new Object[]{resourceName, cp.toString()}); //NOI18N
                        return ;
                    }

                    final FileObject root = cp.findOwnerRoot(resource);
                    if (root == null) {
                        LOG.log(Level.INFO, "Cannot find owner of: {0} on classpath: {1}", new Object[]{FileUtil.getFileDisplayName(resource), cp.toString()}); //NOI18N
                        return ;
                    }

                    @SuppressWarnings({"ConfusingArrayVararg", "PrimitiveArrayArgumentToVariableArgMethod"})
                    final String rootHash = createHash(root.getPath().getBytes(StandardCharsets.UTF_8));
                    final File  sourceRoot = new File(SOURCE_CACHE, "gensrc/" + rootHash);     //NOI18N
                    sourceRoot.mkdirs();
                    final FileObject sourceRootFO = FileUtil.createFolder(sourceRoot.getCanonicalFile());
                    if (sourceRootFO == null) {
                        LOG.log(Level.INFO, "Cannot create folder: {0}", sourceRoot); //NOI18N
                        return ;
                    }

                    final String path = binName + ".java";   //NOI18N
                    final FileObject source = sourceRootFO.getFileObject(path);

                    // The hash is used to check if the generated souce is
                    // still valid. VERSION allows to detect changes in the
                    // generation scheme, the content of the resource.
                    String hash = createHash(VERSION, resource.asBytes());

                    if (source != null) {
                        result[0] = source;

                        String existingHash = (String) source.getAttribute(HASH_ATTRIBUTE_NAME);

                        if (hash.equals(existingHash)) {
                            LOG.log(Level.FINE, "{0} is up to date, reusing from cache.", FileUtil.getFileDisplayName(source));  //NOI18N
                            return;
                        }
                    }

                    if (source == null) {
                        result[0] = FileUtil.createData(sourceRootFO, path);
                        LOG.log(Level.FINE, "{0} does not exist, creating.", FileUtil.getFileDisplayName(result[0]));  //NOI18N
                    } else {
                        LOG.log(Level.FINE, "{0} is not up to date, regenerating.", FileUtil.getFileDisplayName(source));  //NOI18N
                    }

                    // Prepare CRF decompiler and run decompilation
                    BufferingSink bs = new BufferingSink();
                    CfrDriver driver = new CfrDriver.Builder()
                        .withOutputSink(bs)
                        .withClassFileSource(new ClasspathClassFileSource(cp))
                        .build();

                    driver.analyse(asList(binName));

                    // Ensure the cache file can be written if it already existed
                    FileUtil.toFile(result[0]).setWritable(true);

                    // Write decompiled code to cache file
                    try(OutputStream os = result[0].getOutputStream();
                        OutputStreamWriter osm = new OutputStreamWriter(os, StandardCharsets.UTF_8)) {
                        osm.write(bs.getDecomiledSources().get(0).getJava());
                    }

                    FileUtil.toFile(result[0]).setReadOnly();
                    result[0].setAttribute(DISABLE_ERRORS, true);
                    result[0].setAttribute(CLASSFILE_ROOT, root.toURL());
                    result[0].setAttribute(CLASSFILE_BINNAME, binName);
                    result[0].setAttribute(HASH_ATTRIBUTE_NAME, hash);
                }

            }, false);

            return result[0];
        } catch (IOException ex) {
            Exceptions.printStackTrace(ex);
            return null;
        }
    }

    private static String createHash(byte[]... data) throws IOException, NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("MD5");
        for(byte[] d: data) {
            md.update(d);
        }
        byte[] hashBytes = md.digest();
        StringBuilder hashBuilder = new StringBuilder();
        for (byte b : hashBytes) {
            hashBuilder.append(String.format("%02X", b));
        }
        String hash = hashBuilder.toString();
        return hash;
    }
}
