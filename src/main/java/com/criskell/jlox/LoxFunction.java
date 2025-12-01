package com.criskell.jlox;

import java.util.List;

public class LoxFunction implements LoxCallable {
    private final Stmt.Function declaration;
    private final Token name;
    private final List<Token> params;
    private final List<Stmt> body;

    /**
     * It is a data structure that closes and holds over the surrounding variables.
     */
    private final Environment closure;

    LoxFunction(Stmt.Function declaration, Environment closure) {
        this.closure = closure;
        this.declaration = declaration;
        this.name = declaration.name;
        this.body = declaration.body;
        this.params = declaration.params;
    }

    LoxFunction(List<Token> params, List<Stmt> body, Environment closure) {
        this.closure = closure;
        this.declaration = null;
        this.name = null;
        this.body = body;
        this.params = params;
    }

    @Override
    public int arity() {
        return params.size();
    }
    
    @Override
    public Object call(Interpreter interpreter, List<Object> arguments) {
        Environment environment = new Environment(closure);

        for (int i = 0; i < params.size(); i++) {
            environment.define(params.get(i).lexeme, arguments.get(i));
        }

        try {
            interpreter.executeBlock(body, environment);
        } catch (Return returnValue) {
            return returnValue.value;
        }

        return null;
    }

    @Override
    public String toString() {
        if (name == null) {
            return "<fn anonymous>";
        }

        return "<fn " + name.lexeme + ">";
    }
}
