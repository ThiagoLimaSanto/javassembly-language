package compiler.backend.ir;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import compiler.ast.ExpressionInterface;
import compiler.ast.StatementInterface;
import compiler.ast.records.ArrayAccessRecord;
import compiler.ast.records.ArrayCreationRecord;
import compiler.ast.records.ArrayInitializerRecord;
import compiler.ast.records.AssignmentRecord;
import compiler.ast.records.AttributeRecord;
import compiler.ast.records.BinaryExpressionRecord;
import compiler.ast.records.BlockRecord;
import compiler.ast.records.CallRecord;
import compiler.ast.records.CatchRecord;
import compiler.ast.records.ClassRecord;
import compiler.ast.records.ConstructorInvocationRecord;
import compiler.ast.records.ConstructorRecord;
import compiler.ast.records.DeclarationRecord;
import compiler.ast.records.ExpressionStatementRecord;
import compiler.ast.records.ForRecord;
import compiler.ast.records.FreeRecord;
import compiler.ast.records.FunctionRecord;
import compiler.ast.records.IfRecord;
import compiler.ast.records.InterfaceRecord;
import compiler.ast.records.LiteralRecord;
import compiler.ast.records.MemberAccessRecord;
import compiler.ast.records.MemberCallRecord;
import compiler.ast.records.NewObjectRecord;
import compiler.ast.records.ParameterRecord;
import compiler.ast.records.PrintRecord;
import compiler.ast.records.PrintfRecord;
import compiler.ast.records.ProgramRecord;
import compiler.ast.records.ReturnRecord;
import compiler.ast.records.SuperCallRecord;
import compiler.ast.records.ThisAttributeRecord;
import compiler.ast.records.ThisCallRecord;
import compiler.ast.records.ThisRecord;
import compiler.ast.records.ThrowRecord;
import compiler.ast.records.TryRecord;
import compiler.ast.records.TypeRecord;
import compiler.ast.records.UnaryExpressionRecord;
import compiler.ast.records.VariableRecord;
import compiler.ast.records.WhileRecord;
import compiler.backend.memory.ClassLayout;
import compiler.backend.memory.FieldLayout;
import compiler.backend.memory.StaticFieldSupport;
import compiler.backend.memory.VirtualMethodTable;
import compiler.exceptions.CompilerInvariantException;
import compiler.exceptions.UnresolvedCallException;
import compiler.exceptions.UnsupportedBackendFeatureException;
import compiler.lexer.TokenType;
import compiler.semantic.BuiltinExceptionTypes;

public final class IRGenerator {
    private record ResolvedField(Operand receiver, FieldLayout field) {
    }

    private record ResolvedStaticField(String label, AttributeRecord attribute) {
    }

    private record ResolvedMethod(ClassRecord owner, FunctionRecord function) {
    }

    private record ScoredConstructor(ConstructorRecord constructor, int score) {
    }

    private final Deque<Map<String, Operand>> variableScopes = new ArrayDeque<>();
    private final Map<String, ClassLayout> classLayouts;
    private final Map<Operand, String> referenceTypesByOperand = new HashMap<>();
    private final Map<Operand, String> sourceTypesByOperand = new HashMap<>();
    private final Map<String, ClassRecord> classes = new HashMap<>();
    private final Map<String, InterfaceRecord> interfaces = new HashMap<>();
    private final Map<String, FunctionRecord> functionsByLabel = new HashMap<>();
    private ProgramRecord sourceProgram;
    private VirtualMethodTable virtualMethodTable;
    private int nextTemp;
    private int nextLabel;
    private int nextVariable;
    private final Deque<String> exceptionHandlers = new ArrayDeque<>();
    private final Deque<String> runtimeExceptionHandlers = new ArrayDeque<>();
    private final Deque<BlockRecord> activeFinallyBlocks = new ArrayDeque<>();
    private String currentClassName;
    private String currentSourceMethodLabel;
    private String currentSourceDisplayName;
    private FunctionRecord currentFunction;

    public IRGenerator() {
        this(Map.of());
    }

    public IRGenerator(Map<String, ClassLayout> classLayouts) {
        this.classLayouts = Map.copyOf(classLayouts);
    }

    public IRProgram generate(ProgramRecord ast) {
        sourceProgram = ast;
        nextTemp = 0;
        nextLabel = 0;
        nextVariable = 0;
        exceptionHandlers.clear();
        runtimeExceptionHandlers.clear();
        activeFinallyBlocks.clear();
        classes.clear();
        interfaces.clear();
        functionsByLabel.clear();
        virtualMethodTable = new VirtualMethodTable(ast);
        referenceTypesByOperand.clear();
        sourceTypesByOperand.clear();

        if (!ast.registers().isEmpty()) {
            throw unsupported("A geração de código ainda não suporta registradores globais.");
        }

        for (InterfaceRecord interfaceNode : ast.interfaces()) {
            interfaces.put(interfaceNode.name(), interfaceNode);
            for (FunctionRecord function : interfaceNode.functions()) {
                boolean supportedStatic = function.isStatic()
                        && function.block() != null
                        && !function.isAbstract()
                        && !function.isDefault()
                        && !function.isFinal()
                        && !function.isNative();
                boolean supportedInstance = !function.isStatic()
                        && function.visibility() != TokenType.PRIVATE
                        && ((function.isAbstract() && !function.isDefault() && function.block() == null)
                                || (!function.isAbstract() && function.isDefault()
                                        && function.block() != null));
                if (!supportedStatic && !supportedInstance) {
                    throw unsupported("Forma de método de interface ainda não suportada: '"
                            + interfaceNode.name() + "." + function.name() + "'.");
                }
                validateFunctionTypes(function);
                if (function.isStatic()) {
                    functionsByLabel.put(functionLabel(interfaceNode.name(), function), function);
                }
            }
        }

        for (ClassRecord classNode : ast.classes()) {
            classes.put(classNode.name(), classNode);

            for (FunctionRecord function : classNode.functions()) {
                if (function.isAbstract()) {
                    continue;
                }
                if (function.isNative()) {
                    validateNativeScannerFunction(classNode, function);
                }
                validateFunctionTypes(function);
                String label = functionLabel(classNode, function);
                functionsByLabel.put(label, function);
            }
            for (ConstructorRecord constructor : classNode.constructors()) {
                for (ParameterRecord parameter : constructor.parameters()) {
                    typeOf(parameter.type());
                }
            }
        }

        IRProgram ir = new IRProgram();
        emitStaticInitializers(ir, ast);
        for (InterfaceRecord interfaceNode : ast.interfaces()) {
            currentClassName = interfaceNode.name();
            for (FunctionRecord function : interfaceNode.functions()) {
                if (!function.isDefault() && !function.isStatic()) {
                    continue;
                }
                currentFunction = function;
                currentSourceMethodLabel = functionLabel(interfaceNode.name(), function);
                currentSourceDisplayName = interfaceNode.name() + "." + function.name();
                variableScopes.clear();
                referenceTypesByOperand.clear();
                sourceTypesByOperand.clear();
                pushVariableScope();

                ir.add(new IRInstruction(
                        IROpCode.LABEL,
                        Operand.label(currentSourceMethodLabel),
                        null,
                        null));
                emitSourceLocation(ir, function.line(), function.column());
                if (!function.isStatic()) {
                    Operand thisOperand = declareVariable("this", Operand.Type.REF);
                    registerReferenceType(thisOperand, new TypeRecord(interfaceNode.name()));
                    ir.add(new IRInstruction(
                            IROpCode.PARAM,
                            thisOperand,
                            Operand.constant(-1, Operand.Type.I32),
                            null));
                }
                emitFunctionParameters(ir, function);
                emitStatement(ir, function.block());
                if (function.returnType().name().equals("void")) {
                    ir.add(new IRInstruction(IROpCode.RETURN, null, null, null));
                }
                variableScopes.clear();
            }
        }

        for (ClassRecord classNode : ast.classes()) {
            currentClassName = classNode.name();
            for (FunctionRecord function : classNode.functions()) {
                if (function.isAbstract() || function.isNative()) {
                    continue;
                }
                currentFunction = function;
                currentSourceMethodLabel = functionLabel(classNode, function);
                currentSourceDisplayName = classNode.name() + "." + function.name();
                variableScopes.clear();
                referenceTypesByOperand.clear();
                sourceTypesByOperand.clear();
                pushVariableScope();
                ir.add(new IRInstruction(IROpCode.LABEL,
                        Operand.label(currentSourceMethodLabel), null, null));
                emitSourceLocation(ir, function.line(), function.column());

                if (!function.isStatic()) {
                    Operand thisOperand = declareVariable("this", Operand.Type.REF);
                    registerReferenceType(thisOperand, new TypeRecord(classNode.name()));
                    ir.add(new IRInstruction(IROpCode.PARAM,
                            thisOperand,
                            Operand.constant(-1, Operand.Type.I32), null));
                }

                emitFunctionParameters(ir, function);
                emitStatement(ir, function.block());

                if (function.returnType().name().equals("void")) {
                    ir.add(new IRInstruction(IROpCode.RETURN, null, null, null));
                }
                variableScopes.clear();
            }
            if (classNode.constructors().isEmpty()) {
                emitConstructor(ir, classNode, null);
            } else {
                for (ConstructorRecord constructor : classNode.constructors()) {
                    emitConstructor(ir, classNode, constructor);
                }
            }
        }
        currentFunction = null;
        currentClassName = null;
        currentSourceMethodLabel = null;
        currentSourceDisplayName = null;
        return ir;
    }

    private void emitFunctionParameters(IRProgram ir, FunctionRecord function) {
        for (int index = 0; index < function.parameters().size(); index++) {
            ParameterRecord parameter = function.parameters().get(index);
            Operand parameterOperand = declareVariable(parameter.name(), typeOf(parameter.type()));
            registerReferenceType(parameterOperand, parameter.type());
            ir.add(new IRInstruction(
                    IROpCode.PARAM,
                    parameterOperand,
                    Operand.constant(index, Operand.Type.I32),
                    null));
        }
    }

    private void emitStaticInitializers(IRProgram ir, ProgramRecord ast) {
        FunctionRecord initializer = new FunctionRecord(
                TokenType.PUBLIC, true, false, new TypeRecord("void"),
                "<static-init>", List.of(), null, false, false, false, 0, 0);
        currentFunction = initializer;
        currentSourceMethodLabel = "__JVS.static_init__void__ret_void";
        currentSourceDisplayName = "<static-init>";
        variableScopes.clear();
        pushVariableScope();
        ir.add(new IRInstruction(IROpCode.LABEL,
                Operand.label("__JVS.static_init__void__ret_void"), null, null));
        for (ClassRecord classNode : ast.classes()) {
            currentClassName = classNode.name();
            for (AttributeRecord attribute : classNode.attributes()) {
                if (!attribute.isStatic() || attribute.value() == null) continue;
                emitSourceLocation(ir, attribute.line(), attribute.column());
                Operand value = attribute.value() instanceof ArrayInitializerRecord arrayInitializer
                        ? emitArrayInitializer(ir, attribute.type().name(), arrayInitializer)
                        : coerce(ir, expression(ir, attribute.value()), typeOf(attribute.type()));
                ir.add(new IRInstruction(IROpCode.STORE_STATIC, null,
                        Operand.label(StaticFieldSupport.label(classNode.name(), attribute.name())),
                        null, List.of(value)));
            }
        }
        ir.add(new IRInstruction(IROpCode.RETURN, null, null, null));
        variableScopes.clear();
        currentFunction = null;
        currentClassName = null;
        currentSourceMethodLabel = null;
        currentSourceDisplayName = null;
    }

