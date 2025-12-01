SRC := src
OUT := target/classes

SOURCES := $(shell find $(SRC) -name "*.java")

all: build

build: $(SOURCES)
	mkdir -p $(OUT)
	javac -d $(OUT) $(SOURCES)

run:
	@if [ -z "$(FILE)" ]; then \
		echo "Usage: make run FILE=tests/language/loops/while_loop.lox"; \
	else \
		java -cp $(OUT) com.criskell.jlox.Jlox $(FILE); \
	fi

clean:
	rm -rf $(OUT)

generate-ast:
	java ./src/main/java/com/criskell/jlox/tool/GenerateAst.java ./src/main/java/com/criskell/jlox

.PHONY: all build run clean generate-ast