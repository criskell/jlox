package com.criskell.jlox;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

import static com.criskell.jlox.TokenType.*;

// It is a recursive descent parser, where each production becomes a method, which can be
// directly translated into imperative code.
// Grammars with left recursion are problematic for this type of parser, because the method definition
// for this type of rule will recurse infinitely.
public class Parser {
    private static class ParseError extends RuntimeException {}

    private final List<Token> tokens;
    private int current = 0;
    private int loopDepth = 0;
    
    Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    // Translates `program -> statement* EOF;` into recursive descent style 
    public List<Stmt> parse() {
        List<Stmt> statements = new ArrayList<>();

        while (!isAtEnd()) {
            statements.add(declaration());
        }

        return statements;
    }

    private Stmt statement() {
        if (match(FOR)) return forStatement();
        if (match(BREAK)) return breakStatement();
        if (match(IF)) return ifStatement();
        if (match(PRINT)) return printStatement();
        if (match(RETURN)) return returnStatement();
        if (match(WHILE)) return whileStatement();
        if (match(LEFT_BRACE)) return new Stmt.Block(block());

        return expressionStatement();
    }

    private Stmt forStatement() {
        consume(LEFT_PAREN, "Expect '(' after 'for'.");

        Stmt initializer;

        if (match(SEMICOLON)) {
            initializer = null;
        } else if (match(VAR)) {
            initializer = varDeclaration();
        } else {
            initializer = expressionStatement();
        }

        Expr condition = null;

        if (!check(SEMICOLON)) {
            condition = expression();
        }

        consume(SEMICOLON, "Expect ';' after loop condition.");

        Expr increment = null;

        if (!check(RIGHT_PAREN)) {
            increment = expression();
        }

        consume(RIGHT_PAREN, "Expect ')' after for clauses.");
        Stmt body = statement();

        if (increment != null) {
            body = new Stmt.Block(Arrays.asList(body, new Stmt.Expression(increment)));
        }

        if (condition == null) {
            condition = new Expr.Literal(true);
        }

        body = new Stmt.While(condition, body);

        if (initializer != null) {
            body = new Stmt.Block(Arrays.asList(initializer, body));
        }

        return body;
    }

    private Stmt ifStatement() {
        consume(LEFT_PAREN, "Expect '(' after 'if'.");
        Expr condition = expression();
        consume(RIGHT_PAREN, "Expect ')' after if condition.");

        Stmt thenBranch = statement();
        Stmt elseBranch = null;
        
        if (match(ELSE)) {
            elseBranch = statement();
        }

        return new Stmt.If(condition, thenBranch, elseBranch);
    }

    private Stmt printStatement() {
        Expr value = expression();
        consume(SEMICOLON, "Expect ';' after value.");
        return new Stmt.Print(value);
    }

    private Stmt returnStatement() {
        Token keyword = previous();
        Expr value = null;

        if (!check(SEMICOLON)) {
            value = expression();
        }

        consume(SEMICOLON, "Expect ';' after return value.");

        return new Stmt.Return(keyword, value);
    }

    private Stmt varDeclaration() {
        Token name = consume(IDENTIFIER, "Expect variable name.");

        Expr initializer = null;

        if (match(EQUAL)) {
            initializer = expression();
        }

        consume(SEMICOLON, "Expect ';' after variable declaration.");
        return new Stmt.Var(name, initializer);
    }

    private Stmt whileStatement() {
        loopDepth++;

        try {
            consume(LEFT_PAREN, "Expect '(' after 'while'.");
            Expr condition = expression();
            consume(RIGHT_PAREN, "Expect ')' after condition.");
            Stmt body = statement();

            return new Stmt.While(condition, body);
        } finally {
            loopDepth--;
        }
    }

    private Stmt breakStatement() {
        if (loopDepth == 0) {
            error(previous(), "Cannot use 'break' outside of a loop.");
        }

        consume(SEMICOLON, "Expect ';' after 'break'.");
        return new Stmt.Break();
    }

    private Stmt expressionStatement() {
        Expr expr = expression();
        consume(SEMICOLON, "Expect ';' after expression.");
        return new Stmt.Expression(expr);
    }

    private Stmt.Function function(String kind) {
        Token name = consume(IDENTIFIER, "Expect " + kind + " name.");
        consume(LEFT_PAREN, "Expect '(' after " + kind + " name.");

        List<Token> parameters = new ArrayList<>();
        if (!check(RIGHT_PAREN)) {
            do {
                if (parameters.size() >= 255) {
                    error(peek(), "Can't have more than 255 parameters.");
                }

                parameters.add(consume(IDENTIFIER, "Expect parameter name."));
            } while (match(COMMA));
        }
        consume(RIGHT_PAREN, "Expect ')' after parameters.");

        consume(LEFT_BRACE, "Expect '{' before " + kind + " body.");
        List<Stmt> body = block();

        return new Stmt.Function(name, parameters, body);
    }