    private void validateFunctionTypes(FunctionRecord function) {
        if (!function.returnType().name().equals("void")) {
            typeOf(function.returnType());
        }
        for (ParameterRecord parameter : function.parameters()) {
            typeOf(parameter.type());
        }
    }

    private void validateNativeScannerFunction(ClassRecord owner, FunctionRecord function) {
        String expectedReturn = switch (function.name()) {
            case "readInt" -> "int";
            case "readLong" -> "long";
            case "readDouble" -> "double";
            case "readBoolean" -> "boolean";
            case "readString", "readLine" -> "String";
            default -> null;
        };
        if (!(owner.name().equals("io.Scanner") || owner.name().equals("jvs.io.Scanner"))
                || expectedReturn == null
                || !function.parameters().isEmpty()
                || !function.returnType().name().equals(expectedReturn)) {
            throw unsupported("Método native ainda sem implementação no runtime: '"
                    + owner.name() + "." + function.name() + "'.");
        }
    }

    private void emitConstructor(
            IRProgram ir,
            ClassRecord owner,
            ConstructorRecord constructor) {
        List<ParameterRecord> parameters = constructor == null
                ? List.of()
                : constructor.parameters();
        BlockRecord body = constructor == null
                ? new BlockRecord(List.of())
                : constructor.block();
        currentClassName = owner.name();
        currentSourceMethodLabel = constructorLabel(owner, constructor);
        currentSourceDisplayName = owner.name() + ".<init>";
        currentFunction = new FunctionRecord(
                constructor == null ? TokenType.PUBLIC : constructor.visibility(),
                false,
                false,
                new TypeRecord("void"),
                owner.name(),
                parameters,
                body,
                constructor == null ? owner.line() : constructor.line(),
                constructor == null ? owner.column() : constructor.column());
        variableScopes.clear();
        referenceTypesByOperand.clear();
        sourceTypesByOperand.clear();
        pushVariableScope();

        Operand thisOperand = declareVariable("this", Operand.Type.REF);
        registerReferenceType(thisOperand, new TypeRecord(owner.name()));
        ir.add(new IRInstruction(
                IROpCode.LABEL,
                Operand.label(constructorLabel(owner, constructor)),
                null,
                null));
        emitSourceLocation(ir, constructor == null ? owner.line() : constructor.line(),
                constructor == null ? owner.column() : constructor.column());
        ir.add(new IRInstruction(
                IROpCode.PARAM,
                thisOperand,
                Operand.constant(-1, Operand.Type.I32),
                null));

        for (int index = 0; index < parameters.size(); index++) {
            ParameterRecord parameter = parameters.get(index);
            Operand parameterOperand = declareVariable(parameter.name(), typeOf(parameter.type()));
            registerReferenceType(parameterOperand, parameter.type());
            ir.add(new IRInstruction(
                    IROpCode.PARAM,
                    parameterOperand,
                    Operand.constant(index, Operand.Type.I32),
                    null));
        }

        ConstructorInvocationRecord invocation = constructor == null ? null : constructor.invocation();
        boolean delegatesToThis = invocation != null && invocation.delegatesToThis();
        String targetClassName = delegatesToThis
                ? owner.name()
                : owner.superClass();
        List<Operand> invocationArguments = new ArrayList<>();
        if (invocation != null) {
            for (ExpressionInterface argument : invocation.arguments()) {
                invocationArguments.add(expression(ir, argument));
            }
        }
        if (targetClassName != null && !targetClassName.equals("Object")
                && !BuiltinExceptionTypes.isBuiltin(targetClassName)) {
            ClassRecord targetClass = classes.get(targetClassName);
            if (targetClass == null) {
                throw unsupported("Classe pai não encontrada: " + targetClassName + ".");
            }
            ConstructorRecord targetConstructor = selectConstructor(targetClassName, invocationArguments);
            List<Operand> converted = convertConstructorArguments(ir, targetConstructor, invocationArguments);
            List<Operand> callArguments = new ArrayList<>(converted.size() + 1);
            callArguments.add(thisOperand);
            callArguments.addAll(converted);
            ir.add(new IRInstruction(
                    IROpCode.CALL,
                    null,
                    Operand.label(constructorLabel(targetClass, targetConstructor)),
                    null,
                    callArguments));
            emitExceptionCheck(ir);
        } else if (!invocationArguments.isEmpty()) {
            throw unsupported("O construtor de Object não aceita argumentos.");
        }

        if (!delegatesToThis) {
            initializeDeclaredFields(ir, thisOperand, owner);
        }
        emitStatement(ir, body);
        ir.add(new IRInstruction(IROpCode.RETURN, null, null, null));
        variableScopes.clear();
        currentFunction = null;
        currentSourceMethodLabel = null;
        currentSourceDisplayName = null;
    }

    private ConstructorRecord selectConstructor(String className, List<Operand> arguments) {
        ClassRecord classNode = classes.get(className);
        if (classNode == null) {
            throw unsupported("Classe não encontrada: " + className + ".");
        }
        if (classNode.constructors().isEmpty()) {
            if (!arguments.isEmpty()) {
                throw unsupported("O construtor implícito de '" + className + "' não recebe argumentos.");
            }
            return null;
        }

        List<ScoredConstructor> matches = new ArrayList<>();
        for (ConstructorRecord candidate : classNode.constructors()) {
            if (candidate.parameters().size() != arguments.size()) {
                continue;
            }
            int score = 0;
            boolean applicable = true;
            for (int index = 0; index < arguments.size(); index++) {
                int conversion = conversionScore(arguments.get(index), candidate.parameters().get(index).type());
                if (conversion < 0) {
                    applicable = false;
                    break;
                }
                score += conversion;
            }
            if (!applicable) {
                continue;
            }
            matches.add(new ScoredConstructor(candidate, score));
        }
        if (matches.isEmpty()) {
            throw unsupported("Nenhum construtor correspondente encontrado para '" + className + "'.");
        }
        int bestScore = matches.stream().mapToInt(ScoredConstructor::score).min().orElseThrow();
        List<ConstructorRecord> bestCandidates = matches.stream()
                .filter(candidate -> candidate.score() == bestScore)
                .map(ScoredConstructor::constructor)
                .toList();
        ConstructorRecord best = null;
        for (ConstructorRecord candidate : bestCandidates) {
            boolean mostSpecific = true;
            for (ConstructorRecord other : bestCandidates) {
                if (candidate != other && !isMoreSpecific(candidate, other)) {
                    mostSpecific = false;
                    break;
                }
            }
            if (mostSpecific) {
                if (best != null) {
                    throw unsupported("Chamada ambígua de construtor para '" + className + "'.");
                }
                best = candidate;
            }
        }
        if (best == null) {
            throw unsupported("Chamada ambígua de construtor para '" + className + "'.");
        }
        return best;
    }

    private int conversionScore(Operand argument, TypeRecord parameterType) {
        Operand.Type expected = typeOf(parameterType);
        if (argument.type() == expected) {
            String actualSourceType = sourceTypesByOperand.get(argument);
            String expectedSourceType = parameterType.name();
            if (argument.type() != Operand.Type.REF) {
                return 0;
            }
            if (argument.kind() == Operand.Kind.CONSTANT && argument.value() == null) {
                return 1;
            }
            if (argument.kind() == Operand.Kind.CONSTANT && argument.value() instanceof String) {
                actualSourceType = "String";
            }
            if (actualSourceType == null) {
                return -1;
            }
            if (actualSourceType.equals(expectedSourceType)) {
                return 0;
            }
            return expectedSourceType.equals("Object")
                    || isSubclassOf(actualSourceType, expectedSourceType) ? 1 : -1;
        }
        if (argument.type() == Operand.Type.I32 && expected == Operand.Type.I64) {
            return 1;
        }
        if (isInteger(argument.type()) && expected == Operand.Type.F64) {
            return argument.type() == Operand.Type.I32 ? 2 : 1;
        }
        return -1;
    }

    private boolean isSubclassOf(String className, String expectedClassName) {
        String current = className;
        while (current != null) {
            if (current.equals(expectedClassName)) {
                return true;
            }
            ClassRecord classNode = classes.get(current);
            current = classNode == null ? null : classNode.superClass();
        }
        return false;
    }

    private boolean isMoreSpecific(ConstructorRecord candidate, ConstructorRecord other) {
        boolean strict = false;
        for (int index = 0; index < candidate.parameters().size(); index++) {
            String candidateType = candidate.parameters().get(index).type().name();
            String otherType = other.parameters().get(index).type().name();
            if (candidateType.equals(otherType)) {
                continue;
            }
            Operand.Type candidateOperandType = typeOf(new TypeRecord(candidateType));
            Operand.Type otherOperandType = typeOf(new TypeRecord(otherType));
            boolean moreSpecific;
            if (isInteger(candidateOperandType) && otherOperandType == Operand.Type.F64) {
                moreSpecific = true;
            } else if (candidateOperandType == Operand.Type.I32
                    && otherOperandType == Operand.Type.I64) {
                moreSpecific = true;
            } else if (candidateOperandType == Operand.Type.REF
                    && otherOperandType == Operand.Type.REF) {
                moreSpecific = isSubclassOf(candidateType, otherType)
                        || ((candidateType.endsWith("[]") || candidateType.equals("String"))
                                && otherType.equals("Object"));
            } else {
                return false;
            }
            if (!moreSpecific) {
                return false;
            }
            strict = true;
        }
        return strict;
    }

    private List<Operand> convertConstructorArguments(
            IRProgram ir,
            ConstructorRecord constructor,
            List<Operand> arguments) {
        if (constructor == null) {
            return List.of();
        }
        List<Operand> converted = new ArrayList<>(arguments.size());
        for (int index = 0; index < arguments.size(); index++) {
            converted.add(coerce(ir, arguments.get(index), typeOf(constructor.parameters().get(index).type())));
        }
        return converted;
    }

    private String constructorLabel(ClassRecord owner, ConstructorRecord constructor) {
        List<String> parameters = constructor == null
                ? List.of()
                : constructor.parameters().stream()
                        .map(parameter -> mangleType(parameter.type().name()))
                        .toList();
        return owner.name() + ".__ctor__"
                + (parameters.isEmpty() ? "void" : String.join("__", parameters));
    }

