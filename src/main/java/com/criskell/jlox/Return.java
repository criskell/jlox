package com.criskell.jlox;

public class Return extends RuntimeException {
    public final Object value;

    Return(Object value) {
        super(null, null, false, false);
        this.value = value;
    }
}
