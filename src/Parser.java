package src;

import java.util.ArrayList;
import java.util.List;

import src.enums.TokenType;
import src.exceptions.SyntacticException;
import src.interfaces.ExpressionInterface;
import src.interfaces.StatementInterface;
import src.records.BinariaRecord;
import src.records.BlockRecord;
import src.records.CallRecord;
import src.records.ClassRecord;
import src.records.DeclarationRecord;
import src.records.ExpressionStatementRecord;
import src.records.FunctionRecord;
import src.records.JERecord;
import src.records.JMPRecord;
import src.records.LabelRecord;
import src.records.LiteralRecord;
import src.records.ParameterRecord;
import src.records.PopRecord;
import src.records.PrintRecord;
import src.records.ProgramRecord;
import src.records.PushRecord;
import src.records.RegisterRecord;
import src.records.ReturnRecord;
import src.records.VariavelRecord;
import src.records.WhileRecord;

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

        tokens.consumer(TokenType.EOF, "Esperado fim do código");

        return new ProgramRecord(classes);
    }

    private ClassRecord classDeclaration() {
        TokenType visibility = null;
        if (tokens.verify(TokenType.PUBLIC) || tokens.verify(TokenType.PRIVATE)) {
            visibility = tokens.consumer(tokens.current().getType(), "Esperada visibilidade da funcao").getType();
        }
        tokens.consumer(TokenType.CLASS, "Esperado 'class'");
        Token name = tokens.consumer(TokenType.IDENTIFIER, "Esperado o nome da classe");
        tokens.consumer(TokenType.OPEN_BRACE, "Esperado '{' apos nome da classe");

        List<FunctionRecord> functions = new ArrayList<>();

        while (!tokens.verify(TokenType.CLOSE_BRACE) && !tokens.verify(TokenType.EOF)) {
            functions.add(functionDeclaration());
        }

        tokens.consumer(TokenType.CLOSE_BRACE, "Esperado '}' para fechar a classe");

        return new ClassRecord(visibility, name.getLexema(), functions);
    }

    private FunctionRecord functionDeclaration() {
        TokenType visibility = null;
        Token typeReturn;
        Token name;

        if (tokens.verify(TokenType.PUBLIC) || tokens.verify(TokenType.PRIVATE)) {
            visibility = tokens.consumer(tokens.current().getType(), "Esperada visibilidade da funcao").getType();
        }

        tokens.consumer(TokenType.FUNC, "Esperado 'func' para declarar uma funcao");

        if (tokens.verify(TokenType.VOID)) {
            typeReturn = tokens.consumer(TokenType.VOID, "Esperado 'void'");
        } else {
            typeReturn = type();
        }

        if (tokens.verify(TokenType.MAIN)) {
            name = tokens.consumer(TokenType.MAIN, "Esperado funcao 'main'");
        } else {
            name = tokens.consumer(TokenType.IDENTIFIER, "Esperado nome da funcao");
        }

        tokens.consumer(TokenType.OPEN_PARENTHESIS, "Esperado '(' apos nome da funcao");

        List<ParameterRecord> parameters = parameters();

        tokens.consumer(TokenType.CLOSE_PARENTHESIS, "Esperado ')' para fechar a declaracao da funcao");

        BlockRecord block = block();

        return new FunctionRecord(visibility, typeReturn.getType(), name.getLexema(), parameters, block);
    }

    private List<ParameterRecord> parameters() {
        List<ParameterRecord> parameters = new ArrayList<>();

        if (tokens.verify(TokenType.CLOSE_PARENTHESIS)) {
            return parameters;
        }

        do {
            Token type = type();
            Token name = tokens.consumer(TokenType.IDENTIFIER, "Esperado o nome do parametro");

            parameters.add(new ParameterRecord(type.getType(), name.getLexema()));

            if (!tokens.verify(TokenType.COMMA)) {
                break;
            }

            tokens.consumer(TokenType.COMMA, "Esperado ',' entre os parametros");
        } while (true);

        return parameters;
    }

    private ReturnRecord returnStatement() {
        tokens.consumer(TokenType.RETURN, "Esperado 'return'");

        ExpressionInterface value = null;

        if (!tokens.verify(TokenType.SEMICOLON)) {
            value = expression();
        }

        tokens.consumer(TokenType.SEMICOLON, "Esperado ';' apos return");

        return new ReturnRecord(value);
    }

    private CallRecord functionCall(String name) {
        tokens.consumer(TokenType.OPEN_PARENTHESIS, "Esperado '(' para chamar a função");

        List<ExpressionInterface> arguments = arguments();

        tokens.consumer(TokenType.CLOSE_PARENTHESIS, "Esperado ')' para fechar a chamada da função");

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

            tokens.consumer(TokenType.COMMA, "Esperado ',' entre os argumentos");
        } while (true);

        return arguments;
    }

    private BlockRecord block() {
        tokens.consumer(TokenType.OPEN_BRACE, "Esperado '{' para iniciar um bloco");

        List<StatementInterface> commands = new ArrayList<>();

        while (!tokens.verify(TokenType.CLOSE_BRACE) && !tokens.verify(TokenType.EOF)) {
            commands.add(statement());
        }

        tokens.consumer(TokenType.CLOSE_BRACE, "Esperado '}' para fechar o bloco");

        return new BlockRecord(commands);
    }

    private StatementInterface statement() {
        if (tokens.verify(TokenType.INT) || tokens.verify(TokenType.DOUBLE) || tokens.verify(TokenType.BOOLEAN)
                || tokens.verify(TokenType.STRING)) {
            return declaration();
        } else if (tokens.verify(TokenType.PRINT)) {
            tokens.consumer(TokenType.PRINT, "Esperado 'print'");
            ExpressionInterface expression = expression();
            tokens.consumer(TokenType.SEMICOLON, "Esperado ';' apos print");
            return new PrintRecord(expression);
        } else if (tokens.verify(TokenType.WHILE)) {
            tokens.consumer(TokenType.WHILE, "Esperado 'while'");
            tokens.consumer(TokenType.OPEN_PARENTHESIS, "Esperado '(' apos while");
            ExpressionInterface expression = expression();
            tokens.consumer(TokenType.CLOSE_PARENTHESIS, "Esperado ')' apos condicao");
            BlockRecord block = block();
            return new WhileRecord(expression, block);
        } else if (tokens.verify(TokenType.RETURN)) {
            return returnStatement();
        } else if (tokens.verify(TokenType.PUSH)) {
            return pushStatement();
        } else if (tokens.verify(TokenType.POP)) {
            return popStatement();
        } else if (tokens.verify(TokenType.IDENTIFIER)) {
            Token name = tokens.consumer(TokenType.IDENTIFIER, "Esperado um identificador");

            if (tokens.verify(TokenType.COLON)) {
                tokens.consumer(TokenType.COLON, "Esperado ':' apos identificador");
                return new LabelRecord(name.getLexema());
            }

            CallRecord call = functionCall(name.getLexema());

            tokens.consumer(TokenType.SEMICOLON, "Esperado ';' apos chamada de função");

            return new ExpressionStatementRecord(call);
        } else if (tokens.verify(TokenType.JMP)) {
            return jumpStatement();
        } else if (tokens.verify(TokenType.JE)) {
            return jumpEqualStatement();
        } else if (tokens.verify(TokenType.CMP)) {
            ExpressionInterface comparison = comparison();

            tokens.consumer(
                    TokenType.SEMICOLON,
                    "Esperado ';' apos comparação");

            return new ExpressionStatementRecord(comparison);
        } else {
            throw new SyntacticException(
                    "Comando inválido ou não reconhecido"
                            + ". Na linha: " + tokens.current().getLine()
                            + ", coluna: " + tokens.current().getColumn());
        }
    }

    private PushRecord pushStatement() {
        Token type = tokens.consumer(TokenType.PUSH, "Esperado 'push'");
        Token register = tokens.consumer(TokenType.REGISTER, "Esperado um registrador");

        tokens.consumer(TokenType.SEMICOLON, "Esperado ';' apos push");

        return new PushRecord(type.getType(), new RegisterRecord(register.getLexema()));
    }

    private PopRecord popStatement() {
        Token type = tokens.consumer(TokenType.POP, "Esperado 'pop'");
        Token register = tokens.consumer(TokenType.REGISTER, "Esperado um registrador");

        tokens.consumer(TokenType.SEMICOLON, "Esperado ';' apos pop");

        return new PopRecord(type.getType(), new RegisterRecord(register.getLexema()));
    }

    private JMPRecord jumpStatement() {
        tokens.consumer(TokenType.JMP, "Esperado 'jmp'");
        Token name = tokens.consumer(TokenType.IDENTIFIER, "Esperado um identificador");

        tokens.consumer(TokenType.SEMICOLON, "Esperado ';' apos jmp");

        return new JMPRecord(name.getLexema());
    }

    private JERecord jumpEqualStatement() {
        tokens.consumer(TokenType.JE, "Esperado 'je'");
        Token name = tokens.consumer(TokenType.IDENTIFIER, "Esperado um identificador");

        tokens.consumer(TokenType.SEMICOLON, "Esperado ';' apos je");

        return new JERecord(name.getLexema());
    }

    private StatementInterface declaration() {
        Token token = type();
        Token name = tokens.consumer(TokenType.IDENTIFIER, "Esperado o nome da variável");

        ExpressionInterface value = null;

        if (tokens.verify(TokenType.ASSIGN)) {
            tokens.consumer(TokenType.ASSIGN, "Esperado sinal de atribuição '='");
            value = expression();
        }

        tokens.consumer(TokenType.SEMICOLON, "Esperado ';' apos declaração");

        return new DeclarationRecord(token.getType(), name.getLexema(), value);
    }

    private ExpressionInterface expression() {
        switch (tokens.current().getType()) {
            case TokenType.NUMBER: {
                Token token = tokens.consumer(TokenType.NUMBER, "Esperado um número");
                if (token.getLexema().contains(".")) {
                    double value = Double.parseDouble(token.getLexema());

                    if (!Double.isFinite(value)) {
                        throw new SyntacticException(
                                "Literal decimal fora do intervalo de double: "
                                        + token.getLexema()
                                        + ". Na linha: " + token.getLine()
                                        + ", coluna: " + token.getColumn());
                    }

                    return new LiteralRecord(value);
                }
                try {
                    int value = Integer.parseInt(token.getLexema());
                    return new LiteralRecord(value);
                } catch (NumberFormatException e) {
                    throw new SyntacticException(
                            "Literal inteiro fora do intervalo de 32 bits: "
                                    + token.getLexema()
                                    + ". Na linha: " + token.getLine()
                                    + ", coluna: " + token.getColumn());
                }
            }
            case TokenType.TEXT: {
                Token token = tokens.consumer(TokenType.TEXT, "Esperado um literal de texto");
                return new LiteralRecord(token.getLexema());
            }
            case TokenType.IDENTIFIER: {
                Token name = tokens.consumer(TokenType.IDENTIFIER, "Esperado um identificador");

                if (tokens.verify(TokenType.OPEN_PARENTHESIS)) {
                    return functionCall(name.getLexema());
                }
                return new VariavelRecord(name.getLexema());
            }
            case TokenType.REGISTER: {
                Token token = tokens.consumer(TokenType.REGISTER, "Esperado um registrador");
                return new RegisterRecord(token.getLexema());
            }
            case TokenType.TRUE:
            case TokenType.FALSE: {
                Token token = tokens.consumer(tokens.current().getType(), "Esperado um booleano");
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
                tokens.consumer(TokenType.OPEN_PARENTHESIS, "Esperado '('");
                ExpressionInterface expression = expression();
                tokens.consumer(TokenType.CLOSE_PARENTHESIS, "Esperado ')'");
                return expression;
            default:
                throw new SyntacticException(
                        "Expressão inválida. Na linha: " + tokens.current().getLine() + ", coluna: " + tokens.current().getColumn());
        }
    }

    private ExpressionInterface operation() {
        Token operador = tokens.consumer(tokens.current().getType(), "Esperado operador. 'add, sub, mul, div, and, or'");

        ExpressionInterface left = expression();

        tokens.consumer(TokenType.COMMA, "Esperado ',' entre os operandos");

        ExpressionInterface right = expression();

        return new BinariaRecord(operador.getType(), left, right);
    }

    private ExpressionInterface comparison() {
        tokens.consumer(TokenType.CMP, "Esperado 'cmp'");
        tokens.consumer(TokenType.DOT, "Esperado '.' apos cmp");

        Token operador;
        switch (tokens.current().getType()) {
            case EQ:
            case NE:
            case LT:
            case GT:
            case LE:
            case GE:
                operador = tokens.consumer(tokens.current().getType(), "Esperada condicao.");
                break;

            default:
                throw new SyntacticException(
                        "Erro na linha: " + tokens.current().getLine() + " Esperado eq, ne, lt, gt, le ou ge");
        }

        ExpressionInterface left = expression();
        tokens.consumer(TokenType.COMMA, "Esperado ',' entre os operandos");
        ExpressionInterface right = expression();
        return new BinariaRecord(operador.getType(), left, right);
    }

    private Token type() {
        if (tokens.verify(TokenType.INT)) {
            return tokens.consumer(TokenType.INT, "Esperado 'int'");
        } else if (tokens.verify(TokenType.DOUBLE)) {
            return tokens.consumer(TokenType.DOUBLE, "Esperado 'double'");
        } else if (tokens.verify(TokenType.BOOLEAN)) {
            return tokens.consumer(TokenType.BOOLEAN, "Esperado 'boolean'");
        } else if (tokens.verify(TokenType.STRING)) {
            return tokens.consumer(TokenType.STRING, "Esperado 'String'");
        } else {
            throw new SyntacticException(
                    "Esperado tipo: int, double, boolean ou String. Na linha: " + tokens.current().getLine()
                            + ", coluna: " + tokens.current().getColumn());
        }
    }
}
