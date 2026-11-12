package src.parser;

import java.util.ArrayList;
import java.util.List;

import src.ast.ExpressionInterface;
import src.ast.StatementInterface;
import src.ast.records.AssignmentRecord;
import src.ast.records.AttributeRecord;
import src.ast.records.BinaryExpressionRecord;
import src.ast.records.BlockRecord;
import src.ast.records.CallRecord;
import src.ast.records.ClassRecord;
import src.ast.records.DeclarationRecord;
import src.ast.records.ExpressionStatementRecord;
import src.ast.records.ForRecord;
import src.ast.records.FunctionRecord;
import src.ast.records.InterfaceRecord;
import src.ast.records.LiteralRecord;
import src.ast.records.MemberAccessRecord;
import src.ast.records.MemberCallRecord;
import src.ast.records.ModifiersRecord;
import src.ast.records.ParameterRecord;
import src.ast.records.PopRecord;
import src.ast.records.PrintRecord;
import src.ast.records.ProgramRecord;
import src.ast.records.PushRecord;
import src.ast.records.RegisterRecord;
import src.ast.records.RegisterDeclarationRecord;
import src.ast.records.ReturnRecord;
import src.ast.records.SuperAttributeRecord;
import src.ast.records.SuperCallRecord;
import src.ast.records.ThisAttributeRecord;
import src.ast.records.ThisCallRecord;
import src.ast.records.ThisRecord;
import src.ast.records.TypeRecord;
import src.ast.records.VariableRecord;
import src.ast.records.WhileRecord;
import src.exceptions.DoubleOutOfRangeException;
import src.exceptions.IntegerOutOfRangeException;
import src.exceptions.SyntacticException;
import src.lexer.Token;
import src.lexer.TokenType;

public class Parser {

        private final TokenStream tokens;

        public Parser(List<Token> tokens) {
                this.tokens = new TokenStream(tokens);
        }

        // =========================================================
        // PROGRAMA
        // =========================================================

        public ProgramRecord analyze() {

                List<ClassRecord> classes = new ArrayList<>();
                List<InterfaceRecord> interfaces = new ArrayList<>();
                List<RegisterDeclarationRecord> registers = new ArrayList<>();

                while (!tokens.verify(TokenType.EOF)) {

                        if (tokens.verify(TokenType.REGISTER)) {
                                registers.add(registerDeclaration());

                        } else if (tokens.verify(TokenType.CLASS)) {
                                classes.add(classDeclaration());

                        } else if (tokens.verify(TokenType.INTERFACE)) {
                                interfaces.add(interfaceDeclaration());

                        } else if (tokens.verify(TokenType.PUBLIC)
                                        || tokens.verify(TokenType.PRIVATE)
                                        || tokens.verify(TokenType.PROTECTED)) {

                                TokenType next = tokens.peekNext().getType();

                                if (next == TokenType.CLASS) {
                                        classes.add(classDeclaration());

                                } else if (next == TokenType.INTERFACE) {
                                        interfaces.add(interfaceDeclaration());

                                } else {
                                        throw syntacticError(
                                                        "Esperado 'class' ou 'interface' após modificador de visibilidade");
                                }

                        } else {
                                throw syntacticError("Declaração inesperada.");
                        }
                }

                tokens.consume(TokenType.EOF, "Esperado fim do código");

                return new ProgramRecord(classes, interfaces, registers);
        }

        private RegisterDeclarationRecord registerDeclaration() {
                Token register = tokens.consume(
                                TokenType.REGISTER,
                                "Esperado um registrador");

                tokens.consume(
                                TokenType.ASSIGN,
                                "Esperado '=' após registrador global");

                ExpressionInterface value = expression();

                tokens.consume(
                                TokenType.SEMICOLON,
                                "Esperado ';' após declaração do registrador");

                return new RegisterDeclarationRecord(
                                new RegisterRecord(
                                                register.getLexeme(),
                                                register.getLine(),
                                                register.getColumn()),
                                value);
        }

        // =========================================================
        // CLASSES
        // =========================================================