    private void emitStatement(IRProgram ir, StatementInterface statement) {
        if (statement == null) {
            return;
        }
        if (!(statement instanceof BlockRecord) && currentSourceMethodLabel != null) {
            emitSourceLocation(ir, statement.line(), statement.column());
        }
        if (statement instanceof BlockRecord block) {
            pushVariableScope();
            try {
                for (StatementInterface child : block.commands())
                    emitStatement(ir, child);
            } finally {
                popVariableScope();
            }
        } else if (statement instanceof DeclarationRecord declaration) {
            Operand.Type type = typeOf(declaration.type());
            Operand destination = declareVariable(declaration.name(), type);
            registerReferenceType(destination, declaration.type());
            if (declaration.value() != null) {
                Operand source;
                if (declaration.value() instanceof ArrayInitializerRecord initializer) {
                    source = emitArrayInitializer(ir, declaration.type().name(), initializer);
                } else {
                    source = coerce(ir, expression(ir, declaration.value()), type);
                }
                ir.add(new IRInstruction(IROpCode.ASSIGN,
                        destination, source, null));
            }
        } else if (statement instanceof ReturnRecord returned) {
            Operand value = returned.value() == null ? null
                    : returned.value() instanceof ArrayInitializerRecord initializer
                            ? emitArrayInitializer(ir, currentFunction.returnType().name(), initializer)
                            : expression(ir, returned.value());
            if (value != null) {
                value = coerce(ir, value, typeOf(currentFunction.returnType()));
            }
            List<BlockRecord> cleanups = new ArrayList<>(activeFinallyBlocks);
            for (String handler : runtimeExceptionHandlers) {
                ir.add(new IRInstruction(IROpCode.POP_EXCEPTION_HANDLER_IF,
                        Operand.label(handler), null, null));
            }
            for (BlockRecord finallyBlock : cleanups) {
                activeFinallyBlocks.pop();
                emitStatement(ir, finallyBlock);
            }
            for (int index = cleanups.size() - 1; index >= 0; index--) {
                activeFinallyBlocks.push(cleanups.get(index));
            }
            ir.add(new IRInstruction(IROpCode.CLEAR_EXCEPTION, null, null, null));
            ir.add(new IRInstruction(IROpCode.RETURN, null, value, null));
        } else if (statement instanceof ThrowRecord thrown) {
            // A throw inside finally replaces the exception whose cleanup is running.
            // Clear it before evaluating the new throwable so failures during that
            // evaluation also propagate as the active exception.
            ir.add(new IRInstruction(IROpCode.CLEAR_EXCEPTION, null, null, null));
            Operand exception = expression(ir, thrown.expression());
            if (exception.type() != Operand.Type.REF) {
                throw unsupported("throw exige referência a um objeto.");
            }
            String handler = exceptionHandlers.peek();
            ir.add(new IRInstruction(IROpCode.THROW,
                    handler == null ? null : Operand.label(handler), exception,
                    exception == null ? null : Operand.constant(
                            sourceTypesByOperand.getOrDefault(exception,
                                    referenceTypesByOperand.get(exception)), Operand.Type.REF)));
        } else if (statement instanceof TryRecord tryRecord) {
            emitTryStatement(ir, tryRecord);
        } else if (statement instanceof IfRecord conditional) {
            String elseLabel = newLabel();
            String endLabel = newLabel();
            Operand condition = expression(ir, conditional.condition());
            ir.add(new IRInstruction(IROpCode.JUMP_IF_FALSE, Operand.label(elseLabel), condition, null));
            emitStatement(ir, conditional.thenBranch());
            if (conditional.elseBranch() != null) {
                ir.add(new IRInstruction(IROpCode.JUMP, Operand.label(endLabel), null, null));
            }
            ir.add(new IRInstruction(IROpCode.LABEL, Operand.label(elseLabel), null, null));
            if (conditional.elseBranch() != null) {
                emitStatement(ir, conditional.elseBranch());
                ir.add(new IRInstruction(IROpCode.LABEL, Operand.label(endLabel), null, null));
            }
        } else if (statement instanceof WhileRecord loop) {
            String testLabel = newLabel();
            String endLabel = newLabel();
            ir.add(new IRInstruction(IROpCode.LABEL, Operand.label(testLabel), null, null));
            Operand condition = expression(ir, loop.expression());
            ir.add(new IRInstruction(IROpCode.JUMP_IF_FALSE, Operand.label(endLabel), condition, null));
            emitStatement(ir, loop.block());
            ir.add(new IRInstruction(IROpCode.JUMP, Operand.label(testLabel), null, null));
            ir.add(new IRInstruction(IROpCode.LABEL, Operand.label(endLabel), null, null));
        } else if (statement instanceof ForRecord loop) {
            pushVariableScope();
            try {
                String testLabel = newLabel();
                String endLabel = newLabel();
                emitStatement(ir, loop.initializer());
                ir.add(new IRInstruction(IROpCode.LABEL, Operand.label(testLabel), null, null));
                if (loop.condition() != null) {
                    Operand condition = expression(ir, loop.condition());
                    ir.add(new IRInstruction(IROpCode.JUMP_IF_FALSE, Operand.label(endLabel), condition, null));
                }
                emitStatement(ir, loop.block());
                emitStatement(ir, loop.update());
                ir.add(new IRInstruction(IROpCode.JUMP, Operand.label(testLabel), null, null));
                ir.add(new IRInstruction(IROpCode.LABEL, Operand.label(endLabel), null, null));
            } finally {
                popVariableScope();
            }
        } else if (statement instanceof PrintRecord print) {
            Operand value = expression(ir, print.expression());
            if (value.type() != Operand.Type.I32 && value.type() != Operand.Type.I64
                    && value.type() != Operand.Type.F64 && value.type() != Operand.Type.BOOL
                    && !(value.type() == Operand.Type.REF && isStringExpression(print.expression(), value))) {
                throw unsupported("print suporta int, long, double, boolean e String no backend atual.");
            }
            ir.add(new IRInstruction(
                    print.lineBreak() ? IROpCode.PRINT_LN : IROpCode.PRINT,
                    null,
                    value,
                    null));
        } else if (statement instanceof PrintfRecord printf) {
            if (!(printf.format() instanceof LiteralRecord literal)
                    || !(literal.value() instanceof String format)) {
                throw unsupported("O formato do printf deve ser um literal de texto.");
            }
            List<Operand> arguments = new ArrayList<>();
            for (ExpressionInterface argument : printf.arguments()) {
                Operand value = expression(ir, argument);
                if (value.type() != Operand.Type.I32 && value.type() != Operand.Type.I64
                        && value.type() != Operand.Type.F64 && value.type() != Operand.Type.BOOL
                        && value.type() != Operand.Type.REF) {
                    throw unsupported("Tipo não suportado como argumento de printf: " + value.type() + ".");
                }
                arguments.add(value);
            }
            ir.add(new IRInstruction(
                    printf.lineBreak() ? IROpCode.PRINT_FORMAT_LN : IROpCode.PRINT_FORMAT,
                    null,
                    Operand.constant(format, Operand.Type.REF), null, arguments));
        } else if (statement instanceof FreeRecord free) {
            Operand object = expression(ir, free.expression());
            boolean nullLiteral = free.expression() instanceof LiteralRecord literal
                    && literal.value() == null;
            String sourceType = sourceTypesByOperand.get(object);
            boolean arrayReference = sourceType != null && sourceType.endsWith("[]");
            boolean classReference = referenceTypesByOperand.containsKey(object);
            if (object.type() != Operand.Type.REF
                    || (!classReference && !arrayReference && !nullLiteral)) {
                throw unsupported("free exige uma referência a um objeto ou array.");
            }
            ir.add(new IRInstruction(
                    arrayReference ? IROpCode.FREE_ARRAY : IROpCode.FREE_OBJECT,
                    null,
                    object,
                    null));
        } else if (statement instanceof AssignmentRecord assignment) {
            if (assignment.target() instanceof VariableRecord variable) {
                Operand destination = resolveVariable(variable.name());
                ResolvedStaticField staticField = destination == null
                        ? resolveStaticField(currentClassName, variable.name()) : null;
                if (staticField != null) {
                    Operand value = coerce(
                            ir, expression(ir, assignment.value()), typeOf(staticField.attribute().type()));
                    ir.add(new IRInstruction(IROpCode.STORE_STATIC, null,
                            Operand.label(staticField.label()), null, List.of(value)));
                } else if (destination == null && currentFunction != null && !currentFunction.isStatic()) {
                    ResolvedField target = resolveField(
                            currentClassName,
                            variable.name(),
                            resolveVariable("this"));
                    FieldLayout field = target.field();
                    Operand value = coerce(
                            ir,
                            expression(ir, assignment.value()),
                            typeOf(new TypeRecord(field.type())));
                    ir.add(new IRInstruction(
                            IROpCode.STORE_FIELD,
                            null,
                            target.receiver(),
                            Operand.constant(field.offSet(), Operand.Type.I32),
                            List.of(value)));
                } else {
                    if (destination == null) {
                        throw unsupported("Variável sem símbolo na geração de IR: " + variable.name() + ".");
                    }
                    Operand value;
                    if (assignment.value() instanceof ArrayInitializerRecord initializer) {
                        String expectedArrayType = sourceTypesByOperand.get(destination);
                        value = emitArrayInitializer(ir, expectedArrayType, initializer);
                    } else {
                        value = coerce(ir, expression(ir, assignment.value()), destination.type());
                    }
                    ir.add(new IRInstruction(IROpCode.ASSIGN, destination, value, null));
                }
            } else if (assignment.target() instanceof MemberAccessRecord member) {
                ClassRecord staticOwner = resolveStaticClassReceiver(member.receiver());
                ResolvedStaticField staticField = staticOwner == null
                        ? null : resolveStaticField(staticOwner.name(), member.name());
                if (staticField != null) {
                    Operand value = coerce(
                            ir, expression(ir, assignment.value()), typeOf(staticField.attribute().type()));
                    ir.add(new IRInstruction(IROpCode.STORE_STATIC, null,
                            Operand.label(staticField.label()), null, List.of(value)));
                    return;
                }
                ResolvedField target = resolveField(ir, member);
                FieldLayout field = target.field();
                Operand value = assignment.value() instanceof ArrayInitializerRecord initializer
                        ? emitArrayInitializer(ir, field.type(), initializer)
                        : coerce(
                                ir,
                                expression(ir, assignment.value()),
                                typeOf(new TypeRecord(field.type())));
                ir.add(new IRInstruction(
                        IROpCode.STORE_FIELD,
                        null,
                        target.receiver(),
                        Operand.constant(field.offSet(), Operand.Type.I32),
                        List.of(value)));
            } else if (assignment.target() instanceof ThisAttributeRecord attribute) {
                Operand receiver = resolveVariable("this");
                ResolvedField target = resolveField(currentClassName, attribute.name(), receiver);
                FieldLayout field = target.field();
                Operand value = coerce(
                        ir,
                        expression(ir, assignment.value()),
                        typeOf(new TypeRecord(field.type())));
                ir.add(new IRInstruction(
                        IROpCode.STORE_FIELD,
                        null,
                        receiver,
                        Operand.constant(field.offSet(), Operand.Type.I32),
                        List.of(value)));
            } else if (assignment.target() instanceof ArrayAccessRecord access) {
                Operand array = expression(ir, access.array());
                Operand index = expression(ir, access.index());
                String elementType = componentTypeName(sourceTypesByOperand.get(array));
                Operand value = coerce(
                        ir,
                        expression(ir, assignment.value()),
                        typeOf(new TypeRecord(elementType)));
                ir.add(new IRInstruction(
                        IROpCode.STORE_ARRAY,
                        null,
                        array,
                        index,
                        List.of(value, Operand.constant(referenceTypeName(value), Operand.Type.REF))));
            } else {
                throw unsupported("Atribuição ainda não suportada pelo backend.");
            }
        } else if (statement instanceof ExpressionStatementRecord expressionStatement) {
            if (expressionStatement.expression() instanceof CallRecord call) {
                emitCall(ir, call, false);
            } else if (expressionStatement.expression() instanceof MemberCallRecord call) {
                emitMemberCall(ir, call, false);
            } else if (expressionStatement.expression() instanceof ThisCallRecord call) {
                emitThisCall(ir, call, false);
            } else if (expressionStatement.expression() instanceof SuperCallRecord call) {
                emitSuperCall(ir, call, false);
            } else {
                throw unsupported("Este tipo de expressão como comando ainda não é suportado.");
            }
        } else {
            throw unsupported("Comando ainda não suportado: " + statement.getClass().getSimpleName() + ".");
        }
    }

