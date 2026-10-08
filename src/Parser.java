package src;
import java.util.List;

import src.exceptions.SyntacticException;

public class Parser {
    private final List<Token> tokens;
    private int currentTokenIndex;

    public Parser(List<Token> tokens) {
        this.tokens = tokens;
        this.currentTokenIndex = 0;
    }

    public void analyze() {
        classDeclaration();

        while (verify(TokenType.CLASS)) {
            classDeclaration();
        }

        consumer(TokenType.EOF, "Esperado fim do código");
    }

    private void classDeclaration() {
        consumer(TokenType.CLASS, "Esperado 'class'");
        consumer(TokenType.IDENTIFIER, "Esperado o nome da classe");
        consumer(TokenType.OPEN_BRACE, "Esperado '{' apos nome da classe");

        while (!verify(TokenType.CLOSE_BRACE) && !verify(TokenType.EOF)) {
            functionDeclaration();
        }

        consumer(TokenType.CLOSE_BRACE, "Esperado '}' para fechar a classe");
    }

    private void functionDeclaration() {
        if (verify(TokenType.PUBLIC) || verify(TokenType.PRIVATE)) {
            consumer(current().getType(), "Esperada visibilidade da funcao");
        }

        consumer(TokenType.FUNC, "Esperado 'func' para declarar uma funcao");

        if (verify(TokenType.VOID)) {
            consumer(TokenType.VOID, "Esperado 'void'");
        } else {
            type();
        }

        if (verify(TokenType.MAIN)) {
            consumer(TokenType.MAIN, "Esperado funcao 'main'");
        } else {
            consumer(TokenType.IDENTIFIER, "Esperado nome da funcao");
        }

        consumer(TokenType.OPEN_PARENTHESIS, "Esperado '(' apos nome da funcao");
        consumer(TokenType.CLOSE_PARENTHESIS, "Esperado ')'");

        block();
    }

    private void block() {
        consumer(TokenType.OPEN_BRACE, "Esperado '{' para iniciar um bloco");

        while (!verify(TokenType.CLOSE_BRACE) && !verify(TokenType.EOF)) {
            statement();
        }

        consumer(TokenType.CLOSE_BRACE, "Esperado '}' para fechar o bloco");
    }

    private void statement() {
        if (verify(TokenType.INT) || verify(TokenType.DOUBLE) || verify(TokenType.BOOLEAN)) {
            declaration();
        } else if (verify(TokenType.PRINT)) {
            consumer(TokenType.PRINT, "Esperado 'print'");
            expression();
            consumer(TokenType.SEMICOLON, "Esperado ';' apos print");
        } else if (verify(TokenType.WHILE)) {
            consumer(TokenType.WHILE, "Esperado 'while'");
            consumer(TokenType.OPEN_PARENTHESIS, "Esperado '(' apos while");
            expression();
            consumer(TokenType.CLOSE_PARENTHESIS, "Esperado ')' apos condicao");
            block();
        } else {
            throw new SyntacticException(
                    "Esperado declaração, print ou while"
                            + ". Na linha: " + current().getLine()
                            + ", coluna: " + current().getColumn());
        }
    }

    private void declaration() {
        type();

        consumer(TokenType.IDENTIFIER, "Esperado nome da variável");

        if (verify(TokenType.ASSIGN)) {
            consumer(TokenType.ASSIGN, "Esperado sinal de atribuição '='");
            expression();
        }

        consumer(TokenType.SEMICOLON, "Esperado ';' apos declaração");
    }

    private void expression() {
        switch (current().getType()) {
            case TokenType.NUMBER:
            case TokenType.STRING:
            case TokenType.IDENTIFIER:
            case TokenType.REGISTER:
            case TokenType.TRUE:
            case TokenType.FALSE:
                consumer(current().getType(), "Esperada uma expressão");
                break;

            case TokenType.ADD:
            case TokenType.SUB:
            case TokenType.MUL:
            case TokenType.DIV:
                operation();
                break;

            case TokenType.CMP:
                comparison();
                break;
            case TokenType.OPEN_PARENTHESIS:
                consumer(TokenType.OPEN_PARENTHESIS, "Esperado '('");
                expression();
                consumer(TokenType.CLOSE_PARENTHESIS, "Esperado ')'");
                break;
            default:
                throw new SyntacticException(
                        "Expressão inválida. Na linha: " + current().getLine() + ", coluna: " + current().getColumn());
        }
    }

    private void operation() {
        consumer(current().getType(), "Esperado operador. 'add, sub, mul, div'");

        expression();

        consumer(TokenType.COMMA, "Esperado ',' entre os operandos");

        expression();
    }

    private void comparison() {
        consumer(TokenType.CMP, "Esperado 'cmp'");
        consumer(TokenType.DOT, "Esperado '.' apos cmp");

        switch (current().getType()) {
            case EQ:
            case NE:
            case LT:
            case GT:
            case LE:
            case GE:
                consumer(current().getType(), "Esperada condicao.");
                break;

            default:
                throw new SyntacticException("Esperado eq, ne, lt, gt, le ou ge");
        }

        expression();
        consumer(TokenType.COMMA, "Esperado ',' entre os operandos");
        expression();
    }

    private Token current() {
        return tokens.get(currentTokenIndex);
    }

    private boolean verify(TokenType type) {
        return current().getType() == type;
    }

    private Token consumer(TokenType type, String message) {
        if (verify(type)) {
            Token token = current();
            currentTokenIndex++;
            return token;
        }

        throw new SyntacticException(
                message + ". Na linha: " + current().getLine() + ", coluna: " + current().getColumn());
    }

    private void type() {
        if (verify(TokenType.INT)) {
            consumer(TokenType.INT, "Esperado 'int'");
        } else if (verify(TokenType.DOUBLE)) {
            consumer(TokenType.DOUBLE, "Esperado 'double'");
        } else if (verify(TokenType.BOOLEAN)) {
            consumer(TokenType.BOOLEAN, "Esperado 'boolean'");
        } else {
            throw new SyntacticException("Esperado tipo: int, double ou boolean. Na linha: " + current().getLine()
                    + ", coluna: " + current().getColumn());
        }
    }
}