        private ClassRecord classDeclaration() {

                TokenType visibility = null;

                List<String> interfaces = new ArrayList<>();

                if (isVisibility()) {
                        visibility = consumeVisibility();

                        if (visibility != TokenType.PUBLIC) {
                                Token token = tokens.current();
                                throw new SyntacticException(
                                                "Uma classe de nível superior só pode ser public ou sem modificador.",
                                                token.getLine(),
                                                token.getColumn());
                        }
                }

                tokens.consume(
                                TokenType.CLASS,
                                "Esperado 'class'");

                Token name = tokens.consume(
                                TokenType.IDENTIFIER,
                                "Esperado o nome da classe");

                String superClass = null;

                if (tokens.verify(TokenType.EXTENDS)) {

                        tokens.consume(
                                        TokenType.EXTENDS,
                                        "Esperado 'extends'");

                        superClass = tokens.consume(
                                        TokenType.IDENTIFIER,
                                        "Esperado o nome da classe pai").getLexeme();
                }

                if (tokens.verify(TokenType.IMPLEMENTS)) {

                        tokens.consume(
                                        TokenType.IMPLEMENTS,
                                        "Esperado 'implements'");

                        do {

                                Token interfaceName = tokens.consume(
                                                TokenType.IDENTIFIER,
                                                "Esperado o nome da interface");

                                interfaces.add(interfaceName.getLexeme());

                                if (!tokens.verify(TokenType.COMMA)) {
                                        break;
                                }

                                tokens.consume(
                                                TokenType.COMMA,
                                                "Esperado ',' entre interfaces");

                        } while (true);
                }

                tokens.consume(
                                TokenType.OPEN_BRACE,
                                "Esperado '{' após declaração da classe");

                List<FunctionRecord> functions = new ArrayList<>();
                List<AttributeRecord> attributes = new ArrayList<>();

                while (!tokens.verify(TokenType.CLOSE_BRACE)
                                && !tokens.verify(TokenType.EOF)) {

                        ModifiersRecord modifiers = modifiers();

                        if (tokens.verify(TokenType.FUNC)) {
                                functions.add(functionDeclaration(modifiers));

                        } else {
                                attributes.add(attributeDeclaration(modifiers));
                        }
                }

                tokens.consume(
                                TokenType.CLOSE_BRACE,
                                "Esperado '}' para fechar a classe");

                return new ClassRecord(
                                visibility,
                                name.getLexeme(),
                                superClass,
                                interfaces,
                                attributes,
                                functions,
                                name.getLine(),
                                name.getColumn());
        }

        // =========================================================
        // INTERFACES
        // =========================================================

        private InterfaceRecord interfaceDeclaration() {

                TokenType visibility = null;

                if (isVisibility()) {
                        visibility = consumeVisibility();

                        if (visibility != TokenType.PUBLIC) {
                                Token token = tokens.current();
                                throw new SyntacticException(
                                                "Uma interface de nível superior só pode ser public ou sem modificador.",
                                                token.getLine(),
                                                token.getColumn());
                        }
                }

                tokens.consume(
                                TokenType.INTERFACE,
                                "Esperado 'interface'");

                Token name = tokens.consume(
                                TokenType.IDENTIFIER,
                                "Esperado o nome da interface");

                tokens.consume(
                                TokenType.OPEN_BRACE,
                                "Esperado '{' após nome da interface");

                List<FunctionRecord> functions = new ArrayList<>();

                while (!tokens.verify(TokenType.CLOSE_BRACE)
                                && !tokens.verify(TokenType.EOF)) {

                        if (tokens.verify(TokenType.FUNC)) {
                                functions.add(interfaceFunctionDeclaration());

                        } else {
                                throw syntacticError(
                                                "Interfaces permitem apenas declarações de funções.");
                        }
                }

                tokens.consume(
                                TokenType.CLOSE_BRACE,
                                "Esperado '}' para fechar a interface");

                return new InterfaceRecord(
                                visibility,
                                name.getLexeme(),
                                functions,
                                name.getLine(),
                                name.getColumn());
        }

        private FunctionRecord interfaceFunctionDeclaration() {

                tokens.consume(
                                TokenType.FUNC,
                                "Esperado 'func' para declarar uma função");

                TypeRecord returnType = returnType();

                Token name = tokens.consume(
                                TokenType.IDENTIFIER,
                                "Esperado nome da função");

                tokens.consume(
                                TokenType.OPEN_PARENTHESIS,
                                "Esperado '(' após nome da função");

                List<ParameterRecord> parameters = parameters();

                tokens.consume(
                                TokenType.CLOSE_PARENTHESIS,
                                "Esperado ')' para fechar os parâmetros");

                tokens.consume(
                                TokenType.SEMICOLON,
                                "Esperado ';' para finalizar assinatura da função");

                return new FunctionRecord(
                                TokenType.PUBLIC,
                                false,
                                false,
                                returnType,
                                name.getLexeme(),
                                parameters,
                                null,
                                name.getLine(),
                                name.getColumn());
        }