    private void emitSourceLocation(IRProgram ir, int line, int column) {
        if (currentSourceMethodLabel == null || currentSourceDisplayName == null) return;
        String sourceFile = sourceProgram == null
                ? "<source>"
                : sourceProgram.sourceFiles().getOrDefault(currentClassName, "<source>");
        ir.add(new IRInstruction(IROpCode.SOURCE_LOCATION,
                Operand.label(currentSourceDisplayName),
                Operand.constant(line, Operand.Type.I32),
                Operand.constant(column, Operand.Type.I32),
                List.of(Operand.constant(sourceFile, Operand.Type.REF))));
    }

    private void emitTryStatement(IRProgram ir, TryRecord statement) {
        String exceptionEntry = newLabel();
        String finallyLabel = statement.finallyBlock() == null ? null : newLabel();
        String endLabel = newLabel();
        String outerHandler = exceptionHandlers.peek();
        exceptionHandlers.push(exceptionEntry);
        runtimeExceptionHandlers.push(exceptionEntry);
        ir.add(new IRInstruction(IROpCode.PUSH_EXCEPTION_HANDLER,
                Operand.label(exceptionEntry), null, null));
        if (statement.finallyBlock() != null) activeFinallyBlocks.push(statement.finallyBlock());
        emitStatement(ir, statement.tryBlock());
        exceptionHandlers.pop();
        ir.add(new IRInstruction(IROpCode.POP_EXCEPTION_HANDLER_IF,
                Operand.label(exceptionEntry), null, null));
        runtimeExceptionHandlers.pop();
        ir.add(new IRInstruction(IROpCode.JUMP,
                Operand.label(finallyLabel == null ? endLabel : finallyLabel), null, null));
        ir.add(new IRInstruction(IROpCode.LABEL, Operand.label(exceptionEntry), null, null));
        ir.add(new IRInstruction(IROpCode.POP_EXCEPTION_HANDLER_IF,
                Operand.label(exceptionEntry), null, null));

        for (CatchRecord handler : statement.catches()) {
            String nextCatch = newLabel();
            Operand matches = Operand.temporary("t" + nextTemp++, Operand.Type.BOOL);
            ir.add(new IRInstruction(IROpCode.CATCH_MATCH, matches,
                    null, Operand.label(handler.type().name())));
            ir.add(new IRInstruction(IROpCode.JUMP_IF_FALSE,
                    Operand.label(nextCatch), matches, null));
            pushVariableScope();
            Operand exception = declareVariable(handler.name(), Operand.Type.REF);
            registerReferenceType(exception, handler.type());
            ir.add(new IRInstruction(IROpCode.GET_EXCEPTION, exception, null, null));
            ir.add(new IRInstruction(IROpCode.CLEAR_EXCEPTION, null, null, null));
            String catchExceptionHandler = finallyLabel != null ? finallyLabel : outerHandler;
            if (catchExceptionHandler != null) {
                exceptionHandlers.push(catchExceptionHandler);
            }
            if (finallyLabel != null) {
                runtimeExceptionHandlers.push(catchExceptionHandler);
                ir.add(new IRInstruction(IROpCode.PUSH_EXCEPTION_HANDLER,
                        Operand.label(catchExceptionHandler), null, null));
            }
            emitStatement(ir, handler.block());
            if (catchExceptionHandler != null) {
                if (finallyLabel != null) {
                    ir.add(new IRInstruction(IROpCode.POP_EXCEPTION_HANDLER_IF,
                            Operand.label(catchExceptionHandler), null, null));
                }
            }
            if (finallyLabel != null) {
                runtimeExceptionHandlers.pop();
            }
            if (catchExceptionHandler != null) {
                exceptionHandlers.pop();
            }
            popVariableScope();
            ir.add(new IRInstruction(IROpCode.JUMP,
                    Operand.label(finallyLabel == null ? endLabel : finallyLabel), null, null));
            ir.add(new IRInstruction(IROpCode.LABEL, Operand.label(nextCatch), null, null));
        }

        if (finallyLabel != null) {
            ir.add(new IRInstruction(IROpCode.LABEL, Operand.label(finallyLabel), null, null));
            ir.add(new IRInstruction(IROpCode.POP_EXCEPTION_HANDLER_IF,
                    Operand.label(finallyLabel), null, null));
            activeFinallyBlocks.pop();
            emitStatement(ir, statement.finallyBlock());
            ir.add(new IRInstruction(IROpCode.CHECK_EXCEPTION,
                    outerHandler == null ? null : Operand.label(outerHandler), null, null));
        } else {
            ir.add(new IRInstruction(IROpCode.CHECK_EXCEPTION,
                    outerHandler == null ? null : Operand.label(outerHandler), null, null));
        }
        ir.add(new IRInstruction(IROpCode.LABEL, Operand.label(endLabel), null, null));
    }

    private void emitExceptionCheck(IRProgram ir) {
        String handler = exceptionHandlers.peek();
        ir.add(new IRInstruction(IROpCode.CHECK_EXCEPTION,
                handler == null ? null : Operand.label(handler), null, null));
    }

    private void pushVariableScope() {
        variableScopes.push(new HashMap<>());
    }

    private void popVariableScope() {
        if (variableScopes.isEmpty()) {
            throw new CompilerInvariantException("Pilha de escopos da IR vazia.");
        }
        variableScopes.pop();
    }

    private Operand declareVariable(String sourceName, Operand.Type type) {
        if (variableScopes.isEmpty()) {
            throw new CompilerInvariantException("Declaração de variável sem escopo ativo na IR.");
        }
        String internalName = "v" + nextVariable++;
        Operand variable = Operand.variable(internalName, type);
        variableScopes.peek().put(sourceName, variable);
        return variable;
    }

    private Operand resolveVariable(String sourceName) {
        for (Map<String, Operand> scope : variableScopes) {
            Operand variable = scope.get(sourceName);
            if (variable != null) {
                return variable;
            }
        }
        return null;
    }

    private void registerReferenceType(Operand operand, TypeRecord sourceType) {
        String className = sourceType.name();
        sourceTypesByOperand.put(operand, className);
        if (classes.containsKey(className)) {
            referenceTypesByOperand.put(operand, className);
        }
    }

