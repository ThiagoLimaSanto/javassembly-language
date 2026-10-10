package src.parser;

import src.lexer.Token;

import java.util.ArrayList;
import java.util.List;

import src.lexer.TokenType;
import src.exceptions.SyntacticException;
import src.ast.ExpressionInterface;
import src.ast.StatementInterface;
import src.ast.records.BinaryExpressionRecord;
import src.ast.records.BlockRecord;
import src.ast.records.CallRecord;
import src.ast.records.ClassRecord;
import src.ast.records.DeclarationRecord;
import src.ast.records.ExpressionStatementRecord;
import src.ast.records.FunctionRecord;
import src.ast.records.JERecord;
import src.ast.records.JMPRecord;
import src.ast.records.LabelRecord;
import src.ast.records.LiteralRecord;
import src.ast.records.ParameterRecord;
import src.ast.records.PopRecord;
import src.ast.records.PrintRecord;
import src.ast.records.ProgramRecord;
import src.ast.records.PushRecord;
import src.ast.records.RegisterRecord;
import src.ast.records.ReturnRecord;
import src.ast.records.VariableRecord;
import src.ast.records.WhileRecord;

public class Parser {
    private final TokenStream tokens;

    public Parser(List<Token> tokens) {
        this.tokens = new TokenStream(tokens);
    }

    public ProgramRecord analyze() {
        List<ClassRecord> classes = new ArrayList<>();
        while (tokens.verify(TokenType.CLASS)
                || tokens.verify(TokenType.PUBLIC)
                || tokens.verify(TokenType.PRIVATE)) {
            classes.add(classDeclaration());
        }

        tokens.consume(TokenType.EOF, "Esperado fim do código");

        return new ProgramRecord(classes);
    }

    private ClassRecord classDeclaration() {
        TokenType visibility = null;
        if (tokens.verify(TokenType.PUBLIC) || tokens.verify(TokenType.PRIVATE)) {
            visibility = tokens.consume(tokens.current().getType(), "Esperada visibilidade da funcao").getType();
        }
        tokens.consume(TokenType.CLASS, "Esperado 'class'");
        Token name = tokens.consume(TokenType.IDENTIFIER, "Esperado o nome da classe");
        tokens.consume(TokenType.OPEN_BRACE, "Esperado '{' apos nome da classe");

        List<FunctionRecord> functions = new ArrayList<>();

        while (!tokens.verify(TokenType.CLOSE_BRACE) && !tokens.verify(TokenType.EOF)) {
            functions.add(functionDeclaration());
        }

        tokens.consume(TokenType.CLOSE_BRACE, "Esperado '}' para fechar a classe");

        return new ClassRecord(visibility, name.getLexeme(), functions);
    }

    private FunctionRecord functionDeclaration() {
        TokenType visibility = null;
        Token returnType;
        Token name;

        if (tokens.verify(TokenType.PUBLIC) || tokens.verify(TokenType.PRIVATE)) {
            visibility = tokens.consume(tokens.current().getType(), "Esperada visibilidade da funcao").getType();
        }

        tokens.consume(TokenType.FUNC, "Esperado 'func' para declarar uma funcao");

        if (tokens.verify(TokenType.VOID)) {
            returnType = tokens.consume(TokenType.VOID, "Esperado 'void'");
        } else {
            returnType = type();
        }

        if (tokens.verify(TokenType.MAIN)) {
            name = tokens.consume(TokenType.MAIN, "Esperado funcao 'main'");
        } else {
            name = tokens.consume(TokenType.IDENTIFIER, "Esperado nome da funcao");
        }

        tokens.consume(TokenType.OPEN_PARENTHESIS, "Esperado '(' apos nome da funcao");

        List<ParameterRecord> parameters = parameters();

        tokens.consume(TokenType.CLOSE_PARENTHESIS, "Esperado ')' para fechar a declaracao da funcao");

        BlockRecord block = block();

        return new FunctionRecord(visibility, returnType.getType(), name.getLexeme(), parameters, block);
    }

    private List<ParameterRecord> parameters() {
        List<ParameterRecord> parameters = new ArrayList<>();

        if (tokens.verify(TokenType.CLOSE_PARENTHESIS)) {
            return parameters;
        }

        do {
            Token type = type();
            Token name = tokens.consume(TokenType.IDENTIFIER, "Esperado o nome do parametro");

            parameters.add(new ParameterRecord(type.getType(), name.getLexeme()));

            if (!tokens.verify(TokenType.COMMA)) {
                break;
            }

            tokens.consume(TokenType.COMMA, "Esperado ',' entre os parametros");
        } while (true);

        return parameters;
    }

    private ReturnRecord returnStatement() {
        tokens.consume(TokenType.RETURN, "Esperado 'return'");

        ExpressionInterface value = null;

        if (!tokens.verify(TokenType.SEMICOLON)) {
            value = expression();
        }

        tokens.consume(TokenType.SEMICOLON, "Esperado ';' apos return");

        return new ReturnRecord(value);
    }