        // =========================================================
        // MODIFICADORES
        // =========================================================

        private ModifiersRecord modifiers() {

                TokenType visibility = null;

                boolean isStatic = false;
                boolean isFinal = false;

                while (true) {

                        Token token = tokens.current();

                        switch (token.getType()) {

                                case PUBLIC, PRIVATE, PROTECTED -> {

                                        if (visibility != null) {
                                                throw new SyntacticException(
                                                                "Visibilidade repetida ou conflitante",
                                                                token.getLine(),
                                                                token.getColumn());
                                        }

                                        visibility = consumeVisibility();
                                }

                                case STATIC -> {

                                        if (isStatic) {
                                                throw new SyntacticException(
                                                                "Modificador 'static' repetido",
                                                                token.getLine(),
                                                                token.getColumn());
                                        }

                                        tokens.consume(
                                                        TokenType.STATIC,
                                                        "Esperado 'static'");

                                        isStatic = true;
                                }

                                case FINAL -> {

                                        if (isFinal) {
                                                throw new SyntacticException(
                                                                "Modificador 'final' repetido",
                                                                token.getLine(),
                                                                token.getColumn());
                                        }

                                        tokens.consume(
                                                        TokenType.FINAL,
                                                        "Esperado 'final'");

                                        isFinal = true;
                                }

                                default -> {
                                        return new ModifiersRecord(
                                                        visibility,
                                                        isStatic,
                                                        isFinal);
                                }
                        }
                }
        }

        private boolean isVisibility() {
                return tokens.verify(TokenType.PUBLIC)
                                || tokens.verify(TokenType.PRIVATE)
                                || tokens.verify(TokenType.PROTECTED);
        }

        private TokenType consumeVisibility() {

                return tokens.consume(
                                tokens.current().getType(),
                                "Esperada visibilidade").getType();
        }

        // =========================================================
        // TIPOS
        // =========================================================

        private TypeRecord type() {

                Token token = tokens.current();

                switch (token.getType()) {

                        case INT:
                        case DOUBLE:
                        case BOOLEAN:
                        case STRING:
                        case IDENTIFIER:

                                tokens.consume(
                                                token.getType(),
                                                "Esperado um tipo válido");

                                return new TypeRecord(token.getLexeme());

                        default:
                                throw new SyntacticException(
                                                "Esperado tipo primitivo ou nome de classe",
                                                token.getLine(),
                                                token.getColumn());
                }
        }

        private TypeRecord returnType() {

                if (tokens.verify(TokenType.VOID)) {

                        Token token = tokens.consume(
                                        TokenType.VOID,
                                        "Esperado 'void'");

                        return new TypeRecord(token.getLexeme());
                }

                return type();
        }

        // =========================================================
        // ATRIBUTOS
        // =========================================================

        private AttributeRecord attributeDeclaration(
                        ModifiersRecord modifiers) {

                Token typeToken = tokens.current();

                TypeRecord type = type();

                Token name = tokens.consume(
                                TokenType.IDENTIFIER,
                                "Esperado nome do atributo");

                ExpressionInterface value = null;

                if (tokens.verify(TokenType.ASSIGN)) {

                        tokens.consume(
                                        TokenType.ASSIGN,
                                        "Esperado '='");

                        value = expression();
                }

                tokens.consume(
                                TokenType.SEMICOLON,
                                "Esperado ';' após declaração do atributo");

                return new AttributeRecord(
                                modifiers.visibility(),
                                modifiers.isStatic(),
                                modifiers.isFinal(),
                                type,
                                name.getLexeme(),
                                value,
                                typeToken.getLine(),
                                typeToken.getColumn());
        }

        // =========================================================
        // FUNÇÕES
        // =========================================================

        private FunctionRecord functionDeclaration(
                        ModifiersRecord modifiers) {

                TokenType visibility = modifiers.visibility();

                tokens.consume(
                                TokenType.FUNC,
                                "Esperado 'func'");

                TypeRecord returnType = returnType();

                Token name;

                if (tokens.verify(TokenType.MAIN)) {

                        name = tokens.consume(
                                        TokenType.MAIN,
                                        "Esperado função 'main'");

                } else {

                        name = tokens.consume(
                                        TokenType.IDENTIFIER,
                                        "Esperado nome da função");
                }

                tokens.consume(
                                TokenType.OPEN_PARENTHESIS,
                                "Esperado '(' após nome da função");

                List<ParameterRecord> parameters = parameters();

                tokens.consume(
                                TokenType.CLOSE_PARENTHESIS,
                                "Esperado ')' para fechar os parâmetros");

                BlockRecord block = block();

                return new FunctionRecord(
                                visibility,
                                modifiers.isStatic(),
                                modifiers.isFinal(),
                                returnType,
                                name.getLexeme(),
                                parameters,
                                block,
                                name.getLine(),
                                name.getColumn());
        }

