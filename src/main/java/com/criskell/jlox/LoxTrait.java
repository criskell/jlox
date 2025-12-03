package com.criskell.jlox;

import java.util.Map;

public class LoxTrait {
    public final Token name;
    public final Map<String, LoxFunction> methods;
    
    public LoxTrait(Token name, Map<String, LoxFunction> methods) {
        this.name = name;
        this.methods = methods;
    }

    @Override
    public String toString() {
        return name.lexeme;
    }
}
