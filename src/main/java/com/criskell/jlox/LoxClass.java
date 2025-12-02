package com.criskell.jlox;

public class LoxClass {
    private final String name;

    LoxClass(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }
}
