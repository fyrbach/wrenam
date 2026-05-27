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
 * Copyright 2014 ForgeRock AS.
 * Portions copyright 2026 Wren Security.
 */

package org.forgerock.openam.scripting.factories;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.script.ScriptEngine;
import javax.script.ScriptException;
import javax.script.SimpleBindings;

import static org.fest.assertions.Assertions.assertThat;
import static org.testng.Assert.fail;

public class GroovyEngineFactoryTest {

    private GroovyEngineFactory testFactory;

    @BeforeMethod
    public void setupFactory() {
        testFactory = new GroovyEngineFactory();
    }

    @Test
    public void shouldReturnScriptEngine() {
        ScriptEngine engine = testFactory.getScriptEngine();
        assertThat(engine).isNotNull();
    }

    @Test
    public void shouldReturnEngineBoundToThisFactory() {
        // Engines obtained from the stock Groovy factory are not interruptable, so the engine must not hand out
        // a factory other than the one that configured it.
        ScriptEngine engine = testFactory.getScriptEngine();

        assertThat(engine.getFactory()).isSameAs(testFactory);
    }

    @Test
    public void shouldAllowScriptToCallItsOwnMethods() throws Exception {
        ScriptEngine engine = testFactory.getScriptEngine();

        Object result = engine.eval("def twice(x) { x * 2 }\n return [1, 2, 3].collect { twice(it) }",
                new SimpleBindings());

        assertThat(result.toString()).isEqualTo("[2, 4, 6]");
    }

    @Test
    public void shouldAllowScriptToCallClosuresBoundAsVariables() throws Exception {
        // Closures held in the bindings are invoked through the same Groovy mechanism as global functions.
        ScriptEngine engine = testFactory.getScriptEngine();

        Object result = engine.eval("greet = { name -> 'hello ' + name }\n"
                + "def call = { it -> greet(it) }\n"
                + "return call('world')", new SimpleBindings());

        assertThat(result).isEqualTo("hello world");
    }

    @Test
    public void shouldNotLeakScriptMethodsIntoLaterEvaluations() throws Exception {
        // Given - a script that defines a method is evaluated first
        ScriptEngine engine = testFactory.getScriptEngine();
        engine.eval("def leakedMethod() { return 'leaked' }\n return null", new SimpleBindings());

        // When - an unrelated script tries to call it
        try {
            engine.eval("return leakedMethod()", new SimpleBindings());

            // Then
            fail("Method defined by a previous script was callable");
        } catch (ScriptException ex) {
            assertThat(ex.getMessage()).contains("leakedMethod");
        }
    }
}