    private CallRecord functionCall(String name) {
        tokens.consume(TokenType.OPEN_PARENTHESIS, "Esperado '(' para chamar a função");

        List<ExpressionInterface> arguments = arguments();

        tokens.consume(TokenType.CLOSE_PARENTHESIS, "Esperado ')' para fechar a chamada da função");

        return new CallRecord(name, arguments);
    }

    private List<ExpressionInterface> arguments() {
        List<ExpressionInterface> arguments = new ArrayList<>();

        if (tokens.verify(TokenType.CLOSE_PARENTHESIS)) {
            return arguments;
        }

        do {
            arguments.add(expression());

            if (!tokens.verify(TokenType.COMMA)) {
                break;
            }

            tokens.consume(TokenType.COMMA, "Esperado ',' entre os argumentos");
        } while (true);

        return arguments;
    }

    private BlockRecord block() {
        tokens.consume(TokenType.OPEN_BRACE, "Esperado '{' para iniciar um bloco");

        List<StatementInterface> commands = new ArrayList<>();

        while (!tokens.verify(TokenType.CLOSE_BRACE) && !tokens.verify(TokenType.EOF)) {
            commands.add(statement());
        }

        tokens.consume(TokenType.CLOSE_BRACE, "Esperado '}' para fechar o bloco");

        return new BlockRecord(commands);
    }

    private StatementInterface statement() {
        return switch (tokens.current().getType()) {
            case TokenType.INT, TokenType.DOUBLE, TokenType.BOOLEAN, TokenType.STRING -> declaration();
            case TokenType.PRINT -> printStatement();
            case TokenType.WHILE -> whileStatement();
            case TokenType.RETURN -> returnStatement();
            case TokenType.PUSH -> pushStatement();
            case TokenType.POP -> popStatement();
            case TokenType.IDENTIFIER -> identifierStatement();
            case TokenType.JMP -> jumpStatement();
            case TokenType.JE -> jumpEqualStatement();
            case TokenType.CMP -> comparisonStatement();
            default -> throw new SyntacticException(
                    "Comando inválido ou não reconhecido"
                            + ". Na linha: " + tokens.current().getLine()
                            + ", coluna: " + tokens.current().getColumn());
        };
    }

    private StatementInterface comparisonStatement() {
        ExpressionInterface comparison = comparison();

        tokens.consume(
                TokenType.SEMICOLON,
                "Esperado ';' apos comparação");

        return new ExpressionStatementRecord(comparison);
    }

    private StatementInterface identifierStatement() {
        Token name = tokens.consume(TokenType.IDENTIFIER, "Esperado um identificador");

        if (tokens.verify(TokenType.COLON)) {
            tokens.consume(TokenType.COLON, "Esperado ':' apos identificador");
            return new LabelRecord(name.getLexeme());
        }

        CallRecord call = functionCall(name.getLexeme());

        tokens.consume(TokenType.SEMICOLON, "Esperado ';' apos chamada de função");

        return new ExpressionStatementRecord(call);
    }

    private StatementInterface whileStatement() {
        tokens.consume(TokenType.WHILE, "Esperado 'while'");
        tokens.consume(TokenType.OPEN_PARENTHESIS, "Esperado '(' apos while");
        ExpressionInterface expression = expression();
        tokens.consume(TokenType.CLOSE_PARENTHESIS, "Esperado ')' apos condicao");
        BlockRecord block = block();
        return new WhileRecord(expression, block);
    }

    private StatementInterface printStatement() {
        tokens.consume(TokenType.PRINT, "Esperado 'print'");
        ExpressionInterface expression = expression();
        tokens.consume(TokenType.SEMICOLON, "Esperado ';' apos print");
        return new PrintRecord(expression);
    }

    private PushRecord pushStatement() {
        Token type = tokens.consume(TokenType.PUSH, "Esperado 'push'");
        Token register = tokens.consume(TokenType.REGISTER, "Esperado um registrador");

        tokens.consume(TokenType.SEMICOLON, "Esperado ';' apos push");

        return new PushRecord(type.getType(), new RegisterRecord(register.getLexeme()));
    }

    private PopRecord popStatement() {
        Token type = tokens.consume(TokenType.POP, "Esperado 'pop'");
        Token register = tokens.consume(TokenType.REGISTER, "Esperado um registrador");

        tokens.consume(TokenType.SEMICOLON, "Esperado ';' apos pop");

        return new PopRecord(type.getType(), new RegisterRecord(register.getLexeme()));
    }

    private JMPRecord jumpStatement() {
        tokens.consume(TokenType.JMP, "Esperado 'jmp'");
        Token name = tokens.consume(TokenType.IDENTIFIER, "Esperado um identificador");

        tokens.consume(TokenType.SEMICOLON, "Esperado ';' apos jmp");

        return new JMPRecord(name.getLexeme());
    }

    private JERecord jumpEqualStatement() {
        tokens.consume(TokenType.JE, "Esperado 'je'");
        Token name = tokens.consume(TokenType.IDENTIFIER, "Esperado um identificador");

        tokens.consume(TokenType.SEMICOLON, "Esperado ';' apos je");

        return new JERecord(name.getLexeme());
    }