    private Operand expression(IRProgram ir, ExpressionInterface expression) {
        if (expression instanceof LiteralRecord literal) {
            Object value = literal.value();
            if (value instanceof Integer)
                return Operand.constant(value, Operand.Type.I32);
            if (value instanceof Long)
                return Operand.constant(value, Operand.Type.I64);
            if (value instanceof Double)
                return Operand.constant(value, Operand.Type.F64);
            if (value instanceof Boolean)
                return Operand.constant(value, Operand.Type.BOOL);
            if (value == null)
                return Operand.constant(null, Operand.Type.REF);
            if (value instanceof String)
                return Operand.constant(value, Operand.Type.REF);
            throw unsupported("Literal de texto ainda não é suportado pelo backend.");
        }
        if (expression instanceof VariableRecord variable) {
            Operand operand = resolveVariable(variable.name());
            if (operand == null) {
                ResolvedStaticField staticField = resolveStaticField(currentClassName, variable.name());
                if (staticField != null) {
                    return loadStaticField(ir, staticField);
                }
                if (currentFunction != null && !currentFunction.isStatic()) {
                    ResolvedField field = resolveField(
                            currentClassName,
                            variable.name(),
                            resolveVariable("this"));
                    return loadField(ir, field);
                }
                throw unsupported("Variável sem símbolo na geração de IR: " + variable.name() + ".");
            }
            return operand;
        }
        if (expression instanceof ThisRecord) {
            Operand thisOperand = resolveVariable("this");
            if (thisOperand == null) {
                throw unsupported("this só pode ser usado em um método de instância.");
            }
            return thisOperand;
        }
        if (expression instanceof ThisAttributeRecord attribute) {
            Operand receiver = resolveVariable("this");
            ResolvedField resolved = resolveField(currentClassName, attribute.name(), receiver);
            return loadField(ir, resolved);
        }
        if (expression instanceof MemberAccessRecord member) {
            ClassRecord staticOwner = resolveStaticClassReceiver(member.receiver());
            ResolvedStaticField staticField = staticOwner == null
                    ? null : resolveStaticField(staticOwner.name(), member.name());
            if (staticField != null) {
                return loadStaticField(ir, staticField);
            }
            Operand memberReceiver = expression(ir, member.receiver());
            String receiverType = sourceTypesByOperand.get(memberReceiver);
            if (member.name().equals("length")
                    && receiverType != null && receiverType.endsWith("[]")) {
                Operand result = Operand.temporary("t" + nextTemp++, Operand.Type.I32);
                ir.add(new IRInstruction(IROpCode.ARRAY_LENGTH, result, memberReceiver, null));
                return result;
            }
            ResolvedField resolved = resolveField(ir, member, memberReceiver);
            FieldLayout field = resolved.field();

            return loadField(ir, resolved);
        }
        if (expression instanceof MemberCallRecord call) {
            return emitMemberCall(ir, call, true);
        }
        if (expression instanceof ThisCallRecord call) {
            return emitThisCall(ir, call, true);
        }
        if (expression instanceof SuperCallRecord call) {
            return emitSuperCall(ir, call, true);
        }
        if (expression instanceof ArrayAccessRecord access) {
            Operand array = expression(ir, access.array());
            Operand index = expression(ir, access.index());
            String elementType = componentTypeName(sourceTypesByOperand.get(array));
            Operand result = Operand.temporary(
                    "t" + nextTemp++,
                    typeOf(new TypeRecord(elementType)));
            ir.add(new IRInstruction(IROpCode.LOAD_ARRAY, result, array, index));
            registerReferenceType(result, new TypeRecord(elementType));
            return result;
        }
        if (expression instanceof ArrayCreationRecord creation) {
            return emitArrayCreation(ir, creation);
        }
        if (expression instanceof ArrayInitializerRecord) {
            throw unsupported("Inicializador de array sem tipo esperado.");
        }
        if (expression instanceof UnaryExpressionRecord unary) {
            if (unary.operator() != TokenType.NEG) {
                throw unsupported("Operador unário ainda não suportado: " + unary.operator() + ".");
            }
            Operand operand = expression(ir, unary.operand());
            if (!isNumeric(operand.type())) {
                throw unsupported("neg só suporta int, long e double no backend atual.");
            }
            Operand result = Operand.temporary("t" + nextTemp++, operand.type());
            ir.add(new IRInstruction(IROpCode.NEG, result, operand, null));
            return result;
        }
        if (expression instanceof BinaryExpressionRecord binary) {
            Operand left = expression(ir, binary.left());
            Operand right = expression(ir, binary.right());
            IROpCode opcode = switch (binary.operator()) {
                case ADD -> IROpCode.ADD;
                case SUB -> IROpCode.SUB;
                case MUL -> IROpCode.MUL;
                case DIV -> IROpCode.DIV;
                case MOD -> IROpCode.MOD;
                case LT -> IROpCode.LT;
                case GT -> IROpCode.GT;
                case LE -> IROpCode.LE;
                case GE -> IROpCode.GE;
                case EQ -> IROpCode.EQ;
                case NE -> IROpCode.NE;
                default -> throw unsupported("Operador ainda não suportado: " + binary.operator() + ".");
            };
            boolean comparison = switch (opcode) {
                case LT, GT, LE, GE, EQ, NE -> true;
                default -> false;
            };
            Operand.Type operationType;
            if (left.type() == Operand.Type.F64 || right.type() == Operand.Type.F64) {
                if (!isNumeric(left.type()) || !isNumeric(right.type())) {
                    throw unsupported("Operação entre tipos incompatíveis no backend.");
                }
                left = coerce(ir, left, Operand.Type.F64);
                right = coerce(ir, right, Operand.Type.F64);
                operationType = Operand.Type.F64;
            } else if (left.type() == Operand.Type.I64 || right.type() == Operand.Type.I64) {
                if (!isInteger(left.type()) || !isInteger(right.type())) {
                    throw unsupported("Operação entre tipos incompatíveis no backend.");
                }
                left = coerce(ir, left, Operand.Type.I64);
                right = coerce(ir, right, Operand.Type.I64);
                operationType = Operand.Type.I64;
            } else if (left.type() == right.type()
                    && (left.type() == Operand.Type.I32 || left.type() == Operand.Type.BOOL
                            || left.type() == Operand.Type.REF)) {
                operationType = left.type();
            } else {
                throw unsupported("Operação entre tipos ainda não suportada pelo backend.");
            }
            if (!comparison && !isNumeric(operationType)) {
                throw unsupported("A aritmética do backend só suporta int, long e double.");
            }
            if ((opcode == IROpCode.LT || opcode == IROpCode.GT
                    || opcode == IROpCode.LE || opcode == IROpCode.GE)
                    && !isNumeric(operationType)) {
                throw unsupported("Comparações relacionais do backend só suportam números.");
            }
            if (opcode == IROpCode.MOD && operationType == Operand.Type.F64) {
                throw unsupported("mod não suporta double no backend.");
            }
            if ((opcode == IROpCode.EQ || opcode == IROpCode.NE)
                    && operationType == Operand.Type.BOOL) {

            } else if ((opcode == IROpCode.EQ || opcode == IROpCode.NE)
                    && operationType == Operand.Type.REF) {

            } else if (comparison && !isNumeric(operationType)
                    && operationType != Operand.Type.BOOL && operationType != Operand.Type.REF) {
                throw unsupported("Comparação ainda não suportada pelo backend.");
            }
            Operand.Type resultType = comparison ? Operand.Type.BOOL : operationType;
            Operand result = Operand.temporary("t" + nextTemp++, resultType);
            ir.add(new IRInstruction(opcode, result, left, right));
            return result;
        }
        if (expression instanceof NewObjectRecord creation) {
            String className = creation.type().name();
            if (!classes.containsKey(className)) {
                throw unsupported("Classe não encontrada: " + className + ".");
            }

            ClassLayout layout = classLayouts.get(className);
            if (layout == null) {
                throw unsupported("Layout não encontrado para a classe '" + className + "'.");
            }

            List<Operand> arguments = new ArrayList<>();
            for (ExpressionInterface argument : creation.arguments()) {
                arguments.add(expression(ir, argument));
            }
            ConstructorRecord constructor = selectConstructor(className, arguments);
            List<Operand> convertedArguments = convertConstructorArguments(ir, constructor, arguments);

            Operand result = Operand.temporary("t" + nextTemp++, Operand.Type.REF);
            int allocationSize = Math.max(1, layout.size());

            ir.add(new IRInstruction(
                    IROpCode.ALLOC_OBJECT,
                    result,
                    Operand.constant(allocationSize, Operand.Type.I32),
                    Operand.constant(className, Operand.Type.REF)));

            referenceTypesByOperand.put(result, className);
            sourceTypesByOperand.put(result, className);
            List<Operand> constructorArguments = new ArrayList<>(convertedArguments.size() + 1);
            constructorArguments.add(result);
            constructorArguments.addAll(convertedArguments);
            ir.add(new IRInstruction(
                    IROpCode.CALL,
                    null,
                    Operand.label(constructorLabel(classes.get(className), constructor)),
                    null,
                    constructorArguments));
            emitExceptionCheck(ir);
            return result;
        }
        if (expression instanceof CallRecord call)
            return emitCall(ir, call, true);
        throw unsupported("Expressão ainda não suportada: " + expression.getClass().getSimpleName() + ".");
    }

    private Operand emitArrayCreation(IRProgram ir, ArrayCreationRecord creation) {
        String arrayType = creation.type().name();
        int dimensions = arrayDimensions(arrayType);
        if (dimensions < 1 || dimensions > 2) {
            throw unsupported("O backend suporta arrays de até duas dimensões.");
        }
        if (creation.initializer() instanceof ArrayInitializerRecord initializer) {
            return emitArrayInitializer(ir, arrayType, initializer);
        }
        if (dimensions == 2 && creation.dimensions().size() == 2) {
            String elementType = componentTypeName(componentTypeName(arrayType));
            if (!elementType.equals("int")) {
                throw unsupported("A criação retangular de arrays multidimensionais suporta int[][] por enquanto.");
            }
            Operand rows = expression(ir, creation.dimensions().get(0));
            Operand columns = expression(ir, creation.dimensions().get(1));
            ir.add(new IRInstruction(IROpCode.CHECK_ARRAY_SIZE, null, rows, null));
            ir.add(new IRInstruction(IROpCode.CHECK_ARRAY_SIZE, null, columns, null));
            return allocateIntArray2D(ir, arrayType, rows, columns);
        }
        if (creation.dimensions().size() != 1) {
            throw unsupported("A criação do array precisa informar exatamente uma dimensão.");
        }
        Operand length = expression(ir, creation.dimensions().get(0));
        return allocateArray(ir, arrayType, length);
    }

    private Operand emitArrayInitializer(
            IRProgram ir,
            String arrayType,
            ArrayInitializerRecord initializer) {
        int dimensions = arrayDimensions(arrayType);
        if (dimensions < 1 || dimensions > 2) {
            throw unsupported("O backend suporta arrays de até duas dimensões.");
        }
        if (dimensions == 2) {
            String rowType = componentTypeName(arrayType);
            Operand array = allocateArray(
                    ir,
                    arrayType,
                    Operand.constant(initializer.elements().size(), Operand.Type.I32));
            for (int index = 0; index < initializer.elements().size(); index++) {
                ExpressionInterface element = initializer.elements().get(index);
                Operand row;
                if (element instanceof ArrayInitializerRecord nested) {
                    row = emitArrayInitializer(ir, rowType, nested);
                } else {
                    row = coerce(ir, expression(ir, element), Operand.Type.REF);
                }
                ir.add(new IRInstruction(
                        IROpCode.STORE_ARRAY,
                        null,
                        array,
                        Operand.constant(index, Operand.Type.I32),
                        List.of(row, Operand.constant(referenceTypeName(row), Operand.Type.REF))));
            }
            return array;
        }
        String elementType = componentTypeName(arrayType);
        Operand array = allocateArray(
                ir,
                arrayType,
                Operand.constant(initializer.elements().size(), Operand.Type.I32));
        Operand.Type expectedType = typeOf(new TypeRecord(elementType));
        for (int index = 0; index < initializer.elements().size(); index++) {
            ExpressionInterface element = initializer.elements().get(index);
            if (element instanceof ArrayInitializerRecord) {
                throw unsupported("Inicializadores de arrays multidimensionais ainda não são suportados.");
            }
            Operand value = coerce(ir, expression(ir, element), expectedType);
            ir.add(new IRInstruction(
                    IROpCode.STORE_ARRAY,
                    null,
                    array,
                    Operand.constant(index, Operand.Type.I32),
                    List.of(value, Operand.constant(referenceTypeName(value), Operand.Type.REF))));
        }
        return array;
    }

    private Operand allocateIntArray2D(
            IRProgram ir,
            String arrayType,
            Operand rows,
            Operand columns) {
        Operand result = allocateArray(ir, arrayType, rows);
        String testLabel = newLabel();
        String endLabel = newLabel();
        Operand index = Operand.temporary("t" + nextTemp++, Operand.Type.I32);
        ir.add(new IRInstruction(
                IROpCode.ASSIGN,
                index,
                Operand.constant(0, Operand.Type.I32),
                null));
        ir.add(new IRInstruction(IROpCode.LABEL, Operand.label(testLabel), null, null));
        Operand condition = Operand.temporary("t" + nextTemp++, Operand.Type.BOOL);
        ir.add(new IRInstruction(IROpCode.LT, condition, index, rows));
        ir.add(new IRInstruction(
                IROpCode.JUMP_IF_FALSE,
                Operand.label(endLabel),
                condition,
                null));

        Operand row = allocateArray(ir, componentTypeName(arrayType), columns);
        ir.add(new IRInstruction(
                IROpCode.STORE_ARRAY,
                null,
                result,
                index,
                List.of(row, Operand.constant(referenceTypeName(row), Operand.Type.REF))));

        Operand nextIndex = Operand.temporary("t" + nextTemp++, Operand.Type.I32);
        ir.add(new IRInstruction(
                IROpCode.ADD,
                nextIndex,
                index,
                Operand.constant(1, Operand.Type.I32)));
        ir.add(new IRInstruction(IROpCode.ASSIGN, index, nextIndex, null));
        ir.add(new IRInstruction(IROpCode.JUMP, Operand.label(testLabel), null, null));
        ir.add(new IRInstruction(IROpCode.LABEL, Operand.label(endLabel), null, null));
        return result;
    }

