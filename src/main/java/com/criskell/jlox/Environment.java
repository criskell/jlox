package com.criskell.jlox;

import java.util.HashMap;
import java.util.Map;

public class Environment {
    private Environment enclosing;
    private final Map<String, Object> values = new HashMap<>();

    public static final Object HOLE = new Object();

    Environment() {
        enclosing = null;
    }

    Environment(Environment enclosing) {
        this.enclosing = enclosing;
    }

    public Object get(Token name) {
        if (values.containsKey(name.lexeme)) {
            Object value = values.get(name.lexeme);

            if (value == HOLE) {
                throw new RuntimeError(name, "Variable '" + name.lexeme + "' is not initialized.");
            }
        }

        if (enclosing != null) return enclosing.get(name);

        throw new RuntimeError(name, "Undefined variable '" + name.lexeme + "'.");
    }

    public void define(String name, Object value) {
        values.put(name, value);
    }

    public void assign(Token name, Object value) {
        if (values.containsKey(name.lexeme)) {
            values.put(name.lexeme, value);
            return;
        }

        if (enclosing != null) {
            enclosing.assign(name, value);
            return;
        }

        throw new RuntimeError(name, "Undefined variable '"  + name.lexeme + "'");
    }
}
