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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.benf.cfr.reader.api.OutputSinkFactory;
import org.benf.cfr.reader.api.SinkReturns.Decompiled;

/**
 * The BufferingSink is intended to collect {@code Decompiled} results from the
 * decompilation process. It is expected, that exactly one result is created.
 */
public class BufferingSink implements OutputSinkFactory {

    private static final Logger LOG = Logger.getLogger(BufferingSink.class.getName());

    @Override
    public List<SinkClass> getSupportedSinks(SinkType sinkType, Collection<SinkClass> available) {
        LOG.log(Level.FINE, "getSupportSinks({0}, {1})", new Object[]{sinkType, available});
        if (sinkType == SinkType.JAVA && available.contains(SinkClass.DECOMPILED)) {
            // I'd like "Decompiled".  If you can't do that, I'll take STRING.
            return Arrays.asList(SinkClass.DECOMPILED, SinkClass.STRING);
        } else {
            // I only understand how to sink strings, regardless of what you have to give me.
            return Collections.singletonList(SinkClass.STRING);
        }
    }

    @Override
    public <T> Sink<T> getSink(SinkType sinkType, SinkClass sinkClass) {
        LOG.log(Level.FINE, "getSupportSinks({0}, {1})", new Object[]{sinkType, sinkClass});
        if (sinkType == SinkType.JAVA && sinkClass == SinkClass.DECOMPILED) {
            return x -> decomiledSources.add((Decompiled) x);
        } else if (sinkType == SinkType.EXCEPTION && sinkClass == SinkClass.STRING) {
            return msg -> LOG.warning((String) msg);
        } else {
            return msg -> LOG.info((String) msg);
        }
    }

    private List<Decompiled> decomiledSources = new ArrayList<>();

    public List<Decompiled> getDecomiledSources() {
        return decomiledSources;
    }
}