        private List<ParameterRecord> parameters() {

                List<ParameterRecord> parameters = new ArrayList<>();

                if (tokens.verify(TokenType.CLOSE_PARENTHESIS)) {
                        return parameters;
                }

                do {

                        TypeRecord type = type();

                        Token name = tokens.consume(
                                        TokenType.IDENTIFIER,
                                        "Esperado nome do parâmetro");

                        parameters.add(
                                        new ParameterRecord(
                                                        type,
                                                        name.getLexeme(),
                                                        name.getLine(),
                                                        name.getColumn()));

                        if (!tokens.verify(TokenType.COMMA)) {
                                break;
                        }

                        tokens.consume(
                                        TokenType.COMMA,
                                        "Esperado ',' entre os parâmetros");

                } while (true);

                return parameters;
        }

        // =========================================================
        // BLOCOS E COMANDOS
        // =========================================================

        private BlockRecord block() {

                tokens.consume(
                                TokenType.OPEN_BRACE,
                                "Esperado '{' para iniciar um bloco");

                List<StatementInterface> commands = new ArrayList<>();

                while (!tokens.verify(TokenType.CLOSE_BRACE)
                                && !tokens.verify(TokenType.EOF)) {

                        commands.add(statement());
                }

                tokens.consume(
                                TokenType.CLOSE_BRACE,
                                "Esperado '}' para fechar o bloco");

                return new BlockRecord(commands);
        }

        private StatementInterface statement() {

                return switch (tokens.current().getType()) {

                        case INT, DOUBLE, BOOLEAN, STRING ->
                                declaration();

                        case PRINT ->
                                printStatement();

                        case WHILE ->
                                whileStatement();

                        case FOR ->
                                forStatement();

                        case RETURN ->
                                returnStatement();

                        case PUSH ->
                                pushStatement();

                        case POP ->
                                popStatement();

                        case REGISTER ->
                                registerAssignment();

                        case IDENTIFIER -> {

                                if (tokens.peekNext().getType() == TokenType.IDENTIFIER) {
                                        yield declaration();
                                }

                                yield identifierStatement();
                        }

                        case CMP ->
                                comparisonStatement();

                        case SUPER -> {

                                ExpressionInterface expression = superExpression();

                                tokens.consume(
                                                TokenType.SEMICOLON,
                                                "Esperado ';' após acesso com super");

                                yield new ExpressionStatementRecord(expression);
                        }

                        case THIS ->
                                thisStatement();

                        default -> throw syntacticError(
                                        "Comando inválido ou não reconhecido.");
                };
        }

        // =========================================================
        // DECLARAÇÕES
        // =========================================================

        private StatementInterface thisStatement() {

                Token thisToken = tokens.consume(
                                TokenType.THIS,
                                "Esperado 'this'");

                tokens.consume(
                                TokenType.DOT,
                                "Esperado '.' após this");

                Token member = tokens.consume(
                                TokenType.IDENTIFIER,
                                "Esperado nome de atributo ou método após this.");

                if (tokens.verify(TokenType.OPEN_PARENTHESIS)) {
                        tokens.consume(
                                        TokenType.OPEN_PARENTHESIS,
                                        "Esperado '(' após nome do método");

                        List<ExpressionInterface> arguments = arguments();

                        tokens.consume(
                                        TokenType.CLOSE_PARENTHESIS,
                                        "Esperado ')' após argumentos");
                        tokens.consume(
                                        TokenType.SEMICOLON,
                                        "Esperado ';' após chamada do método");

                        return new ExpressionStatementRecord(
                                        new ThisCallRecord(
                                                        member.getLexeme(),
                                                        arguments,
                                                        thisToken.getLine(),
                                                        thisToken.getColumn()));
                }

                ThisAttributeRecord target = new ThisAttributeRecord(
                                member.getLexeme(),
                                thisToken.getLine(),
                                thisToken.getColumn());

                tokens.consume(
                                TokenType.ASSIGN,
                                "Esperado '=' após atributo");

                ExpressionInterface value = expression();

                tokens.consume(
                                TokenType.SEMICOLON,
                                "Esperado ';' após atribuição");

                return new AssignmentRecord(
                                target,
                                value,
                                thisToken.getLine(),
                                thisToken.getColumn());
        }