    private Operand allocateArray(IRProgram ir, String arrayType, Operand length) {
        String elementType = componentTypeName(arrayType);
        Operand.Type valueType = typeOf(new TypeRecord(elementType));
        int elementSize = switch (valueType) {
            case BOOL -> 1;
            case I32 -> 4;
            case I64, F64, REF -> 8;
        };
        Operand result = Operand.temporary("t" + nextTemp++, Operand.Type.REF);
        ir.add(new IRInstruction(
                IROpCode.ALLOC_ARRAY,
                result,
                length,
                Operand.constant(elementSize, Operand.Type.I32),
                List.of(
                        Operand.constant(arrayType, Operand.Type.REF),
                        Operand.constant(elementType, Operand.Type.REF))));
        sourceTypesByOperand.put(result, arrayType);
        return result;
    }

    private String referenceTypeName(Operand operand) {
        if (operand == null || operand.type() != Operand.Type.REF) return "";
        if (operand.kind() == Operand.Kind.CONSTANT) {
            return operand.value() == null ? "null"
                    : operand.value() instanceof String ? "String" : "Object";
        }
        return sourceTypesByOperand.getOrDefault(operand, "Object");
    }

    private String componentTypeName(String arrayType) {
        if (arrayType == null || !arrayType.endsWith("[]")) {
            throw unsupported("Era esperado um array, mas o tipo não foi identificado.");
        }
        return arrayType.substring(0, arrayType.length() - 2);
    }

    private int arrayDimensions(String typeName) {
        int dimensions = 0;
        for (int index = 0; index < typeName.length() - 1; index++) {
            if (typeName.charAt(index) == '[' && typeName.charAt(index + 1) == ']') {
                dimensions++;
                index++;
            }
        }
        return dimensions;
    }

    private void initializeDeclaredFields(
            IRProgram ir,
            Operand object,
            ClassRecord classNode) {
        ClassLayout layout = classLayouts.get(classNode.name());
        if (layout == null) {
            throw unsupported("Layout não encontrado para a classe '" + classNode.name() + "'.");
        }

        for (AttributeRecord attribute : classNode.attributes()) {
            if (attribute.isStatic() || attribute.value() == null) {
                continue;
            }

            FieldLayout field = layout.getField(attribute.name());
            if (field == null) {
                throw unsupported(
                        "Campo não encontrado no layout: " + classNode.name()
                                + "." + attribute.name() + ".");
            }

            Operand value = attribute.value() instanceof ArrayInitializerRecord initializer
                    ? emitArrayInitializer(ir, field.type(), initializer)
                    : coerce(
                            ir,
                            expression(ir, attribute.value()),
                            typeOf(new TypeRecord(field.type())));
            ir.add(new IRInstruction(
                    IROpCode.STORE_FIELD,
                    null,
                    object,
                    Operand.constant(field.offSet(), Operand.Type.I32),
                    List.of(value)));
        }
    }

    private ResolvedField resolveField(IRProgram ir, MemberAccessRecord member) {
        Operand receiver = expression(ir, member.receiver());
        return resolveField(ir, member, receiver);
    }

    private ClassRecord resolveStaticClassReceiver(ExpressionInterface receiver) {
        if (!(receiver instanceof VariableRecord variable)
                || resolveVariable(variable.name()) != null) {
            return null;
        }
        List<ClassRecord> matches = classes.values().stream()
                .filter(classNode -> simpleName(classNode.name()).equals(variable.name()))
                .toList();
        if (matches.size() > 1) {
            throw unsupported("Nome de classe ambíguo em acesso static: " + variable.name() + ".");
        }
        return matches.isEmpty() ? null : matches.get(0);
    }

    private ResolvedStaticField resolveStaticField(String className, String fieldName) {
        ClassRecord current = classes.get(className);
        Set<String> visited = new java.util.HashSet<>();
        while (current != null && visited.add(current.name())) {
            for (AttributeRecord attribute : current.attributes()) {
                if (attribute.name().equals(fieldName)) {
                    return attribute.isStatic()
                            ? new ResolvedStaticField(
                                    StaticFieldSupport.label(current.name(), attribute.name()), attribute)
                            : null;
                }
            }
            current = current.superClass() == null ? null : classes.get(current.superClass());
        }
        return null;
    }

    private Operand loadStaticField(IRProgram ir, ResolvedStaticField field) {
        Operand destination = Operand.temporary(
                "t" + nextTemp++, typeOf(field.attribute().type()));
        ir.add(new IRInstruction(
                IROpCode.LOAD_STATIC, destination, Operand.label(field.label()), null));
        registerReferenceType(destination, field.attribute().type());
        return destination;
    }

    private ResolvedField resolveField(
            IRProgram ir,
            MemberAccessRecord member,
            Operand receiver) {
        String receiverClass = referenceTypesByOperand.get(receiver);
        if (receiverClass == null) {
            throw unsupported(
                    "Não foi possível determinar a classe do objeto usado em '"
                            + member.name() + "'.");
        }

        return resolveField(receiverClass, member.name(), receiver);
    }

    private ResolvedField resolveField(String receiverClass, String memberName, Operand receiver) {
        ClassLayout layout = classLayouts.get(receiverClass);
        if (layout == null) {
            throw unsupported("Layout não encontrado para a classe '" + receiverClass + "'.");
        }

        FieldLayout field = layout.getField(memberName);
        if (field == null) {
            throw unsupported(
                    "Campo '" + memberName + "' não encontrado em '" + receiverClass + "'.");
        }
        return new ResolvedField(receiver, field);
    }

    private Operand loadField(IRProgram ir, ResolvedField resolved) {
        FieldLayout field = resolved.field();
        Operand result = Operand.temporary("t" + nextTemp++, typeOf(new TypeRecord(field.type())));
        ir.add(new IRInstruction(
                IROpCode.LOAD_FIELD,
                result,
                resolved.receiver(),
                Operand.constant(field.offSet(), Operand.Type.I32)));
        registerReferenceType(result, new TypeRecord(field.type()));
        return result;
    }

    private boolean isStringExpression(ExpressionInterface expression, Operand value) {
        if (expression instanceof LiteralRecord literal) {
            return literal.value() instanceof String;
        }
        return "String".equals(sourceTypesByOperand.get(value));
    }

    private Operand emitCall(IRProgram ir, CallRecord call, boolean keepResult) {
        List<Operand> arguments = new ArrayList<>();
        for (ExpressionInterface argument : call.arguments())
            arguments.add(expression(ir, argument));

        String targetLabel;
        try {
            targetLabel = resolveCallTarget(call, arguments);
        } catch (UnresolvedCallException exception) {
            if (currentFunction == null || currentFunction.isStatic()) {
                throw exception;
            }
            if (interfaces.containsKey(currentClassName)) {
                return emitInterfaceInstanceCall(
                        ir, resolveVariable("this"), currentClassName, call.name(), arguments, keepResult);
            }
            ResolvedMethod instanceMethod = resolveInstanceMethod(
                    currentClassName,
                    call.name(),
                    arguments);
            return emitInstanceCall(
                    ir,
                    resolveVariable("this"),
                    arguments,
                    instanceMethod,
                    keepResult,
                    VirtualMethodTable.isVirtual(instanceMethod.function()));
        }
        FunctionRecord target = functionsByLabel.get(targetLabel);
        if (target == null)
            throw unsupported("Não foi possível resolver a função chamada '" + call.name() + "'.");
        if (arguments.size() != target.parameters().size()) {
            throw unsupported("Quantidade de argumentos incompatível para '" + call.name() + "'.");
        }
        for (int index = 0; index < arguments.size(); index++) {
            arguments.set(index, coerce(ir, arguments.get(index), typeOf(target.parameters().get(index).type())));
        }

        String returnName = target.returnType().name();
        boolean returnsVoid = returnName.equals("void");
        if (keepResult && returnsVoid) {
            throw unsupported("Uma função void não pode ser usada como expressão.");
        }
        Operand destination = keepResult
                ? Operand.temporary("t" + nextTemp++, typeOf(target.returnType()))
                : null;
        ir.add(new IRInstruction(IROpCode.CALL, destination, Operand.label(targetLabel), null, arguments));
        emitExceptionCheck(ir);
        if (destination != null) {
            registerReferenceType(destination, target.returnType());
        }
        return destination;
    }

    private Operand emitMemberCall(IRProgram ir, MemberCallRecord call, boolean keepResult) {
        ClassRecord staticOwner = resolveStaticClassReceiver(call);
        if (staticOwner != null) {
            List<Operand> arguments = new ArrayList<>();
            for (ExpressionInterface argument : call.arguments()) {
                arguments.add(expression(ir, argument));
            }
            FunctionRecord target = resolveStaticMethod(staticOwner, call.name(), arguments);
            String targetLabel = functionLabel(staticOwner, target);
            if (!functionsByLabel.containsKey(targetLabel)) {
                throw unsupported("Método static não foi registrado na IR: " + targetLabel + ".");
            }
            if (arguments.size() != target.parameters().size()) {
                throw unsupported("Quantidade de argumentos incompatível para '" + call.name() + "'.");
            }
            List<Operand> convertedArguments = new ArrayList<>();
            for (int index = 0; index < arguments.size(); index++) {
                convertedArguments.add(coerce(
                        ir,
                        arguments.get(index),
                        typeOf(target.parameters().get(index).type())));
            }
            if (keepResult && target.returnType().name().equals("void")) {
                throw unsupported("Uma função void não pode ser usada como expressão.");
            }
            Operand destination = keepResult
                    ? Operand.temporary("t" + nextTemp++, typeOf(target.returnType()))
                    : null;
            ir.add(new IRInstruction(
                    IROpCode.CALL,
                    destination,
                    Operand.label(targetLabel),
                    null,
                    convertedArguments));
            emitExceptionCheck(ir);
            if (destination != null) {
                registerReferenceType(destination, target.returnType());
            }
            return destination;
        }

        InterfaceRecord staticInterface = resolveStaticInterfaceReceiver(call);
        if (staticInterface != null) {
            List<Operand> arguments = new ArrayList<>();
            for (ExpressionInterface argument : call.arguments()) {
                arguments.add(expression(ir, argument));
            }
            FunctionRecord target = resolveStaticInterfaceMethod(
                    staticInterface, call.name(), arguments);
            String targetLabel = functionLabel(staticInterface.name(), target);
            if (!functionsByLabel.containsKey(targetLabel)) {
                throw unsupported("Método static de interface não foi registrado na IR: "
                        + targetLabel + ".");
            }
            return emitDirectCall(ir, targetLabel, target, arguments, keepResult);
        }

        Operand receiver = expression(ir, call.receiver());
        String receiverClass = sourceTypesByOperand.get(receiver);
        if (receiverClass == null) {
            receiverClass = referenceTypesByOperand.get(receiver);
        }
        if (receiverClass != null && interfaces.containsKey(receiverClass)) {
            List<Operand> arguments = new ArrayList<>();
            for (ExpressionInterface argument : call.arguments()) {
                arguments.add(expression(ir, argument));
            }
            FunctionRecord interfaceMethod = resolveInterfaceMethod(
                    receiverClass,
                    call.name(),
                    arguments);
            return emitInstanceCall(
                    ir,
                    receiver,
                    arguments,
                    new ResolvedMethod(null, interfaceMethod),
                    keepResult,
                    true);
        }
        if (isThrowableType(receiverClass)
                && call.arguments().isEmpty()
                && (call.name().equals("getMessage") || call.name().equals("getType"))) {
            if (!keepResult) {
                throw unsupported("O resultado de '" + call.name() + "' não pode ser descartado neste contexto.");
            }
            Operand result = Operand.temporary("t" + nextTemp++, Operand.Type.REF);
            if (call.name().equals("getMessage")) {
                ClassLayout layout = classLayouts.get(receiverClass);
                FieldLayout messageField = layout == null ? null : layout.getField("message");
                int messageOffset = messageField != null && messageField.type().equals("String")
                        ? messageField.offSet() : -1;
                ir.add(new IRInstruction(IROpCode.EXCEPTION_GET_MESSAGE,
                        result, receiver, Operand.constant(messageOffset, Operand.Type.I32)));
            } else {
                ir.add(new IRInstruction(IROpCode.EXCEPTION_GET_TYPE,
                        result, receiver, null));
            }
            registerReferenceType(result, new TypeRecord("String"));
            return result;
        }
        if (receiverClass == null || !classes.containsKey(receiverClass)) {
            throw unsupported("Não foi possível determinar a classe do receptor de '"
                    + call.name() + "'.");
        }
        List<Operand> arguments = new ArrayList<>();
        for (ExpressionInterface argument : call.arguments()) {
            arguments.add(expression(ir, argument));
        }
        ResolvedMethod method = resolveInstanceMethod(receiverClass, call.name(), arguments);
        return emitInstanceCall(ir, receiver, arguments, method, keepResult,
                VirtualMethodTable.isVirtual(method.function()));
    }

