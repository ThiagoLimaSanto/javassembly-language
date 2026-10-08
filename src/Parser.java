package src;

import java.util.ArrayList;
import java.util.List;

import src.enums.TokenType;
import src.exceptions.SyntacticException;
import src.interfaces.ExpressionInterface;
import src.interfaces.StatementInterface;
import src.records.BinariaRecord;
import src.records.BlockRecord;
import src.records.ClassRecord;
import src.records.DeclarationRecord;
import src.records.FunctionRecord;
import src.records.LiteralRecord;
import src.records.PrintRecord;
import src.records.ProgramRecord;
import src.records.RegistradorRecord;
import src.records.VariavelRecord;
import src.records.WhileRecord;

public class Parser {
    private final List<Token> tokens;
    private int currentTokenIndex;

    public Parser(List<Token> tokens) {
        this.tokens = tokens;
        this.currentTokenIndex = 0;
    }

    public ProgramRecord analyze() {
        List<ClassRecord> classes = new ArrayList<>();
        classes.add(classDeclaration());

        while (verify(TokenType.CLASS)) {
            classes.add(classDeclaration());
        }

        consumer(TokenType.EOF, "Esperado fim do código");

        return new ProgramRecord(classes);
    }

    private ClassRecord classDeclaration() {
        TokenType visibility = null;
        if (verify(TokenType.PUBLIC) || verify(TokenType.PRIVATE)) {
            visibility = consumer(current().getType(), "Esperada visibilidade da funcao").getType();
        }
        consumer(TokenType.CLASS, "Esperado 'class'");
        Token name = consumer(TokenType.IDENTIFIER, "Esperado o nome da classe");
        consumer(TokenType.OPEN_BRACE, "Esperado '{' apos nome da classe");

        List<FunctionRecord> functions = new ArrayList<>();

        while (!verify(TokenType.CLOSE_BRACE) && !verify(TokenType.EOF)) {
            functions.add(functionDeclaration());
        }

        consumer(TokenType.CLOSE_BRACE, "Esperado '}' para fechar a classe");

        return new ClassRecord(visibility, name.getLexema(), functions);
    }

    private FunctionRecord functionDeclaration() {
        TokenType visibility = null;
        Token typeReturn;
        Token name;

        if (verify(TokenType.PUBLIC) || verify(TokenType.PRIVATE)) {
            visibility = consumer(current().getType(), "Esperada visibilidade da funcao").getType();
        }

        consumer(TokenType.FUNC, "Esperado 'func' para declarar uma funcao");

        if (verify(TokenType.VOID)) {
            typeReturn = consumer(TokenType.VOID, "Esperado 'void'");
        } else {
            typeReturn = type();
        }

        if (verify(TokenType.MAIN)) {
            name = consumer(TokenType.MAIN, "Esperado funcao 'main'");
        } else {
            name = consumer(TokenType.IDENTIFIER, "Esperado nome da funcao");
        }

        consumer(TokenType.OPEN_PARENTHESIS, "Esperado '(' apos nome da funcao");
        consumer(TokenType.CLOSE_PARENTHESIS, "Esperado ')' para fechar a declaracao da funcao");

        BlockRecord block = block();

        return new FunctionRecord(visibility, typeReturn.getType(), name.getLexema(), block);
    }

    private BlockRecord block() {
        consumer(TokenType.OPEN_BRACE, "Esperado '{' para iniciar um bloco");

        List<StatementInterface> commands = new ArrayList<>();

        while (!verify(TokenType.CLOSE_BRACE) && !verify(TokenType.EOF)) {
            commands.add(statement());
        }

        consumer(TokenType.CLOSE_BRACE, "Esperado '}' para fechar o bloco");

        return new BlockRecord(commands);
    }

    private StatementInterface statement() {
        if (verify(TokenType.INT) || verify(TokenType.DOUBLE) || verify(TokenType.BOOLEAN)) {
            return declaration();
        } else if (verify(TokenType.PRINT)) {
            consumer(TokenType.PRINT, "Esperado 'print'");
            ExpressionInterface expression = expression();
            consumer(TokenType.SEMICOLON, "Esperado ';' apos print");
            return new PrintRecord(expression);
        } else if (verify(TokenType.WHILE)) {
            consumer(TokenType.WHILE, "Esperado 'while'");
            consumer(TokenType.OPEN_PARENTHESIS, "Esperado '(' apos while");
            ExpressionInterface expression = expression();
            consumer(TokenType.CLOSE_PARENTHESIS, "Esperado ')' apos condicao");
            BlockRecord block = block();
            return new WhileRecord(expression, block);
        } else {
            throw new SyntacticException(
                    "Esperado declaração, print ou while"
                            + ". Na linha: " + current().getLine()
                            + ", coluna: " + current().getColumn());
        }
    }