        private StatementInterface registerAssignment() {
                Token register = tokens.consume(
                                TokenType.REGISTER,
                                "Esperado um registrador");

                tokens.consume(
                                TokenType.ASSIGN,
                                "Esperado '=' após registrador");

                ExpressionInterface value = expression();

                tokens.consume(
                                TokenType.SEMICOLON,
                                "Esperado ';' após atribuição ao registrador");

                RegisterRecord target = new RegisterRecord(
                                register.getLexeme(),
                                register.getLine(),
                                register.getColumn());

                return new AssignmentRecord(
                                target,
                                value,
                                register.getLine(),
                                register.getColumn());
        }

        private StatementInterface declaration() {
                return declaration(true);
        }

        private StatementInterface declaration(boolean consumeSemicolon) {

                Token typeToken = tokens.current();

                TypeRecord type = type();

                Token name = tokens.consume(
                                TokenType.IDENTIFIER,
                                "Esperado nome da variável");

                ExpressionInterface value = null;

                if (tokens.verify(TokenType.ASSIGN)) {

                        tokens.consume(
                                        TokenType.ASSIGN,
                                        "Esperado sinal de atribuição '='");

                        value = expression();
                }

                if (consumeSemicolon) {
                        tokens.consume(
                                        TokenType.SEMICOLON,
                                        "Esperado ';' após declaração");
                }

                return new DeclarationRecord(
                                type,
                                name.getLexeme(),
                                value,
                                typeToken.getLine(),
                                typeToken.getColumn());
        }

        // =========================================================
        // RETURN
        // =========================================================

        private ReturnRecord returnStatement() {

                Token returnToken = tokens.consume(
                                TokenType.RETURN,
                                "Esperado 'return'");

                ExpressionInterface value = null;

                if (!tokens.verify(TokenType.SEMICOLON)) {
                        value = expression();
                }

                tokens.consume(
                                TokenType.SEMICOLON,
                                "Esperado ';' após return");

                return new ReturnRecord(
                                value,
                                returnToken.getLine(),
                                returnToken.getColumn());
        }

        // =========================================================
        // CHAMADAS DE FUNÇÃO
        // =========================================================

        private CallRecord functionCall(Token name) {

                tokens.consume(
                                TokenType.OPEN_PARENTHESIS,
                                "Esperado '(' para chamar a função");

                List<ExpressionInterface> arguments = arguments();

                tokens.consume(
                                TokenType.CLOSE_PARENTHESIS,
                                "Esperado ')' para fechar a chamada da função");

                return new CallRecord(
                                name.getLexeme(),
                                arguments,
                                name.getLine(),
                                name.getColumn());
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

                        tokens.consume(
                                        TokenType.COMMA,
                                        "Esperado ',' entre os argumentos");

                } while (true);

