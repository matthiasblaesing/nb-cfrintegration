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

import com.sun.source.util.TreePath;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.lang.model.element.Element;
import org.netbeans.api.java.source.ClasspathInfo;
import org.netbeans.api.java.source.CompilationController;
import org.netbeans.api.java.source.ElementHandle;
import org.netbeans.api.java.source.JavaSource;
import org.netbeans.api.java.source.Task;
import org.netbeans.modules.java.BinaryElementOpen;
import org.openide.filesystems.FileObject;
import org.openide.util.Exceptions;


@org.openide.util.lookup.ServiceProvider(
    service=org.netbeans.modules.java.BinaryElementOpen.class,
    position = 10244223
)
public class BinaryElementOpenImpl implements BinaryElementOpen {

    @Override
    public boolean open(ClasspathInfo cpInfo, final ElementHandle<? extends Element> toOpen, final AtomicBoolean cancel) {
        // generate the java source for the target element
        FileObject source = CfrCodeGenerator.generateCode(cpInfo, toOpen);
        // if it worked, try to find the location of the target element
        if (source != null) {
            final int[] pos = new int[] {-1};

            try {
                JavaSource.create(cpInfo, source).runUserActionTask(new Task<CompilationController>() {
                    @Override public void run(CompilationController parameter) throws Exception {
                        if (cancel.get()) return ;
                        parameter.toPhase(JavaSource.Phase.RESOLVED);

                        Element el = toOpen.resolve(parameter);

                        if (el == null) return ;

                        TreePath p = parameter.getTrees().getPath(el);

                        if (p == null) {
                            pos[0] = 0;
                        } else {
                            pos[0] = (int) parameter.getTrees().getSourcePositions().getStartPosition(p.getCompilationUnit(), p.getLeaf());
                        }
                    }
                }, true);
            } catch (IOException ex) {
                Exceptions.printStackTrace(ex);
            }

            // Finally open the source to the target location. If location
            // finding fails, the start of the file will be opened
            if (pos[0] != (-1) && !cancel.get()) {
                return open(source, pos[0]);
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    @SuppressWarnings("deprecation")
    private boolean open(FileObject source, int pos) {
        return org.netbeans.api.java.source.UiUtils.open(source, pos);
    }

}
