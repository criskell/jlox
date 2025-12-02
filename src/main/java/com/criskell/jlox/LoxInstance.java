package com.criskell.jlox;

import java.util.HashMap;
import java.util.Map;

public class LoxInstance {
    private LoxClass klass;
    private final Map<String, Object> fields = new HashMap<>();

    public LoxInstance(LoxClass klass) {
        this.klass = klass;
    }

    public Object get(Token property) {
        if (!fields.containsKey(property.lexeme)) {
            throw new RuntimeError(property, "Undefined property '" + property.lexeme + "'");
        }

        return fields.get(property.lexeme);
    }

    public void set(Token name, Object value) {
        fields.put(name.lexeme, value);
    }

    @Override
    public String toString() {
        return klass.name + " instance";
    }    
}
