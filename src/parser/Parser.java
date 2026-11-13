package src.parser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import src.ast.ExpressionInterface;
import src.ast.StatementInterface;
import src.ast.records.ArrayAccessRecord;
import src.ast.records.ArrayCreationRecord;
import src.ast.records.ArrayInitializerRecord;
import src.ast.records.AssignmentRecord;
import src.ast.records.AttributeRecord;
import src.ast.records.BinaryExpressionRecord;
import src.ast.records.BlockRecord;
import src.ast.records.CallRecord;
import src.ast.records.ClassRecord;
import src.ast.records.ConstructorInvocationRecord;
import src.ast.records.ConstructorRecord;
import src.ast.records.DeclarationRecord;
import src.ast.records.ExpressionStatementRecord;
import src.ast.records.ForRecord;
import src.ast.records.FunctionRecord;
import src.ast.records.IfRecord;
import src.ast.records.InterfaceRecord;
import src.ast.records.LiteralRecord;
import src.ast.records.MemberAccessRecord;
import src.ast.records.MemberCallRecord;
import src.ast.records.ModifiersRecord;
import src.ast.records.NewObjectRecord;
import src.ast.records.ParameterRecord;
import src.ast.records.PrintRecord;
import src.ast.records.ProgramRecord;
import src.ast.records.RegisterDeclarationRecord;
import src.ast.records.RegisterRecord;
import src.ast.records.ReturnRecord;
import src.ast.records.SuperAttributeRecord;
import src.ast.records.SuperCallRecord;
import src.ast.records.ThisAttributeRecord;
import src.ast.records.ThisCallRecord;
import src.ast.records.ThisRecord;
import src.ast.records.TypeRecord;
import src.ast.records.UnaryExpressionRecord;
import src.ast.records.VariableRecord;
import src.ast.records.WhileRecord;
import src.exceptions.DoubleOutOfRangeException;
import src.exceptions.LongOutOfRangeException;
import src.exceptions.SyntacticException;
import src.lexer.Token;
import src.lexer.TokenType;

public class Parser {

        private final TokenStream tokens;
        private String packageName = "";
        private final Map<String, String> importedTypes = new HashMap<>();
        private final List<String> imports = new ArrayList<>();

        public Parser(List<Token> tokens) {
                this.tokens = new TokenStream(tokens);
        }

        private TypeRecord addDeclaratorDimensions(TypeRecord type) {
                String typeName = type.name();
                while (tokens.verify(TokenType.OPEN_BRACKET)
                                && tokens.peekNext().getType() == TokenType.CLOSE_BRACKET) {
                        tokens.consume(TokenType.OPEN_BRACKET, "Esperado '['");
                        tokens.consume(TokenType.CLOSE_BRACKET, "Esperado ']'");
                        typeName += "[]";
                }
                return new TypeRecord(typeName);
        }

