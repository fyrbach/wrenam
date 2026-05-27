/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2026 Wren Security.
 */
package org.forgerock.openam.scripting.factories;

import groovy.lang.GroovyClassLoader;
import java.io.Reader;
import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptException;
import javax.script.SimpleBindings;
import org.codehaus.groovy.jsr223.GroovyScriptEngineImpl;
import org.forgerock.openam.scripting.ChainedBindings;
import org.forgerock.util.Reject;

/**
 * The Groovy script engine used by Wren:AM. Adds evaluation isolation to the stock Groovy JSR-223 engine, which is
 * shared by all scripts of a single script context.
 *
 * @see <a href="https://www.jcp.org/en/jsr/detail?id=223">JSR-223: Scripting for the Java Platform</a>
 */
class GroovyScriptEngine extends GroovyScriptEngineImpl {

    /**
     * Groovy engine setting that determines how strongly the global function namespace holds on to script methods.
     */
    private static final String KEEP_GLOBALS_SETTING = "#jsr223.groovy.engine.keep.globals";

    /**
     * Phantom references are never resolvable, which effectively turns the global function namespace off.
     */
    private static final String GLOBALS_DISABLED = "phantom";

    private final GroovyEngineFactory factory;

    /**
     * Constructs the script engine with the given class loader and parent engine factory.
     *
     * @param classLoader the class loader used to compile scripts. Must not be null.
     * @param factory the parent script engine factory. Must not be null.
     */
    GroovyScriptEngine(GroovyClassLoader classLoader, GroovyEngineFactory factory) {
        super(classLoader);
        Reject.ifNull(factory);
        this.factory = factory;
    }

    /**
     * Returns the factory that created this engine instead of the plain Groovy factory that the superclass would
     * create on demand. Engines obtained from that factory would lack the interrupt customisation and would
     * therefore silently ignore the configured script execution timeout.
     *
     * {@inheritDoc}
     */
    @Override
    public ScriptEngineFactory getFactory() {
        return factory;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object eval(String script, ScriptContext context) throws ScriptException {
        return super.eval(script, isolate(context));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object eval(Reader reader, ScriptContext context) throws ScriptException {
        return super.eval(reader, isolate(context));
    }

    /**
     * Stops the script that is about to be evaluated from contributing to - and from seeing - the engine wide Groovy
     * global function namespace. Groovy registers the methods of every script it evaluates in that namespace, keyed
     * by bare method name and held by strong references. Since Wren:AM evaluates unrelated scripts - possibly
     * belonging to different realms - through a single shared engine, a method defined by one script would otherwise
     * be callable by every script evaluated after it, and the bindings it closes over (session, identity, ...) would
     * be kept alive indefinitely. Scripts can still call their own methods and closures, which are resolved before
     * the global function namespace is consulted.
     *
     * @param context the context the script is going to be evaluated in.
     * @return the given context, with the global function namespace disabled.
     */
    private static ScriptContext isolate(ScriptContext context) {
        final Bindings engineScope = context.getBindings(ScriptContext.ENGINE_SCOPE);
        if (engineScope != null && GLOBALS_DISABLED.equals(engineScope.get(KEEP_GLOBALS_SETTING))) {
            return context;
        }
        final Bindings settings = new SimpleBindings();
        settings.put(KEEP_GLOBALS_SETTING, GLOBALS_DISABLED);
        context.setBindings(engineScope == null ? settings : new ChainedBindings(settings, engineScope),
                ScriptContext.ENGINE_SCOPE);
        return context;
    }
}
