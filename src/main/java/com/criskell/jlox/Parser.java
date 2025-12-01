package com.criskell.jlox;

import java.util.ArrayList;
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
    
    Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    // Translates `program -> statement* EOF;` into recursive descent style 
    List<Stmt> parse() {
        List<Stmt> statements = new ArrayList<>();

        while (!isAtEnd()) {
            statements.add(declaration());
        }

        return statements;
    }

    private Stmt statement() {
        if (match(PRINT)) return printStatement();
        if (match(LEFT_BRACE)) return new Stmt.Block(block());

        return expressionStatement();
    }

    private Stmt printStatement() {
        Expr value = expression();
        consume(SEMICOLON, "Expect ';' after value.");
        return new Stmt.Print(value);
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

    private Stmt expressionStatement() {
        Expr expr = expression();
        consume(SEMICOLON, "Expect ';' after expression.");
        return new Stmt.Expression(expr);
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
        Expr expr = equality();

        if (match(EQUAL)) {
            Token equals = previous();
            Expr value = assignment();

            if (expr instanceof Expr.Variable) {
                Token name = ((Expr.Variable)expr).name;

                return new Expr.Assign(name, value);
            }

            error(equals, "Invalid assignment target.");
        }

        return expr;
    }

    private Expr expression() {
        return assignment();
    }

    private Stmt declaration() {
        try {
            if (match(VAR)) return varDeclaration();

            return statement();
        } catch (ParseError error) {
            synchronize();
            return null;
        }
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

        return primary();
    }

    private Expr primary() {
        if (match(FALSE)) return new Expr.Literal(false);
        if (match(TRUE)) return new Expr.Literal(true);
        if (match(NIL)) return new Expr.Literal(null);

        if (match(NUMBER, STRING)) {
            return new Expr.Literal(previous().literal);
        }

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