    private boolean isThrowableType(String className) {
        if (className == null) return false;
        if (BuiltinExceptionTypes.isBuiltin(className)) return true;
        java.util.Set<String> visited = new java.util.HashSet<>();
        String current = className;
        while (current != null && visited.add(current)) {
            if (BuiltinExceptionTypes.simpleName(current).equals("Throwable")) return true;
            if (BuiltinExceptionTypes.isSubtype(current, "Throwable")) return true;
            ClassRecord classNode = classes.get(current);
            current = classNode == null ? null : classNode.superClass();
        }
        return false;
    }

    private ClassRecord resolveStaticClassReceiver(MemberCallRecord call) {
        if (!(call.receiver() instanceof VariableRecord variable)
                || resolveVariable(variable.name()) != null) {
            return null;
        }
        List<ClassRecord> matches = classes.values().stream()
                .filter(classNode -> simpleName(classNode.name()).equals(variable.name()))
                .toList();
        if (matches.size() > 1) {
            throw unsupported("Nome de classe ambíguo na chamada static: " + variable.name() + ".");
        }
        return matches.isEmpty() ? null : matches.get(0);
    }

    private InterfaceRecord resolveStaticInterfaceReceiver(MemberCallRecord call) {
        if (!(call.receiver() instanceof VariableRecord variable)
                || resolveVariable(variable.name()) != null
                || resolveStaticClassReceiver(call) != null) {
            return null;
        }
        List<InterfaceRecord> matches = interfaces.values().stream()
                .filter(interfaceNode -> simpleName(interfaceNode.name()).equals(variable.name()))
                .toList();
        if (matches.size() > 1) {
            throw unsupported("Nome de interface ambíguo na chamada static: "
                    + variable.name() + ".");
        }
        return matches.isEmpty() ? null : matches.get(0);
    }

    private FunctionRecord resolveStaticMethod(
            ClassRecord owner,
            String methodName,
            List<Operand> arguments) {
        FunctionRecord best = null;
        int bestScore = Integer.MAX_VALUE;
        boolean ambiguous = false;
        Set<String> visited = new java.util.HashSet<>();
        while (owner != null && visited.add(owner.name())) {
            for (FunctionRecord function : owner.functions()) {
                if (!function.isStatic() || !function.name().equals(methodName)
                        || function.parameters().size() != arguments.size()) {
                    continue;
                }
                int score = 0;
                boolean applicable = true;
                for (int index = 0; index < arguments.size(); index++) {
                    Operand.Type actual = arguments.get(index).type();
                    Operand.Type expected = typeOf(function.parameters().get(index).type());
                    if (actual == expected) continue;
                    if (actual == Operand.Type.I32 && expected == Operand.Type.I64) score++;
                    else if (isInteger(actual) && expected == Operand.Type.F64) score += actual == Operand.Type.I32 ? 2 : 1;
                    else {
                        applicable = false;
                        break;
                    }
                }
                if (!applicable) continue;
                if (score < bestScore) {
                    best = function;
                    bestScore = score;
                    ambiguous = false;
                } else if (score == bestScore) {
                    ambiguous = true;
                }
            }
            owner = owner.superClass() == null ? null : classes.get(owner.superClass());
        }
        if (ambiguous) throw unsupported("Chamada static ambígua para '" + methodName + "'.");
        if (best == null) throw unsupported("Método static não encontrado: " + methodName + ".");
        return best;
    }

    private FunctionRecord resolveStaticInterfaceMethod(
            InterfaceRecord owner,
            String methodName,
            List<Operand> arguments) {
        FunctionRecord best = null;
        int bestScore = Integer.MAX_VALUE;
        boolean ambiguous = false;
        for (FunctionRecord function : owner.functions()) {
            if (!function.isStatic() || !function.name().equals(methodName)
                    || function.parameters().size() != arguments.size()) {
                continue;
            }
            int score = 0;
            boolean applicable = true;
            for (int index = 0; index < arguments.size(); index++) {
                Operand.Type actual = arguments.get(index).type();
                Operand.Type expected = typeOf(function.parameters().get(index).type());
                if (actual == expected) continue;
                if (actual == Operand.Type.I32 && expected == Operand.Type.I64) score++;
                else if (isInteger(actual) && expected == Operand.Type.F64) {
                    score += actual == Operand.Type.I32 ? 2 : 1;
                } else {
                    applicable = false;
                    break;
                }
            }
            if (!applicable) continue;
            if (score < bestScore) {
                best = function;
                bestScore = score;
                ambiguous = false;
            } else if (score == bestScore) {
                ambiguous = true;
            }
        }
        if (ambiguous) {
            throw unsupported("Chamada static ambígua para '" + methodName + "' na interface '"
                    + owner.name() + "'.");
        }
        if (best == null) {
            throw unsupported("Método static não encontrado: " + owner.name() + "." + methodName + ".");
        }
        return best;
    }

    private Operand emitDirectCall(
            IRProgram ir,
            String targetLabel,
            FunctionRecord target,
            List<Operand> arguments,
            boolean keepResult) {
        List<Operand> convertedArguments = new ArrayList<>();
        for (int index = 0; index < arguments.size(); index++) {
            convertedArguments.add(coerce(
                    ir,
                    arguments.get(index),
                    typeOf(target.parameters().get(index).type())));
        }
        boolean returnsVoid = target.returnType().name().equals("void");
        if (keepResult && returnsVoid) {
            throw unsupported("Uma função void não pode ser usada como expressão.");
        }
        Operand destination = keepResult
                ? Operand.temporary("t" + nextTemp++, typeOf(target.returnType()))
                : null;
        ir.add(new IRInstruction(
                IROpCode.CALL, destination, Operand.label(targetLabel), null, convertedArguments));
        emitExceptionCheck(ir);
        if (destination != null) registerReferenceType(destination, target.returnType());
        return destination;
    }

    private String simpleName(String qualifiedName) {
        int separator = qualifiedName.lastIndexOf('.');
        return separator < 0 ? qualifiedName : qualifiedName.substring(separator + 1);
    }

    private FunctionRecord resolveInterfaceMethod(
            String interfaceName,
            String methodName,
            List<Operand> arguments) {
        List<FunctionRecord> candidates = new ArrayList<>();
        Set<String> visitedInterfaces = new java.util.HashSet<>();
        Set<String> seenSignatures = new java.util.HashSet<>();
        collectInterfaceMethods(interfaceName, methodName, visitedInterfaces, seenSignatures, candidates);

        FunctionRecord best = null;
        int bestScore = Integer.MAX_VALUE;
        boolean ambiguous = false;
        for (FunctionRecord function : candidates) {
            if (function.parameters().size() != arguments.size()) {
                continue;
            }
            int score = 0;
            boolean applicable = true;
            for (int index = 0; index < arguments.size(); index++) {
                Operand.Type actual = arguments.get(index).type();
                Operand.Type expected = typeOf(function.parameters().get(index).type());
                if (actual == expected) continue;
                if (actual == Operand.Type.I32 && expected == Operand.Type.I64) score++;
                else if (isInteger(actual) && expected == Operand.Type.F64) {
                    score += actual == Operand.Type.I32 ? 2 : 1;
                } else {
                    applicable = false;
                    break;
                }
            }
            if (!applicable) continue;
            if (score < bestScore) {
                best = function;
                bestScore = score;
                ambiguous = false;
            } else if (score == bestScore) {
                ambiguous = true;
            }
        }
        if (ambiguous) {
            throw unsupported("Chamada ambígua do método de interface '" + methodName + "'.");
        }
        if (best == null) {
            throw unsupported("Método '" + methodName + "' não encontrado na interface '"
                    + interfaceName + "'.");
        }
        return best;
    }

    private Operand emitInterfaceInstanceCall(
            IRProgram ir,
            Operand receiver,
            String interfaceName,
            String methodName,
            List<Operand> arguments,
            boolean keepResult) {
        FunctionRecord method = resolveInterfaceMethod(interfaceName, methodName, arguments);
        return emitInstanceCall(
                ir,
                receiver,
                arguments,
                new ResolvedMethod(null, method),
                keepResult,
                true);
    }

    private void collectInterfaceMethods(
            String interfaceName,
            String methodName,
            Set<String> visitedInterfaces,
            Set<String> seenSignatures,
            List<FunctionRecord> candidates) {
        if (!visitedInterfaces.add(interfaceName)) {
            return;
        }
        InterfaceRecord interfaceNode = interfaces.get(interfaceName);
        if (interfaceNode == null) {
            throw unsupported("Interface não encontrada na geração de IR: " + interfaceName + ".");
        }
        for (FunctionRecord function : interfaceNode.functions()) {
            String signature = VirtualMethodTable.signature(function);
            if (!function.isStatic() && function.name().equals(methodName)
                    && seenSignatures.add(signature)) {
                candidates.add(function);
            }
        }
        for (String parent : interfaceNode.superInterfaces()) {
            collectInterfaceMethods(parent, methodName, visitedInterfaces, seenSignatures, candidates);
        }
    }