        public ProgramRecord analyze() {

                List<ClassRecord> classes = new ArrayList<>();
                List<InterfaceRecord> interfaces = new ArrayList<>();
                List<RegisterDeclarationRecord> registers = new ArrayList<>();

                if (tokens.verify(TokenType.PACKAGE)) {
                        tokens.consume(TokenType.PACKAGE, "Esperado 'package'");
                        packageName = qualifiedName("package");
                        tokens.consume(TokenType.SEMICOLON, "Esperado ';' após package");
                }

                while (tokens.verify(TokenType.IMPORT)) {
                        tokens.consume(TokenType.IMPORT, "Esperado 'import'");
                        String importedName = qualifiedName("import");
                        tokens.consume(TokenType.SEMICOLON, "Esperado ';' após import");

                        int separator = importedName.lastIndexOf('.');
                        String simpleName = separator < 0
                                        ? importedName
                                        : importedName.substring(separator + 1);
                        String previousImport = importedTypes.putIfAbsent(simpleName, importedName);
                        if (previousImport != null && !previousImport.equals(importedName)) {
                                throw syntacticError(
                                                "Import ambíguo para o tipo '" + simpleName + "'.");
                        }
                        imports.add(importedName);
                }

                while (!tokens.verify(TokenType.EOF)) {

                        if (tokens.verify(TokenType.REGISTER)) {
                                registers.add(registerDeclaration());

                        } else if (tokens.verify(TokenType.CLASS)) {
                                classes.add(classDeclaration());

                        } else if (tokens.verify(TokenType.INTERFACE)) {
                                interfaces.add(interfaceDeclaration());

                        } else if (tokens.verify(TokenType.ABSTRACT)
                                        || tokens.verify(TokenType.FINAL)) {
                                classes.add(classDeclaration());

                        } else if (tokens.verify(TokenType.PUBLIC)
                                        || tokens.verify(TokenType.PRIVATE)
                                        || tokens.verify(TokenType.PROTECTED)) {

                                TokenType next = tokens.peekNext().getType();

                                if (next == TokenType.CLASS
                                                || next == TokenType.ABSTRACT
                                                || next == TokenType.FINAL) {
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

                return new ProgramRecord(classes, interfaces, registers, packageName, imports);
        }

        private String qualifiedName(String declaration) {
                StringBuilder name = new StringBuilder(tokens.consume(
                                TokenType.IDENTIFIER,
                                "Esperado nome após '" + declaration + "'").getLexeme());

                while (tokens.verify(TokenType.DOT)) {
                        tokens.consume(TokenType.DOT, "Esperado '.' em nome qualificado");
                        name.append('.').append(tokens.consume(
                                        TokenType.IDENTIFIER,
                                        "Esperado identificador após '.'").getLexeme());
                }

                return name.toString();
        }

        private String resolveTypeName(String name) {
                if (name.equals("Object")) {
                        return name;
                }
                String imported = importedTypes.get(name);
                if (imported != null) {
                        return imported;
                }
                return packageName.isEmpty() ? name : packageName + "." + name;
        }

        private String declaredTypeName(String name) {
                return packageName.isEmpty() ? name : packageName + "." + name;
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

        private ClassRecord classDeclaration() {

                TokenType visibility = null;
                boolean isAbstract = false;
                boolean isFinal = false;

                List<String> interfaces = new ArrayList<>();

                while (isVisibility()
                                || tokens.verify(TokenType.ABSTRACT)
                                || tokens.verify(TokenType.FINAL)) {
                        Token modifier = tokens.current();
                        if (isVisibility()) {
                                if (visibility != null) {
                                        throw new SyntacticException(
                                                        "Visibilidade repetida ou conflitante",
                                                        modifier.getLine(),
                                                        modifier.getColumn());
                                }
                                visibility = consumeVisibility();
                        } else if (tokens.verify(TokenType.ABSTRACT)) {
                                if (isAbstract) {
                                        throw new SyntacticException(
                                                        "Modificador 'abstract' repetido",
                                                        modifier.getLine(),
                                                        modifier.getColumn());
                                }
                                tokens.consume(TokenType.ABSTRACT, "Esperado 'abstract'");
                                isAbstract = true;
                        } else {
                                if (isFinal) {
                                        throw new SyntacticException(
                                                        "Modificador 'final' repetido",
                                                        modifier.getLine(),
                                                        modifier.getColumn());
                                }
                                tokens.consume(TokenType.FINAL, "Esperado 'final'");
                                isFinal = true;
                        }
                }

                if (isAbstract && isFinal) {
                        throw new SyntacticException(
                                        "Uma classe não pode ser abstract e final.",
                                        tokens.current().getLine(),
                                        tokens.current().getColumn());
                }

                if (visibility != null && visibility != TokenType.PUBLIC) {
                        Token token = tokens.current();
                        throw new SyntacticException(
                                        "Uma classe de nível superior só pode ser public ou sem modificador.",
                                        token.getLine(),
                                        token.getColumn());
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

                        superClass = resolveTypeName(tokens.consume(
                                        TokenType.IDENTIFIER,
                                        "Esperado o nome da classe pai").getLexeme());
                }

                if (tokens.verify(TokenType.IMPLEMENTS)) {

                        tokens.consume(
                                        TokenType.IMPLEMENTS,
                                        "Esperado 'implements'");

                        do {

                                Token interfaceName = tokens.consume(
                                                TokenType.IDENTIFIER,
                                                "Esperado o nome da interface");

                                interfaces.add(resolveTypeName(interfaceName.getLexeme()));

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
                List<ConstructorRecord> constructors = new ArrayList<>();

                while (!tokens.verify(TokenType.CLOSE_BRACE)
                                && !tokens.verify(TokenType.EOF)) {

                        ModifiersRecord modifiers = modifiers();

                        if (tokens.verify(TokenType.FUNC)) {
                                functions.add(functionDeclaration(modifiers));

                        } else if (tokens.verify(TokenType.IDENTIFIER)
                                        && tokens.current().getLexeme().equals(name.getLexeme())
                                        && tokens.peekNext().getType() == TokenType.OPEN_PARENTHESIS) {
                                if (modifiers.isStatic() || modifiers.isFinal() || modifiers.isAbstract()) {
                                        throw new SyntacticException(
                                                        "Construtores não aceitam os modificadores static, final ou abstract.",
                                                        tokens.current().getLine(),
                                                        tokens.current().getColumn());
                                }
                                constructors.add(constructorDeclaration(modifiers.visibility()));

                        } else {
                                if (modifiers.isAbstract()) {
                                        throw syntacticError(
                                                        "O modificador abstract só pode ser usado em classes e métodos.");
                                }
                                attributes.add(attributeDeclaration(modifiers));
                        }
                }

                tokens.consume(
                                TokenType.CLOSE_BRACE,
                                "Esperado '}' para fechar a classe");

                return new ClassRecord(
                                visibility,
                                declaredTypeName(name.getLexeme()),
                                superClass,
                                interfaces,
                                attributes,
                                functions,
                                constructors,
                                isAbstract,
                                isFinal,
                                name.getLine(),
                                name.getColumn());
        }

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
                                declaredTypeName(name.getLexeme()),
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
                                true,
                                name.getLine(),
                                name.getColumn());
        }

        private ModifiersRecord modifiers() {

                TokenType visibility = null;

                boolean isStatic = false;
                boolean isFinal = false;
                boolean isAbstract = false;

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

                                case ABSTRACT -> {
                                        if (isAbstract) {
                                                throw new SyntacticException(
                                                                "Modificador 'abstract' repetido",
                                                                token.getLine(),
                                                                token.getColumn());
                                        }
                                        tokens.consume(TokenType.ABSTRACT, "Esperado 'abstract'");
                                        isAbstract = true;
                                }

                                default -> {
                                        return new ModifiersRecord(
                                                        visibility,
                                                        isStatic,
                                                        isFinal,
                                                        isAbstract);
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

        private TypeRecord type() {
                return type(true);
        }

        private TypeRecord type(boolean allowArray) {

                Token token = tokens.current();

                switch (token.getType()) {

                        case INT:
                        case LONG:
                        case DOUBLE:
                        case BOOLEAN:
                        case STRING:
                        case IDENTIFIER:

                                tokens.consume(
                                                token.getType(),
                                                "Esperado um tipo válido");

                                String typeName = token.getType() == TokenType.IDENTIFIER
                                                ? resolveTypeName(token.getLexeme())
                                                : token.getLexeme();
                                while (allowArray && tokens.verify(TokenType.OPEN_BRACKET)) {
                                        tokens.consume(
                                                        TokenType.OPEN_BRACKET,
                                                        "Esperado '[' no tipo de array");
                                        tokens.consume(
                                                        TokenType.CLOSE_BRACKET,
                                                        "Esperado ']' no tipo de array");
                                        typeName += "[]";
                                }
                                return new TypeRecord(typeName);

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

        private AttributeRecord attributeDeclaration(
                        ModifiersRecord modifiers) {

                Token typeToken = tokens.current();

                TypeRecord type = type();

                Token name = tokens.consume(
                                TokenType.IDENTIFIER,
                                "Esperado nome do atributo");
                type = addDeclaratorDimensions(type);

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

        private FunctionRecord functionDeclaration(
                        ModifiersRecord modifiers) {

                TokenType visibility = modifiers.visibility();

                tokens.consume(
                                TokenType.FUNC,
                                "Esperado 'func'");

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

                BlockRecord block;
                if (modifiers.isAbstract()) {
                        if (modifiers.isStatic() || modifiers.isFinal()
                                        || visibility == TokenType.PRIVATE) {
                                throw new SyntacticException(
                                                "Método abstract não pode ser private, static ou final.",
                                                name.getLine(),
                                                name.getColumn());
                        }
                        tokens.consume(
                                        TokenType.SEMICOLON,
                                        "Esperado ';' após assinatura do método abstract");
                        block = null;
                } else {
                        block = block();
                }

                return new FunctionRecord(
                                visibility,
                                modifiers.isStatic(),
                                modifiers.isFinal(),
                                returnType,
                                name.getLexeme(),
                                parameters,
                                block,
                                modifiers.isAbstract(),
                                name.getLine(),
                                name.getColumn());
        }

        private ConstructorRecord constructorDeclaration(TokenType visibility) {
                Token name = tokens.consume(
                                TokenType.IDENTIFIER,
                                "Esperado nome do construtor");
                tokens.consume(TokenType.OPEN_PARENTHESIS, "Esperado '(' após nome do construtor");
                List<ParameterRecord> parameters = parameters();
                tokens.consume(TokenType.CLOSE_PARENTHESIS, "Esperado ')' após parâmetros");

                tokens.consume(TokenType.OPEN_BRACE, "Esperado '{' no construtor");
                ConstructorInvocationRecord invocation = null;
                if ((tokens.verify(TokenType.THIS) || tokens.verify(TokenType.SUPER))
                                && tokens.peekNext().getType() == TokenType.OPEN_PARENTHESIS) {
                        Token invocationToken = tokens.current();
                        boolean delegatesToThis = tokens.verify(TokenType.THIS);
                        tokens.consume(
                                        invocationToken.getType(),
                                        "Esperado 'this' ou 'super'");
                        tokens.consume(
                                        TokenType.OPEN_PARENTHESIS,
                                        "Esperado '(' após invocação do construtor");
                        List<ExpressionInterface> invocationArguments = arguments();
                        tokens.consume(
                                        TokenType.CLOSE_PARENTHESIS,
                                        "Esperado ')' após argumentos do construtor");
                        tokens.consume(
                                        TokenType.SEMICOLON,
                                        "Esperado ';' após invocação do construtor");
                        invocation = new ConstructorInvocationRecord(
                                        delegatesToThis,
                                        invocationArguments,
                                        invocationToken.getLine(),
                                        invocationToken.getColumn());
                }

                List<StatementInterface> commands = new ArrayList<>();
                while (!tokens.verify(TokenType.CLOSE_BRACE)
                                && !tokens.verify(TokenType.EOF)) {
                        commands.add(statement());
                }
                tokens.consume(TokenType.CLOSE_BRACE, "Esperado '}' após construtor");

                return new ConstructorRecord(
                                visibility,
                                parameters,
                                invocation,
                                new BlockRecord(commands),
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
                        type = addDeclaratorDimensions(type);

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

                        case INT, LONG, DOUBLE, BOOLEAN, STRING ->
                                declaration();

                        case PRINT ->
                                printStatement();

                        case IF ->
                                ifStatement();

                        case WHILE ->
                                whileStatement();

                        case FOR ->
                                forStatement();

                        case RETURN ->
                                returnStatement();

                        case REGISTER ->
                                registerAssignment();

                        case IDENTIFIER -> {

                                if (isVariableDeclarationStart()) {
                                        yield declaration();
                                }

                                yield identifierStatement();
                        }

                        case CMP ->
                                comparisonStatement();

                        case SUPER -> {

                                Token superToken = tokens.current();
                                ExpressionInterface expression = superExpression();
                                expression = postfixExpression(expression);
                                if (tokens.verify(TokenType.ASSIGN)) {
                                        tokens.consume(
                                                        TokenType.ASSIGN,
                                                        "Esperado '=' após destino");
                                        ExpressionInterface value = expression();
                                        tokens.consume(
                                                        TokenType.SEMICOLON,
                                                        "Esperado ';' após atribuição");
                                        yield new AssignmentRecord(
                                                        expression,
                                                        value,
                                                        superToken.getLine(),
                                                        superToken.getColumn());
                                }
                                if (!(expression instanceof SuperCallRecord)
                                                && !(expression instanceof MemberCallRecord)) {
                                        throw syntacticError(
                                                        "Um acesso a atributo não pode ser usado como comando.");
                                }

                                tokens.consume(
                                                TokenType.SEMICOLON,
                                                "Esperado ';' após acesso com super");

                                yield new ExpressionStatementRecord(
                                                expression,
                                                superToken.getLine(),
                                                superToken.getColumn());
                        }

                        case THIS ->
                                thisStatement();

                        default -> throw syntacticError(
                                        "Comando inválido ou não reconhecido.");
                };
        }

        private IfRecord ifStatement() {
                Token ifToken = tokens.consume(TokenType.IF, "Esperado 'if'");
                tokens.consume(
                                TokenType.OPEN_PARENTHESIS,
                                "Esperado '(' após 'if'");
                ExpressionInterface condition = expression();
                tokens.consume(
                                TokenType.CLOSE_PARENTHESIS,
                                "Esperado ')' após condição do if");

                StatementInterface thenBranch = block();
                StatementInterface elseBranch = null;
                if (tokens.verify(TokenType.ELSE)) {
                        tokens.consume(TokenType.ELSE, "Esperado 'else'");
                        elseBranch = tokens.verify(TokenType.IF)
                                        ? ifStatement()
                                        : block();
                }

                return new IfRecord(
                                condition,
                                thenBranch,
                                elseBranch,
                                ifToken.getLine(),
                                ifToken.getColumn());
        }

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

                ExpressionInterface expression;
                if (tokens.verify(TokenType.OPEN_PARENTHESIS)) {
                        tokens.consume(
                                        TokenType.OPEN_PARENTHESIS,
                                        "Esperado '(' após nome do método");

                        List<ExpressionInterface> arguments = arguments();

                        tokens.consume(
                                        TokenType.CLOSE_PARENTHESIS,
                                        "Esperado ')' após argumentos");
                        expression = new ThisCallRecord(
                                        member.getLexeme(),
                                        arguments,
                                        thisToken.getLine(),
                                        thisToken.getColumn());
                } else {
                        expression = new ThisAttributeRecord(
                                        member.getLexeme(),
                                        thisToken.getLine(),
                                        thisToken.getColumn());
                }

                expression = postfixExpression(expression);

                if (tokens.verify(TokenType.ASSIGN)) {
                        tokens.consume(
                                        TokenType.ASSIGN,
                                        "Esperado '=' após atributo");

                        ExpressionInterface value = expression();

                        tokens.consume(
                                        TokenType.SEMICOLON,
                                        "Esperado ';' após atribuição");

                        return new AssignmentRecord(
                                        expression,
                                        value,
                                        thisToken.getLine(),
                                        thisToken.getColumn());
                }

                if (!(expression instanceof ThisCallRecord)
                                && !(expression instanceof MemberCallRecord)) {
                        throw syntacticError(
                                        "Um acesso a atributo não pode ser usado como comando.");
                }
                tokens.consume(
                                TokenType.SEMICOLON,
                                "Esperado ';' após chamada do método");
                return new ExpressionStatementRecord(
                                expression,
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
                type = addDeclaratorDimensions(type);

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

                ExpressionInterface targetOrCall;
                if (tokens.verify(TokenType.OPEN_PARENTHESIS)) {
                        targetOrCall = functionCall(name);
                } else {
                        targetOrCall = new VariableRecord(
                                        name.getLexeme(),
                                        name.getLine(),
                                        name.getColumn());
                }
                targetOrCall = postfixExpression(targetOrCall);

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
                                        targetOrCall,
                                        value,
                                        name.getLine(),
                                        name.getColumn());
                }

                if (!(targetOrCall instanceof CallRecord)
                                && !(targetOrCall instanceof MemberCallRecord)) {
                        if (targetOrCall instanceof MemberAccessRecord) {
                                throw syntacticError(
                                                "Um acesso a atributo não pode ser usado como comando.");
                        }
                        throw syntacticError(
                                        "A expressão não pode ser usada como comando.");
                }

                        if (consumeSemicolon) {
                                tokens.consume(
                                                TokenType.SEMICOLON,
                                                "Esperado ';' após chamada");
                        }
                return new ExpressionStatementRecord(
                                targetOrCall,
                                name.getLine(),
                                name.getColumn());
        }

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
                                                || current == TokenType.LONG
                                                || current == TokenType.DOUBLE
                                                || current == TokenType.BOOLEAN
                                                || current == TokenType.STRING
                                                || (current == TokenType.IDENTIFIER
                                                                && isVariableDeclarationStart()))) {
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

        private StatementInterface printStatement() {

                Token printToken = tokens.consume(
                                TokenType.PRINT,
                                "Esperado 'print'");

                ExpressionInterface expression = expression();

                tokens.consume(
                                TokenType.SEMICOLON,
                                "Esperado ';' após print");

                return new PrintRecord(
                                expression,
                                printToken.getLine(),
                                printToken.getColumn());
        }

        private ExpressionInterface expression() {
                ExpressionInterface primary = switch (tokens.current().getType()) {

                        case NUMBER ->
                                numberExpression();

                        case TEXT ->
                                textExpression();

                        case IDENTIFIER ->
                                identifierExpression();

                        case NEW ->
                                newObjectExpression();

                        case REGISTER ->
                                registerExpression();

                        case TRUE, FALSE ->
                                booleanExpression();

                        case NULL -> {
                                tokens.consume(TokenType.NULL, "Esperado 'null'");
                                yield new LiteralRecord(null);
                        }

                        case NEG ->
                                unaryOperation();

                        case ADD, SUB, MUL, DIV, MOD, AND, OR ->
                                operation();

                        case CMP ->
                                comparison();

                        case OPEN_PARENTHESIS ->
                                parenthesisExpression();

                        case OPEN_BRACE ->
                                arrayInitializer();

                        case SUPER ->
                                superExpression();

                        case THIS ->
                                thisExpression();

                        default -> throw syntacticError(
                                        "Expressão inválida.");
                };
                return postfixExpression(primary);
        }

        private ExpressionInterface postfixExpression(ExpressionInterface expression) {
                while (true) {
                        if (tokens.verify(TokenType.OPEN_BRACKET)) {
                                Token bracket = tokens.consume(
                                                TokenType.OPEN_BRACKET,
                                                "Esperado '[' antes do índice");
                                ExpressionInterface index = expression();
                                tokens.consume(
                                                TokenType.CLOSE_BRACKET,
                                                "Esperado ']' após o índice");
                                expression = new ArrayAccessRecord(
                                                expression,
                                                index,
                                                bracket.getLine(),
                                                bracket.getColumn());
                        } else if (tokens.verify(TokenType.DOT)) {
                                expression = memberAccess(expression);
                        } else {
                                return expression;
                        }
                }
        }

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

                if (token.getLexeme().contains(".")
                                || token.getLexeme().contains("e")
                                || token.getLexeme().contains("E")) {

                        double value = Double.parseDouble(token.getLexeme());

                        if (!Double.isFinite(value)) {
                                throw new DoubleOutOfRangeException(
                                                token.getLexeme(),
                                                token.getLine(),
                                                token.getColumn());
                        }

                        return new LiteralRecord(value);
                }

                String lexeme = token.getLexeme();
                boolean explicitLong = lexeme.endsWith("L") || lexeme.endsWith("l");
                String digits = explicitLong
                                ? lexeme.substring(0, lexeme.length() - 1)
                                : lexeme;
                try {
                        long value = Long.parseLong(digits);
                        if (explicitLong || value > Integer.MAX_VALUE) {
                                return new LiteralRecord(value);
                        }
                        return new LiteralRecord((int) value);
                } catch (NumberFormatException exception) {
                        throw new LongOutOfRangeException(
                                        lexeme,
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

        private ExpressionInterface unaryOperation() {
                Token operator = tokens.consume(
                                TokenType.NEG,
                                "Esperado 'neg'");

                ExpressionInterface operand;
                Object minimumValue = tokens.verify(TokenType.NUMBER)
                                ? minimumIntegerValue(tokens.current().getLexeme())
                                : null;
                if (minimumValue != null) {
                        tokens.consume(TokenType.NUMBER, "Esperado operando de 'neg'");
                        operand = new LiteralRecord(minimumValue);
                } else {
                        operand = expression();
                }

                return new UnaryExpressionRecord(
                                operator.getType(),
                                operand,
                                operator.getLine(),
                                operator.getColumn());
        }

        private Object minimumIntegerValue(String lexeme) {
                boolean explicitLong = lexeme.endsWith("L") || lexeme.endsWith("l");
                String digits = explicitLong
                                ? lexeme.substring(0, lexeme.length() - 1)
                                : lexeme;
                int firstNonZero = 0;
                while (firstNonZero < digits.length()
                                && digits.charAt(firstNonZero) == '0') {
                        firstNonZero++;
                }

                String magnitude = digits.substring(firstNonZero);
                if (!explicitLong && magnitude.equals("2147483648")) {
                        return Integer.MIN_VALUE;
                }
                if (magnitude.equals("9223372036854775808")) {
                        return Long.MIN_VALUE;
                }
                return null;
        }

        private ExpressionInterface identifierExpression() {

                Token name = tokens.consume(
                                TokenType.IDENTIFIER,
                                "Esperado um identificador");

                if (tokens.verify(TokenType.OPEN_PARENTHESIS)) {
                        return functionCall(name);
                }

                return new VariableRecord(
                                name.getLexeme(),
                                name.getLine(),
                                name.getColumn());
        }

        private ExpressionInterface newObjectExpression() {
                Token newToken = tokens.consume(TokenType.NEW, "Esperado 'new'");
                TypeRecord objectType = type(false);

                if (tokens.verify(TokenType.OPEN_BRACKET)) {
                        List<ExpressionInterface> dimensions = new ArrayList<>();
                        boolean sawEmptyDimension = false;
                        int dimensionCount = 0;
                        while (tokens.verify(TokenType.OPEN_BRACKET)) {
                                tokens.consume(TokenType.OPEN_BRACKET, "Esperado '['");
                                dimensionCount++;
                                if (tokens.verify(TokenType.CLOSE_BRACKET)) {
                                        sawEmptyDimension = true;
                                        dimensions.add(null);
                                } else {
                                        if (sawEmptyDimension) {
                                                throw syntacticError(
                                                                "Dimensões com tamanho não podem vir após dimensões vazias.");
                                        }
                                        dimensions.add(expression());
                                }
                                tokens.consume(
                                                TokenType.CLOSE_BRACKET,
                                                "Esperado ']' após dimensão");
                        }

                        ExpressionInterface initializer = null;
                        if (tokens.verify(TokenType.OPEN_BRACE)) {
                                if (!dimensions.isEmpty() && dimensions.get(0) != null) {
                                        throw syntacticError(
                                                        "Um array com inicializador não pode declarar tamanho na primeira dimensão.");
                                }
                                initializer = arrayInitializer();
                        } else if (dimensions.stream().allMatch(dimension -> dimension == null)) {
                                throw syntacticError(
                                                "A criação de array precisa de pelo menos uma dimensão ou inicializador.");
                        }

                        return new ArrayCreationRecord(
                                        new TypeRecord(objectType.name() + "[]".repeat(dimensionCount)),
                                        dimensions.stream().filter(dimension -> dimension != null).toList(),
                                        initializer,
                                        newToken.getLine(),
                                        newToken.getColumn());
                }

                tokens.consume(
                                TokenType.OPEN_PARENTHESIS,
                                "Esperado '(' após o tipo do objeto");
                List<ExpressionInterface> constructorArguments = arguments();
                tokens.consume(
                                TokenType.CLOSE_PARENTHESIS,
                                "Esperado ')' após argumentos do construtor");

                return new NewObjectRecord(
                                objectType,
                                constructorArguments,
                                newToken.getLine(),
                                newToken.getColumn());
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

        private ArrayInitializerRecord arrayInitializer() {
                Token openingBrace = tokens.consume(
                                TokenType.OPEN_BRACE,
                                "Esperado '{' para iniciar o array");
                List<ExpressionInterface> elements = new ArrayList<>();
                if (!tokens.verify(TokenType.CLOSE_BRACE)) {
                        do {
                                elements.add(expression());
                                if (!tokens.verify(TokenType.COMMA)) {
                                        break;
                                }
                                tokens.consume(TokenType.COMMA, "Esperado ',' entre elementos");
                                if (tokens.verify(TokenType.CLOSE_BRACE)) {
                                        break;
                                }
                        } while (true);
                }
                tokens.consume(
                                TokenType.CLOSE_BRACE,
                                "Esperado '}' após inicializador do array");
                return new ArrayInitializerRecord(
                                elements,
                                openingBrace.getLine(),
                                openingBrace.getColumn());
        }

        private boolean isVariableDeclarationStart() {
                if (tokens.peekNext().getType() == TokenType.IDENTIFIER) {
                        return true;
                }
                int offset = 1;
                while (tokens.peek(offset).getType() == TokenType.OPEN_BRACKET
                                && tokens.peek(offset + 1).getType() == TokenType.CLOSE_BRACKET) {
                        offset += 2;
                }
                return offset > 1
                                && tokens.peek(offset).getType() == TokenType.IDENTIFIER;
        }

        private ExpressionInterface registerExpression() {

                Token token = tokens.consume(
                                TokenType.REGISTER,
                                "Esperado um registrador");

                return new RegisterRecord(
                                token.getLexeme(),
                                token.getLine(),
                                token.getColumn());
        }

        private StatementInterface comparisonStatement() {

                Token comparisonToken = tokens.current();
                ExpressionInterface comparison = comparison();

                tokens.consume(
                                TokenType.SEMICOLON,
                                "Esperado ';' após comparação");

                return new ExpressionStatementRecord(
                                comparison,
                                comparisonToken.getLine(),
                                comparisonToken.getColumn());
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

        private SyntacticException syntacticError(String message) {

                Token token = tokens.current();

                return new SyntacticException(
                                message,
                                token.getLine(),
                                token.getColumn());
        }
}