    private StatementInterface declaration() {
        Token token = type();
        Token name = tokens.consume(TokenType.IDENTIFIER, "Esperado o nome da variável");

        ExpressionInterface value = null;

        if (tokens.verify(TokenType.ASSIGN)) {
            tokens.consume(TokenType.ASSIGN, "Esperado sinal de atribuição '='");
            value = expression();
        }

        tokens.consume(TokenType.SEMICOLON, "Esperado ';' apos declaração");

        return new DeclarationRecord(token.getType(), name.getLexeme(), value);
    }

    private ExpressionInterface expression() {
        switch (tokens.current().getType()) {
            case TokenType.NUMBER: {
                Token token = tokens.consume(TokenType.NUMBER, "Esperado um número");
                if (token.getLexeme().contains(".")) {
                    double value = Double.parseDouble(token.getLexeme());

                    if (!Double.isFinite(value)) {
                        throw new SyntacticException(
                                "Literal decimal fora do intervalo de double: "
                                        + token.getLexeme()
                                        + ". Na linha: " + token.getLine()
                                        + ", coluna: " + token.getColumn());
                    }

                    return new LiteralRecord(value);
                }
                try {
                    int value = Integer.parseInt(token.getLexeme());
                    return new LiteralRecord(value);
                } catch (NumberFormatException e) {
                    throw new SyntacticException(
                            "Literal inteiro fora do intervalo de 32 bits: "
                                    + token.getLexeme()
                                    + ". Na linha: " + token.getLine()
                                    + ", coluna: " + token.getColumn());
                }
            }
            case TokenType.TEXT: {
                Token token = tokens.consume(TokenType.TEXT, "Esperado um literal de texto");
                return new LiteralRecord(token.getLexeme());
            }
            case TokenType.IDENTIFIER: {
                Token name = tokens.consume(TokenType.IDENTIFIER, "Esperado um identificador");

                if (tokens.verify(TokenType.OPEN_PARENTHESIS)) {
                    return functionCall(name.getLexeme());
                }
                return new VariableRecord(name.getLexeme());
            }
            case TokenType.REGISTER: {
                Token token = tokens.consume(TokenType.REGISTER, "Esperado um registrador");
                return new RegisterRecord(token.getLexeme());
            }
            case TokenType.TRUE:
            case TokenType.FALSE: {
                Token token = tokens.consume(tokens.current().getType(), "Esperado um booleano");
                return new LiteralRecord(token.getType() == TokenType.TRUE);
            }

            case TokenType.ADD:
            case TokenType.SUB:
            case TokenType.MUL:
            case TokenType.DIV:
            case TokenType.AND:
            case TokenType.OR:
                return operation();

            case TokenType.CMP:
                return comparison();
            case TokenType.OPEN_PARENTHESIS:
                tokens.consume(TokenType.OPEN_PARENTHESIS, "Esperado '('");
                ExpressionInterface expression = expression();
                tokens.consume(TokenType.CLOSE_PARENTHESIS, "Esperado ')'");
                return expression;
            default:
                throw new SyntacticException(
                        "Expressão inválida. Na linha: " + tokens.current().getLine() + ", coluna: "
                                + tokens.current().getColumn());
        }
    }

    private ExpressionInterface operation() {
        Token operator = tokens.consume(tokens.current().getType(),
                "Esperado operador. 'add, sub, mul, div, and, or'");

        ExpressionInterface left = expression();

        tokens.consume(TokenType.COMMA, "Esperado ',' entre os operandos");

        ExpressionInterface right = expression();

        return new BinaryExpressionRecord(operator.getType(), left, right);
    }

    private ExpressionInterface comparison() {
        tokens.consume(TokenType.CMP, "Esperado 'cmp'");
        tokens.consume(TokenType.DOT, "Esperado '.' apos cmp");

        Token operator;
        switch (tokens.current().getType()) {
            case EQ:
            case NE:
            case LT:
            case GT:
            case LE:
            case GE:
                operator = tokens.consume(tokens.current().getType(), "Esperada condicao.");
                break;

            default:
                throw new SyntacticException(
                        "Erro na linha: " + tokens.current().getLine() + " Esperado eq, ne, lt, gt, le ou ge");
        }

        ExpressionInterface left = expression();
        tokens.consume(TokenType.COMMA, "Esperado ',' entre os operandos");
        ExpressionInterface right = expression();
        return new BinaryExpressionRecord(operator.getType(), left, right);
    }

    private Token type() {
        if (tokens.verify(TokenType.INT)) {
            return tokens.consume(TokenType.INT, "Esperado 'int'");
        } else if (tokens.verify(TokenType.DOUBLE)) {
            return tokens.consume(TokenType.DOUBLE, "Esperado 'double'");
        } else if (tokens.verify(TokenType.BOOLEAN)) {
            return tokens.consume(TokenType.BOOLEAN, "Esperado 'boolean'");
        } else if (tokens.verify(TokenType.STRING)) {
            return tokens.consume(TokenType.STRING, "Esperado 'String'");
        } else {
            throw new SyntacticException(
                    "Esperado tipo: int, double, boolean ou String. Na linha: " + tokens.current().getLine()
                            + ", coluna: " + tokens.current().getColumn());
        }
    }
}