    private Operand emitThisCall(IRProgram ir, ThisCallRecord call, boolean keepResult) {
        Operand receiver = resolveVariable("this");
        if (receiver == null) {
            throw unsupported("this só pode ser usado em um método de instância.");
        }
        List<Operand> arguments = new ArrayList<>();
        for (ExpressionInterface argument : call.arguments()) {
            arguments.add(expression(ir, argument));
        }
        if (interfaces.containsKey(currentClassName)) {
            return emitInterfaceInstanceCall(
                    ir, receiver, currentClassName, call.name(), arguments, keepResult);
        }
        ResolvedMethod method = resolveInstanceMethod(currentClassName, call.name(), arguments);
        return emitInstanceCall(ir, receiver, arguments, method, keepResult,
                VirtualMethodTable.isVirtual(method.function()));
    }

    private Operand emitSuperCall(IRProgram ir, SuperCallRecord call, boolean keepResult) {
        ClassRecord current = classes.get(currentClassName);
        String parentName = current == null ? null : current.superClass();
        Operand receiver = resolveVariable("this");
        if (parentName == null || receiver == null) {
            throw unsupported("super só pode chamar um método pai dentro de método de instância.");
        }
        List<Operand> arguments = new ArrayList<>();
        for (ExpressionInterface argument : call.arguments()) {
            arguments.add(expression(ir, argument));
        }
        ResolvedMethod method = resolveInstanceMethod(parentName, call.name(), arguments);
        return emitInstanceCall(ir, receiver, arguments, method, keepResult, false);
    }

    private Operand emitInstanceCall(
            IRProgram ir,
            Operand receiver,
            List<Operand> arguments,
            ResolvedMethod method,
            boolean keepResult,
            boolean virtual) {
        FunctionRecord function = method.function();
        if (arguments.size() != function.parameters().size()) {
            throw unsupported("Quantidade de argumentos incompatível para '" + function.name() + "'.");
        }
        List<Operand> convertedArguments = new ArrayList<>();
        for (int index = 0; index < arguments.size(); index++) {
            convertedArguments.add(coerce(
                    ir,
                    arguments.get(index),
                    typeOf(function.parameters().get(index).type())));
        }
        boolean returnsVoid = function.returnType().name().equals("void");
        if (keepResult && returnsVoid) {
            throw unsupported("Uma função void não pode ser usada como expressão.");
        }
        Operand destination = keepResult
                ? Operand.temporary("t" + nextTemp++, typeOf(function.returnType()))
                : null;
        if (virtual) {
            ir.add(new IRInstruction(
                    IROpCode.CALL_VIRTUAL,
                    destination,
                    receiver,
                    Operand.constant(virtualMethodTable.slot(function), Operand.Type.I32),
                    convertedArguments));
            emitExceptionCheck(ir);
        } else {
            List<Operand> allArguments = new ArrayList<>(convertedArguments.size() + 1);
            allArguments.add(receiver);
            allArguments.addAll(convertedArguments);
            ir.add(new IRInstruction(
                    IROpCode.CALL,
                    destination,
                    Operand.label(functionLabel(method.owner(), function)),
                    null,
                    allArguments));
            emitExceptionCheck(ir);
        }
        if (destination != null) {
            registerReferenceType(destination, function.returnType());
        }
        return destination;
    }

    private ResolvedMethod resolveInstanceMethod(
            String className,
            String methodName,
            List<Operand> arguments) {
        List<ResolvedMethod> candidates = new ArrayList<>();
        java.util.Set<String> seenSignatures = new java.util.HashSet<>();
        ClassRecord owner = classes.get(className);
        while (owner != null) {
            for (FunctionRecord function : owner.functions()) {
                if (!function.name().equals(methodName) || function.isStatic()) {
                    continue;
                }
                String signature = VirtualMethodTable.signature(function);
                if (seenSignatures.add(signature)) {
                    candidates.add(new ResolvedMethod(owner, function));
                }
            }
            owner = owner.superClass() == null ? null : classes.get(owner.superClass());
        }

        ResolvedMethod best = null;
        int bestScore = Integer.MAX_VALUE;
        boolean ambiguous = false;
        for (ResolvedMethod candidate : candidates) {
            FunctionRecord function = candidate.function();
            if (function.parameters().size() != arguments.size()) {
                continue;
            }
            int score = 0;
            boolean applicable = true;
            for (int index = 0; index < arguments.size(); index++) {
                Operand.Type actual = arguments.get(index).type();
                Operand.Type expected = typeOf(function.parameters().get(index).type());
                if (actual == expected) {
                    continue;
                }
                if (actual == Operand.Type.I32 && expected == Operand.Type.I64) {
                    score++;
                } else if (isInteger(actual) && expected == Operand.Type.F64) {
                    score += actual == Operand.Type.I32 ? 2 : 1;
                } else {
                    applicable = false;
                    break;
                }
            }
            if (!applicable) {
                continue;
            }
            if (score < bestScore) {
                best = candidate;
                bestScore = score;
                ambiguous = false;
            } else if (score == bestScore) {
                ambiguous = true;
            }
        }
        if (ambiguous) {
            throw unsupported("Chamada ambígua do método '" + methodName + "'.");
        }
        if (best == null) {
            throw unsupported("Método de instância não encontrado: " + className + "." + methodName + ".");
        }
        return best;
    }

    private String resolveCallTarget(CallRecord call, List<Operand> arguments) {
        List<String> candidateLabels = new ArrayList<>();
        for (String imported : call.staticImports()) {
            if (!imported.startsWith("static:"))
                continue;
            String target = imported.substring("static:".length());
            boolean onDemand = target.endsWith(".*");
            String ownerName = onDemand ? target.substring(0, target.length() - 2)
                    : target.substring(0, target.lastIndexOf('.'));
            String importedMember = onDemand ? call.name() : target.substring(target.lastIndexOf('.') + 1);
            if (!importedMember.equals(call.name()))
                continue;
            ClassRecord owner = classes.get(ownerName);
            if (owner != null)
                addMethods(candidateLabels, owner, call.name());
            InterfaceRecord interfaceOwner = interfaces.get(ownerName);
            if (interfaceOwner != null)
                addInterfaceStaticMethods(candidateLabels, interfaceOwner, call.name());
        }
        if (candidateLabels.isEmpty()) {
            ClassRecord owner = classes.get(currentClassName);
            if (owner != null) {
                while (owner != null) {
                    addMethods(candidateLabels, owner, call.name());
                    owner = owner.superClass() == null ? null : classes.get(owner.superClass());
                }
            } else {
                InterfaceRecord interfaceOwner = interfaces.get(currentClassName);
                if (interfaceOwner != null) {
                    addInterfaceStaticMethods(candidateLabels, interfaceOwner, call.name());
                }
            }
        }

        String best = null;
        int bestScore = Integer.MAX_VALUE;
        boolean ambiguous = false;
        for (String label : candidateLabels.stream().distinct().toList()) {
            FunctionRecord function = functionsByLabel.get(label);
            if (function == null || function.parameters().size() != arguments.size())
                continue;
            int score = 0;
            boolean applicable = true;
            for (int index = 0; index < arguments.size(); index++) {
                Operand.Type actual = arguments.get(index).type();
                Operand.Type expected = typeOf(function.parameters().get(index).type());
                if (actual == expected)
                    continue;
                if (actual == Operand.Type.I32 && expected == Operand.Type.I64) {
                    score++;
                    continue;
                }
                if (isInteger(actual) && expected == Operand.Type.F64) {
                    score += actual == Operand.Type.I32 ? 2 : 1;
                    continue;
                }
                applicable = false;
                break;
            }
            if (!applicable)
                continue;
            if (score < bestScore) {
                best = label;
                bestScore = score;
                ambiguous = false;
            } else if (score == bestScore) {
                ambiguous = true;
            }
        }
        if (ambiguous)
            throw unsupported("Chamada ambígua de '" + call.name() + "' na geração de IR.");
        if (best == null)
            throw new UnresolvedCallException(
                    "Nenhuma sobrecarga suportada corresponde à chamada '" + call.name() + "'.");
        return best;
    }

    private void addMethods(List<String> labels, ClassRecord owner, String methodName) {
        for (FunctionRecord function : owner.functions()) {
            if (function.name().equals(methodName) && function.isStatic() && !function.isAbstract()) {
                labels.add(functionLabel(owner, function));
            }
        }
    }

    private void addInterfaceStaticMethods(
            List<String> labels,
            InterfaceRecord owner,
            String methodName) {
        for (FunctionRecord function : owner.functions()) {
            if (function.name().equals(methodName) && function.isStatic()) {
                labels.add(functionLabel(owner.name(), function));
            }
        }
    }

    private Operand coerce(IRProgram ir, Operand source, Operand.Type expected) {
        if (source.type() == expected)
            return source;
        if (source.type() == Operand.Type.I32 && expected == Operand.Type.I64) {
            Operand converted = Operand.temporary("t" + nextTemp++, Operand.Type.I64);
            ir.add(new IRInstruction(IROpCode.CAST_I32_TO_I64, converted, source, null));
            return converted;
        }
        if (expected == Operand.Type.F64 && isInteger(source.type())) {
            Operand converted = Operand.temporary("t" + nextTemp++, Operand.Type.F64);
            IROpCode opcode = source.type() == Operand.Type.I32
                    ? IROpCode.CAST_I32_TO_F64
                    : IROpCode.CAST_I64_TO_F64;
            ir.add(new IRInstruction(opcode, converted, source, null));
            return converted;
        }
        throw unsupported("Conversão de " + source.type() + " para " + expected + " não suportada pelo backend.");
    }

    private Operand.Type typeOf(TypeRecord type) {
        String name = type.name();
        return switch (name) {
            case "int" -> Operand.Type.I32;
            case "long" -> Operand.Type.I64;
            case "double" -> Operand.Type.F64;
            case "boolean" -> Operand.Type.BOOL;
            case "void" -> throw unsupported("void não é um tipo de operando.");
            default -> Operand.Type.REF;
        };
    }

    private boolean isInteger(Operand.Type type) {
        return type == Operand.Type.I32 || type == Operand.Type.I64;
    }

    private boolean isNumeric(Operand.Type type) {
        return isInteger(type) || type == Operand.Type.F64;
    }

    private String functionLabel(ClassRecord owner, FunctionRecord function) {
        return functionLabel(owner.name(), function);
    }

    private String functionLabel(String ownerName, FunctionRecord function) {
        List<String> parameters = function.parameters().stream()
                .map(parameter -> mangleType(parameter.type().name()))
                .toList();
        return ownerName + "." + function.name() + "__"
                + (parameters.isEmpty() ? "void" : String.join("__", parameters))
                + "__ret_" + mangleType(function.returnType().name());
    }

    private String mangleType(String type) {
        if (type.equals("String[]"))
            return "String_array";
        if (type.equals("int") || type.equals("long") || type.equals("double")
                || type.equals("boolean") || type.equals("void"))
            return type;
        StringBuilder encoded = new StringBuilder("T");
        type.codePoints().forEach(codePoint -> encoded.append('_')
                .append(Integer.toHexString(codePoint)).append('_'));
        return encoded.toString();
    }

    private String newLabel() {
        return "L" + nextLabel++;
    }

    private UnsupportedBackendFeatureException unsupported(String message) {
        return new UnsupportedBackendFeatureException(message);
    }
}