    private List<Stmt> block() {
        List<Stmt> statements = new ArrayList<>();

        while (!check(RIGHT_BRACE) && !isAtEnd()) {
            statements.add(declaration());
        }

        consume(RIGHT_BRACE, "Expect '}' after block.");
        return statements;
    }

    private Expr assignment() {
        // Analyzing the left-hand side of an assignment requires an unlimited number of lookaheads.
        Expr expr = or();

        if (match(EQUAL)) {
            Token equals = previous();
            Expr value = assignment();

            if (expr instanceof Expr.Variable) {
                Token name = ((Expr.Variable)expr).name;

                return new Expr.Assign(name, value);
            } else if (expr instanceof Expr.Get) {
                Expr.Get get = (Expr.Get)expr;
                return new Expr.Set(get.object, get.name, value);
            }

            error(equals, "Invalid assignment target.");
        }

        return expr;
    }

    private Expr or() {
        Expr expr = and();

        while (match(OR)) {
            Token operator = previous();
            Expr right = and();

            expr = new Expr.Logical(expr, operator, right);
        }

        return expr;
    }

    private Expr and() {
        Expr expr = equality();

        while (match(AND)) {
            Token operator = previous();
            Expr right = equality();

            expr = new Expr.Logical(expr, operator, right);
        }

        return expr;
    }

    private Expr expression() {
        return assignment();
    }

    private Stmt declaration() {
        try {
            if (match(CLASS)) return classDeclaration();
            if (match(FUN) && !check(LEFT_PAREN)) return function("function");
            if (match(VAR)) return varDeclaration();

            return statement();
        } catch (ParseError error) {
            synchronize();
            return null;
        }
    }

    private Stmt classDeclaration() {
        Token name = consume(IDENTIFIER, "Expect class name.");
        consume(LEFT_BRACE, "Expect '{' before class body.");

        List<Stmt.Function> methods = new ArrayList<>();

        while (!check(RIGHT_BRACE) && !isAtEnd()) {
            methods.add(function("method"));
        }

        consume(RIGHT_BRACE, "Expect '}' after class body.");

        return new Stmt.Class(name, methods);
    }