                return arguments;
        }

        private StatementInterface identifierStatement() {
                return identifierStatement(true);
        }

        private StatementInterface identifierStatement(boolean consumeSemicolon) {

                Token name = tokens.consume(
                                TokenType.IDENTIFIER,
                                "Esperado um identificador");

                if (tokens.verify(TokenType.ASSIGN)) {
                        tokens.consume(
                                        TokenType.ASSIGN,
                                        "Esperado '='");

                        ExpressionInterface value = expression();

                        if (consumeSemicolon) {
                                tokens.consume(
                                                TokenType.SEMICOLON,
                                                "Esperado ';' após atribuição");
                        }

                        return new AssignmentRecord(
                                        new VariableRecord(
                                                        name.getLexeme(),
                                                        name.getLine(),
                                                        name.getColumn()),
                                        value,
                                        name.getLine(),
                                        name.getColumn());
                }

                if (tokens.verify(TokenType.DOT)) {
                        ExpressionInterface member = new VariableRecord(
                                        name.getLexeme(),
                                        name.getLine(),
                                        name.getColumn());

                        while (tokens.verify(TokenType.DOT)) {
                                member = memberAccess(member);
                        }

                        if (tokens.verify(TokenType.ASSIGN)) {
                                tokens.consume(
                                                TokenType.ASSIGN,
                                                "Esperado '='");
                                ExpressionInterface value = expression();
                                if (consumeSemicolon) {
                                        tokens.consume(
                                                        TokenType.SEMICOLON,
                                                        "Esperado ';' após atribuição");
                                }
                                return new AssignmentRecord(
                                                member,
                                                value,
                                                name.getLine(),
                                                name.getColumn());
                        }

                        if (consumeSemicolon) {
                                tokens.consume(
                                                TokenType.SEMICOLON,
                                                "Esperado ';' após acesso ao membro");
                        }
                        return new ExpressionStatementRecord(member);
                }

                CallRecord call = functionCall(name);

                if (consumeSemicolon) {
                        tokens.consume(
                                        TokenType.SEMICOLON,
                                        "Esperado ';' após chamada de função");
                }

                return new ExpressionStatementRecord(call);
        }

        // =========================================================
        // WHILE
        // =========================================================

        private StatementInterface whileStatement() {

                Token whileToken = tokens.consume(
                                TokenType.WHILE,
                                "Esperado 'while'");

                tokens.consume(
                                TokenType.OPEN_PARENTHESIS,
                                "Esperado '(' após while");

                ExpressionInterface expression = expression();

                tokens.consume(
                                TokenType.CLOSE_PARENTHESIS,
                                "Esperado ')' após condição");

                BlockRecord block = block();

                return new WhileRecord(
                                expression,
                                block,
                                whileToken.getLine(),
                                whileToken.getColumn());
        }

        private StatementInterface forStatement() {
                Token forToken = tokens.consume(
                                TokenType.FOR,
                                "Esperado 'for'");

                tokens.consume(
                                TokenType.OPEN_PARENTHESIS,
                                "Esperado '(' após 'for'");

                StatementInterface initializer = null;
                if (!tokens.verify(TokenType.SEMICOLON)) {
                        initializer = forClause(true);
                }

                tokens.consume(
                                TokenType.SEMICOLON,
                                "Esperado ';' após inicialização do for");

                ExpressionInterface condition = null;
                if (!tokens.verify(TokenType.SEMICOLON)) {
                        condition = expression();
                }

                tokens.consume(
                                TokenType.SEMICOLON,
                                "Esperado ';' após condição do for");

                StatementInterface update = null;
                if (!tokens.verify(TokenType.CLOSE_PARENTHESIS)) {
                        update = forClause(false);
                }

                tokens.consume(
                                TokenType.CLOSE_PARENTHESIS,
                                "Esperado ')' após cabeçalho do for");

                return new ForRecord(
                                initializer,
                                condition,
                                update,
                                block(),
                                forToken.getLine(),
                                forToken.getColumn());
        }

        private StatementInterface forClause(boolean allowDeclaration) {
                TokenType current = tokens.current().getType();
                if (allowDeclaration
                                && (current == TokenType.INT
                                                || current == TokenType.DOUBLE
                                                || current == TokenType.BOOLEAN
                                                || current == TokenType.STRING
                                                || (current == TokenType.IDENTIFIER
                                                                && tokens.peekNext().getType() == TokenType.IDENTIFIER))) {
                        return declaration(false);
                }

                if (current == TokenType.IDENTIFIER) {
                        return identifierStatement(false);
                }

                throw syntacticError(
                                allowDeclaration
                                                ? "Inicialização do for deve ser uma declaração ou expressão."
                                                : "Atualização do for deve ser uma expressão.");
        }

        // =========================================================
        // PRINT
        // =========================================================

        private StatementInterface printStatement() {

                tokens.consume(
                                TokenType.PRINT,
                                "Esperado 'print'");

                ExpressionInterface expression = expression();

                tokens.consume(
                                TokenType.SEMICOLON,
                                "Esperado ';' após print");

                return new PrintRecord(expression);
        }

        // =========================================================
        // PUSH E POP
        // =========================================================

        private PushRecord pushStatement() {

                Token type = tokens.consume(
                                TokenType.PUSH,
                                "Esperado 'push'");

                Token register = tokens.consume(
                                TokenType.REGISTER,
                                "Esperado um registrador");

                tokens.consume(
                                TokenType.SEMICOLON,
                                "Esperado ';' após push");

                return new PushRecord(
                                type.getType(),
                                new RegisterRecord(
                                                register.getLexeme(),
                                                register.getLine(),
                                                register.getColumn()));
        }

        private PopRecord popStatement() {

                Token type = tokens.consume(
                                TokenType.POP,
                                "Esperado 'pop'");

                Token register = tokens.consume(
                                TokenType.REGISTER,
                                "Esperado um registrador");

                tokens.consume(
                                TokenType.SEMICOLON,
                                "Esperado ';' após pop");

                return new PopRecord(
                                type.getType(),
                                new RegisterRecord(
                                                register.getLexeme(),
                                                register.getLine(),
                                                register.getColumn()));
        }

        // =========================================================
        // EXPRESSÕES
        // =========================================================

        private ExpressionInterface expression() {

                return switch (tokens.current().getType()) {

                        case NUMBER ->
                                numberExpression();

                        case TEXT ->
                                textExpression();

                        case IDENTIFIER ->
                                identifierExpression();

                        case REGISTER ->
                                registerExpression();

                        case TRUE, FALSE ->
                                booleanExpression();

                        case ADD, SUB, MUL, DIV, AND, OR ->
                                operation();

                        case CMP ->
                                comparison();

                        case OPEN_PARENTHESIS ->
                                parenthesisExpression();

                        case SUPER ->
                                superExpression();

                        case THIS ->
                                thisExpression();

                        default -> throw syntacticError(
                                        "Expressão inválida.");
                };
        }

        // =========================================================
        // EXPRESSÃO SUPER
        // =========================================================

        private ExpressionInterface thisExpression() {

                Token token = tokens.consume(
                                TokenType.THIS,
                                "Esperado 'this'");

                if (tokens.verify(TokenType.DOT)) {

                        tokens.consume(
                                        TokenType.DOT,
                                        "Esperado '.' após this");

                        Token attribute = tokens.consume(
                                        TokenType.IDENTIFIER,
                                        "Esperado nome de atributo ou método");

                        if (tokens.verify(TokenType.OPEN_PARENTHESIS)) {
                                tokens.consume(
                                                TokenType.OPEN_PARENTHESIS,
                                                "Esperado '(' após nome do método");

                                List<ExpressionInterface> arguments = arguments();

                                tokens.consume(
                                                TokenType.CLOSE_PARENTHESIS,
                                                "Esperado ')' após argumentos");

                                return new ThisCallRecord(
                                                attribute.getLexeme(),
                                                arguments,
                                                token.getLine(),
                                                token.getColumn());
                        }

                        return new ThisAttributeRecord(
                                        attribute.getLexeme(),
                                        token.getLine(),
                                        token.getColumn());
                }

                return new ThisRecord(
                                token.getLine(),
                                token.getColumn());
        }

        private ExpressionInterface superExpression() {

                Token superToken = tokens.consume(
                                TokenType.SUPER,
                                "Esperado 'super'");

                tokens.consume(
                                TokenType.DOT,
                                "Esperado '.' após super");

                Token member = tokens.consume(
                                TokenType.IDENTIFIER,
                                "Esperado nome do atributo ou método");

                if (tokens.verify(TokenType.OPEN_PARENTHESIS)) {

                        tokens.consume(
                                        TokenType.OPEN_PARENTHESIS,
                                        "Esperado '(' para chamada do método");

                        List<ExpressionInterface> arguments = arguments();

                        tokens.consume(
                                        TokenType.CLOSE_PARENTHESIS,
                                        "Esperado ')' para fechar chamada");

                        return new SuperCallRecord(
                                        member.getLexeme(),
                                        arguments,
                                        superToken.getLine(),
                                        superToken.getColumn());
                }

                return new SuperAttributeRecord(
                                member.getLexeme(),
                                superToken.getLine(),
                                superToken.getColumn());
        }

        // =========================================================
        // EXPRESSÃO ENTRE PARÊNTESES
        // =========================================================

        private ExpressionInterface parenthesisExpression() {

                tokens.consume(
                                TokenType.OPEN_PARENTHESIS,
                                "Esperado '('");

                ExpressionInterface expression = expression();

                tokens.consume(
                                TokenType.CLOSE_PARENTHESIS,
                                "Esperado ')'");

                return expression;
        }

        // =========================================================
        // LITERAIS
        // =========================================================

        private ExpressionInterface booleanExpression() {

                Token token = tokens.consume(
                                tokens.current().getType(),
                                "Esperado um booleano");

                return new LiteralRecord(
                                token.getType() == TokenType.TRUE);
        }

        private ExpressionInterface numberExpression() {

                Token token = tokens.consume(
                                TokenType.NUMBER,
                                "Esperado um número");

                if (token.getLexeme().contains(".")) {

                        double value = Double.parseDouble(token.getLexeme());

                        if (!Double.isFinite(value)) {
                                throw new DoubleOutOfRangeException(
                                                token.getLexeme(),
                                                token.getLine(),
                                                token.getColumn());
                        }

                        return new LiteralRecord(value);
                }

                try {

                        int value = Integer.parseInt(token.getLexeme());

                        return new LiteralRecord(value);

                } catch (NumberFormatException e) {

                        throw new IntegerOutOfRangeException(
                                        token.getLexeme(),
                                        token.getLine(),
                                        token.getColumn());
                }
        }

        private ExpressionInterface textExpression() {

                Token token = tokens.consume(
                                TokenType.TEXT,
                                "Esperado um literal de texto");

                return new LiteralRecord(token.getLexeme());
        }

        // =========================================================
        // OPERAÇÕES
        // =========================================================

        private ExpressionInterface operation() {

                Token operator = tokens.consume(
                                tokens.current().getType(),
                                "Esperado operador");

                ExpressionInterface left = expression();

                tokens.consume(
                                TokenType.COMMA,
                                "Esperado ',' entre os operandos");

                ExpressionInterface right = expression();

                return new BinaryExpressionRecord(
                                operator.getType(),
                                left,
                                right,
                                operator.getLine(),
                                operator.getColumn());
        }

        // =========================================================
        // IDENTIFICADORES
        // =========================================================

        private ExpressionInterface identifierExpression() {

                Token name = tokens.consume(
                                TokenType.IDENTIFIER,
                                "Esperado um identificador");

                if (tokens.verify(TokenType.OPEN_PARENTHESIS)) {
                        return functionCall(name);
                }

                ExpressionInterface expression = new VariableRecord(
                                name.getLexeme(),
                                name.getLine(),
                                name.getColumn());

                while (tokens.verify(TokenType.DOT)) {
                        expression = memberAccess(expression);
                }

                return expression;
        }

        private ExpressionInterface memberAccess(ExpressionInterface receiver) {
                tokens.consume(
                                TokenType.DOT,
                                "Esperado '.' antes do membro");

                Token member = tokens.consume(
                                TokenType.IDENTIFIER,
                                "Esperado nome do atributo ou método");

                if (tokens.verify(TokenType.OPEN_PARENTHESIS)) {
                        tokens.consume(
                                        TokenType.OPEN_PARENTHESIS,
                                        "Esperado '(' após nome do método");
                        List<ExpressionInterface> methodArguments = arguments();
                        tokens.consume(
                                        TokenType.CLOSE_PARENTHESIS,
                                        "Esperado ')' após argumentos");

                        return new MemberCallRecord(
                                        receiver,
                                        member.getLexeme(),
                                        methodArguments,
                                        member.getLine(),
                                        member.getColumn());
                }

                return new MemberAccessRecord(
                                receiver,
                                member.getLexeme(),
                                member.getLine(),
                                member.getColumn());
        }

        // =========================================================
        // REGISTRADORES
        // =========================================================

        private ExpressionInterface registerExpression() {

                Token token = tokens.consume(
                                TokenType.REGISTER,
                                "Esperado um registrador");

                return new RegisterRecord(
                                token.getLexeme(),
                                token.getLine(),
                                token.getColumn());
        }

        // =========================================================
        // COMPARAÇÕES
        // =========================================================

        private StatementInterface comparisonStatement() {

                ExpressionInterface comparison = comparison();

                tokens.consume(
                                TokenType.SEMICOLON,
                                "Esperado ';' após comparação");

                return new ExpressionStatementRecord(comparison);
        }

        private ExpressionInterface comparison() {

                tokens.consume(
                                TokenType.CMP,
                                "Esperado 'cmp'");

                tokens.consume(
                                TokenType.DOT,
                                "Esperado '.' após cmp");

                Token operator;

                switch (tokens.current().getType()) {

                        case EQ, NE, LT, GT, LE, GE -> {

                                operator = tokens.consume(
                                                tokens.current().getType(),
                                                "Esperada condição");
                        }

                        default -> throw syntacticError(
                                        "Esperado eq, ne, lt, gt, le ou ge.");
                }

                ExpressionInterface left = expression();

                tokens.consume(
                                TokenType.COMMA,
                                "Esperado ',' entre os operandos");

                ExpressionInterface right = expression();

                return new BinaryExpressionRecord(
                                operator.getType(),
                                left,
                                right,
                                operator.getLine(),
                                operator.getColumn());
        }

        // =========================================================
        // ERROS SINTÁTICOS
        // =========================================================

        private SyntacticException syntacticError(String message) {

                Token token = tokens.current();

                return new SyntacticException(
                                message,
                                token.getLine(),
                                token.getColumn());
        }
}