    private StatementInterface declaration() {
        Token token = type();
        Token name = consumer(TokenType.IDENTIFIER, "Esperado o nome da variável");

        ExpressionInterface value = null;

        if (verify(TokenType.ASSIGN)) {
            consumer(TokenType.ASSIGN, "Esperado sinal de atribuição '='");
            value = expression();
        }

        consumer(TokenType.SEMICOLON, "Esperado ';' apos declaração");

        return new DeclarationRecord(token.getType(), name.getLexema(), value);
    }

    private ExpressionInterface expression() {
        switch (current().getType()) {
            case TokenType.NUMBER: {
                Token token = consumer(TokenType.NUMBER, "Esperado um número");
                return new LiteralRecord(Integer.parseInt(token.getLexema()));
            }
            case TokenType.STRING: {
                Token token = consumer(TokenType.STRING, "Esperada uma string");
                return new LiteralRecord(token.getLexema());
            }
            case TokenType.IDENTIFIER: {
                Token token = consumer(TokenType.IDENTIFIER, "Esperado um identificador");
                return new VariavelRecord(token.getLexema());
            }
            case TokenType.REGISTER: {
                Token token = consumer(TokenType.REGISTER, "Esperado um registrador");
                return new RegistradorRecord(token.getLexema());
            }
            case TokenType.TRUE:
            case TokenType.FALSE: {
                Token token = consumer(current().getType(), "Esperado um booleano");
                return new LiteralRecord(token.getType() == TokenType.TRUE);
            }

            case TokenType.ADD:
            case TokenType.SUB:
            case TokenType.MUL:
            case TokenType.DIV:
                return operation();

            case TokenType.CMP:
                return comparison();
            case TokenType.OPEN_PARENTHESIS:
                consumer(TokenType.OPEN_PARENTHESIS, "Esperado '('");
                ExpressionInterface expression = expression();
                consumer(TokenType.CLOSE_PARENTHESIS, "Esperado ')'");
                return expression;
            default:
                throw new SyntacticException(
                        "Expressão inválida. Na linha: " + current().getLine() + ", coluna: " + current().getColumn());
        }
    }

    private ExpressionInterface operation() {
        Token operador = consumer(current().getType(), "Esperado operador. 'add, sub, mul, div'");

        ExpressionInterface left = expression();

        consumer(TokenType.COMMA, "Esperado ',' entre os operandos");

        ExpressionInterface right = expression();

        return new BinariaRecord(operador.getType(), left, right);
    }

    private ExpressionInterface comparison() {
        consumer(TokenType.CMP, "Esperado 'cmp'");
        consumer(TokenType.DOT, "Esperado '.' apos cmp");

        Token operador;
        switch (current().getType()) {
            case EQ:
            case NE:
            case LT:
            case GT:
            case LE:
            case GE:
                operador = consumer(current().getType(), "Esperada condicao.");
                break;

            default:
                throw new SyntacticException(
                        "Erro na linha: " + current().getLine() + " Esperado eq, ne, lt, gt, le ou ge");
        }

        ExpressionInterface left = expression();
        consumer(TokenType.COMMA, "Esperado ',' entre os operandos");
        ExpressionInterface right = expression();
        return new BinariaRecord(operador.getType(), left, right);
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

    private Token type() {
        if (verify(TokenType.INT)) {
            return consumer(TokenType.INT, "Esperado 'int'");
        } else if (verify(TokenType.DOUBLE)) {
            return consumer(TokenType.DOUBLE, "Esperado 'double'");
        } else if (verify(TokenType.BOOLEAN)) {
            return consumer(TokenType.BOOLEAN, "Esperado 'boolean'");
        } else {
            throw new SyntacticException("Esperado tipo: int, double ou boolean. Na linha: " + current().getLine()
                    + ", coluna: " + current().getColumn());
        }
    }
}