    private Expr comma() {
        Expr expr = ternary();

        while (match(COMMA)) {
            Token operator = previous();
            Expr right = ternary();
            
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    private Expr ternary() {
        Expr expr = equality();

        if (match(QUESTION)) {
            Expr thenBranch = expression();

            consume(COLON, "Expect ':' after then branch of ternary expression.");
            // Right recursion = right associativity.
            Expr elseBranch = ternary();

            expr = new Expr.Ternary(expr, thenBranch, elseBranch); 
        }

        return expr;
    }

    private boolean match(TokenType... types) {
        for (TokenType type : types) {
            if (check(type)) {
                advance();
                return true;
            }
        }

        return false;
    }

    private Token consume(TokenType type, String message)
    {
        if (check(type)) return advance();

        throw error(peek(), message);
    }

    private boolean check(TokenType type) {
        if (isAtEnd()) return false;
        return peek().type == type;
    }

    // Consumes the current token and returns it.
    private Token advance() {
        if (!isAtEnd()) current++;
        return previous();
    }

    private boolean isAtEnd() {
        return peek().type == EOF;
    }

    private Token peek() {
        return tokens.get(current);
    }

    // Returns the most recently consumed token.
    private Token previous() {
        return tokens.get(current - 1);
    }

    private ParseError error(Token token, String message) {
        Jlox.error(token, message);
        return new ParseError();
    }

    // We will discard tokens until the start of the next statement.
    // This is when we have a ParseError, and we need to synchronize the token flow, removing invalid tokens
    // that could be caused by a cascade of errors.    
    // We call this function after catching an error.
    private void synchronize() {
        advance();

        while (!isAtEnd()) {
            if (previous().type == SEMICOLON) return;

            switch (peek().type) {
                case CLASS:
                case FUN:
                case VAR:
                case FOR:
                case IF:
                case WHILE:
                case PRINT:
                case RETURN:
                    return;
            }

            advance();
        }
    }

    // Translates `equality -> comparison ( ( "!=" | "==" ) comparison )* ;`.
    private Expr equality() {
        if (match(BANG_EQUAL, EQUAL_EQUAL)) {
            Token operator = previous();
            Expr right = comparison();

            error(operator, "Binary operator '" + operator.lexeme + "' missing left-hand operand.");
            return right;
        }

        // We obtain the first left operand.
        Expr expr = comparison();

        while (match(BANG_EQUAL, EQUAL_EQUAL)) {
            Token operator = previous();

            // Let's get the operand from the right-hand side.
            Expr right = comparison();

            // We combine the operator and operands into a new node in the syntax tree.
            expr = new Expr.Binary(expr, operator, right);

            // We will use the new expr as the left operand in the next iterations.
        }

        // If we don't enter the loop, we'll effectively just call the `comparison` rule.
        // In this way, the method will match an equality operator or something of higher precedence.

        return expr;
    }

    // Translates `comparison -> term ( ( ">" | ">=" | "<" | "<=" ) term )* ;`.
    private Expr comparison() {
        if (match(GREATER, GREATER_EQUAL, LESS, LESS_EQUAL)) {
            Token operator = previous();
            Expr right = term();
            
            error(operator, "Binary operator '" + operator.lexeme + "' missing left-hand operand.");
            return right;
        }

        Expr expr = term();

        while (match(GREATER, GREATER_EQUAL, LESS, LESS_EQUAL)) {
            Token operator = previous();
            Expr right = term();

            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    // Left-associative
    private Expr term() {
        if (match(MINUS, PLUS)) {
            Token operator = previous();
            Expr right = factor();

            if (operator.type == PLUS) {
                error(operator, "Binary operator '+' missing left-hand operand.");
            } else {
                return new Expr.Unary(operator, right);
            }
            
            return right;
        }

        Expr expr = factor();

        while (match(MINUS, PLUS)) {
            Token operator = previous();
            Expr right = factor();

            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    private Expr factor() {
        if (match(SLASH, STAR)) {
            Token operator = previous();
            Expr right = unary();

            error(operator, "Binary operator '" + operator.lexeme + "' missing left-hand operand.");
            return right;
        }

        Expr expr = unary();

        while (match(SLASH, STAR)) {
            Token operator = previous();
            Expr right = unary();

            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    // Translates:
    // `unary -> ( "!" | "-" ) unary | primary`
    private Expr unary() {
        // Recursive descent parsers belong to the predictive parser category because
        // they look at tokens further ahead (lookahead) to decide how to proceed with the analysis.
        if (match(BANG, MINUS)) {
            Token operator = previous();

            Expr right = unary();
            return new Expr.Unary(operator, right);
        }

        return call();
    }

    private Expr finishCall(Expr callee) {
        List<Expr> arguments = new ArrayList<>();

        if (!check(RIGHT_PAREN)) {
            do {
                if (arguments.size() >= 255) {
                    error(peek(), "Can't have more than 255 arguments.");
                }

                arguments.add(expression());
            } while (match(COMMA));
        }

        Token paren = consume(RIGHT_PAREN, "Expect ')' after arguments.");

        return new Expr.Call(callee, paren, arguments);
    }

    private Expr call() {
        Expr expr = primary();

        while (true) {
            if (match(LEFT_PAREN)) {
                expr = finishCall(expr);
            } else if (match(DOT)) {
                Token name = consume(IDENTIFIER, "Expected property name after '.'.");
                expr = new Expr.Get(expr, name);
            } else {
                break;
            }
        }

        return expr;
    }

    private Expr primary() {
        if (match(FUN) || previous().type == TokenType.FUN) return anonymousFunction();
        if (match(FALSE)) return new Expr.Literal(false);
        if (match(TRUE)) return new Expr.Literal(true);
        if (match(NIL)) return new Expr.Literal(null);

        if (match(NUMBER, STRING)) {
            return new Expr.Literal(previous().literal);
        }

        if (match(THIS)) return new Expr.This(previous());

        if (match(IDENTIFIER)) {
            return new Expr.Variable(previous());
        }

        if (match(LEFT_PAREN)) {
            Expr expr = expression();
            consume(RIGHT_PAREN, "Expect ')' after expression.");
            return new Expr.Grouping(expr);
        }

        // We are dealing with a token that cannot begin an expression.
        throw error(peek(), "Expect expression.");
    }

    private Expr anonymousFunction() {
        consume(LEFT_PAREN, "Expect '(' after 'fun'.");

        List<Token> parameters = new ArrayList<>();
        if (!check(RIGHT_PAREN)) {
            do {
                parameters.add(consume(IDENTIFIER, "Expect parameter name."));
            } while (match(COMMA));
        }
        consume(RIGHT_PAREN, "Expect ')' after parameters.");

        consume(LEFT_BRACE, "Expect '{' before function body.");
        List<Stmt> block = block();

        return new Expr.AnonymousFunction(parameters, block);
    }

    // Supplier is a Java functional interface that represents a function that does not
    // receive any parameters and returns a value.
    private Expr parseLeftAssociative(Supplier<Expr> operandParser, TokenType... operators) {
        Expr expr = operandParser.get();

        while (match(operators)) {
            Token operator = previous();
            Expr right = operandParser.get();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }
}
