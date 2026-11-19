package src.backend.ir;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import src.ast.ExpressionInterface;
import src.ast.StatementInterface;
import src.ast.records.AssignmentRecord;
import src.ast.records.ArrayAccessRecord;
import src.ast.records.ArrayCreationRecord;
import src.ast.records.ArrayInitializerRecord;
import src.ast.records.AttributeRecord;
import src.ast.records.BinaryExpressionRecord;
import src.ast.records.BlockRecord;
import src.ast.records.CallRecord;
import src.ast.records.ClassRecord;
import src.ast.records.DeclarationRecord;
import src.ast.records.ExpressionStatementRecord;
import src.ast.records.ForRecord;
import src.ast.records.FreeRecord;
import src.ast.records.FunctionRecord;
import src.ast.records.IfRecord;
import src.ast.records.LiteralRecord;
import src.ast.records.MemberAccessRecord;
import src.ast.records.MemberCallRecord;
import src.ast.records.NewObjectRecord;
import src.ast.records.ParameterRecord;
import src.ast.records.PrintRecord;
import src.ast.records.PrintfRecord;
import src.ast.records.ProgramRecord;
import src.ast.records.ReturnRecord;
import src.ast.records.TypeRecord;
import src.ast.records.ThisAttributeRecord;
import src.ast.records.ThisCallRecord;
import src.ast.records.ThisRecord;
import src.ast.records.SuperCallRecord;
import src.ast.records.UnaryExpressionRecord;
import src.ast.records.VariableRecord;
import src.ast.records.WhileRecord;
import src.backend.memory.ClassLayout;
import src.backend.memory.FieldLayout;
import src.backend.memory.VirtualMethodTable;
import src.lexer.TokenType;

public final class IRGenerator {
    private record ResolvedField(Operand receiver, FieldLayout field) {
    }

    private record ResolvedMethod(ClassRecord owner, FunctionRecord function) {
    }

    private final Deque<Map<String, Operand>> variableScopes = new ArrayDeque<>();
    private final Map<String, ClassLayout> classLayouts;
    private final Map<Operand, String> referenceTypesByOperand = new HashMap<>();
    private final Map<Operand, String> sourceTypesByOperand = new HashMap<>();
    private final Map<String, ClassRecord> classes = new HashMap<>();
    private final Map<String, FunctionRecord> functionsByLabel = new HashMap<>();
    private VirtualMethodTable virtualMethodTable;
    private int nextTemp;
    private int nextLabel;
    private int nextVariable;
    private String currentClassName;
    private FunctionRecord currentFunction;

    public IRGenerator() {
        this(Map.of());
    }

    public IRGenerator(Map<String, ClassLayout> classLayouts) {
        this.classLayouts = Map.copyOf(classLayouts);
    }

    public IRProgram generate(ProgramRecord ast) {
        nextTemp = 0;
        nextLabel = 0;
        nextVariable = 0;
        classes.clear();
        functionsByLabel.clear();
        virtualMethodTable = new VirtualMethodTable(ast);
        referenceTypesByOperand.clear();
        sourceTypesByOperand.clear();

        if (!ast.interfaces().isEmpty()) {
            throw unsupported("A geração de código ainda não suporta interfaces.");
        }
        if (!ast.registers().isEmpty()) {
            throw unsupported("A geração de código ainda não suporta registradores globais.");
        }

        for (ClassRecord classNode : ast.classes()) {
            classes.put(classNode.name(), classNode);

            if (!classNode.constructors().isEmpty()) {
                throw unsupported("A geração de código ainda não suporta construtores explícitos: "
                        + classNode.name() + ".");
            }
            if (!classNode.interfaces().isEmpty()) {
                throw unsupported("A geração de código ainda não suporta implementação de interfaces: "
                        + classNode.name() + ".");
            }
            for (FunctionRecord function : classNode.functions()) {
                if (function.isAbstract()) {
                    continue;
                }
                validateFunctionTypes(function);
                String label = functionLabel(classNode, function);
                functionsByLabel.put(label, function);
            }
        }

        IRProgram ir = new IRProgram();
        for (ClassRecord classNode : ast.classes()) {
            currentClassName = classNode.name();
            for (FunctionRecord function : classNode.functions()) {
                if (function.isAbstract()) {
                    continue;
                }
                currentFunction = function;
                variableScopes.clear();
                referenceTypesByOperand.clear();
                sourceTypesByOperand.clear();
                pushVariableScope();
                ir.add(new IRInstruction(IROpCode.LABEL,
                        Operand.label(functionLabel(classNode, function)), null, null));

                if (!function.isStatic()) {
                    Operand thisOperand = declareVariable("this", Operand.Type.REF);
                    registerReferenceType(thisOperand, new TypeRecord(classNode.name()));
                    ir.add(new IRInstruction(IROpCode.PARAM,
                            thisOperand,
                            Operand.constant(-1, Operand.Type.I32), null));
                }

                for (int index = 0; index < function.parameters().size(); index++) {
                    ParameterRecord parameter = function.parameters().get(index);
                    Operand.Type parameterType = typeOf(parameter.type());
                    Operand parameterOperand = declareVariable(parameter.name(), parameterType);
                    registerReferenceType(parameterOperand, parameter.type());
                    ir.add(new IRInstruction(IROpCode.PARAM,
                            parameterOperand,
                            Operand.constant(index, Operand.Type.I32), null));
                }

                emitStatement(ir, function.block());

                if (function.returnType().name().equals("void")) {
                    ir.add(new IRInstruction(IROpCode.RETURN, null, null, null));
                }
                variableScopes.clear();
            }
        }
        currentFunction = null;
        currentClassName = null;
        return ir;
    }

    private void validateFunctionTypes(FunctionRecord function) {
        if (!function.returnType().name().equals("void")) {
            typeOf(function.returnType());
        }
        for (ParameterRecord parameter : function.parameters()) {
            typeOf(parameter.type());
        }
    }

    private void emitStatement(IRProgram ir, StatementInterface statement) {
        if (statement == null) {
            return;
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
            ir.add(new IRInstruction(IROpCode.RETURN, null, value, null));
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
            ir.add(new IRInstruction(IROpCode.PRINT, null, value, null));
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
            ir.add(new IRInstruction(IROpCode.PRINT_FORMAT, null,
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
                if (destination == null && currentFunction != null && !currentFunction.isStatic()) {
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
                        List.of(value)));
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

    private void pushVariableScope() {
        variableScopes.push(new HashMap<>());
    }

    private void popVariableScope() {
        if (variableScopes.isEmpty()) {
            throw new IllegalStateException("Pilha de escopos da IR vazia.");
        }
        variableScopes.pop();
    }

    private Operand declareVariable(String sourceName, Operand.Type type) {
        if (variableScopes.isEmpty()) {
            throw new IllegalStateException("Declaração de variável sem escopo ativo na IR.");
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
            if (!creation.arguments().isEmpty()) {
                throw unsupported(
                        "Argumentos de construtor ainda não são suportados pelo backend.");
            }

            String className = creation.type().name();
            if (!classes.containsKey(className)) {
                throw unsupported("Classe não encontrada: " + className + ".");
            }

            ClassLayout layout = classLayouts.get(className);
            if (layout == null) {
                throw unsupported("Layout não encontrado para a classe '" + className + "'.");
            }

            Operand result = Operand.temporary("t" + nextTemp++, Operand.Type.REF);
            int allocationSize = Math.max(1, layout.size());

            ir.add(new IRInstruction(
                    IROpCode.ALLOC_OBJECT,
                    result,
                    Operand.constant(allocationSize, Operand.Type.I32),
                    Operand.constant(className, Operand.Type.REF)));

            referenceTypesByOperand.put(result, className);
            sourceTypesByOperand.put(result, className);
            initializeObjectFields(ir, result, classes.get(className));
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
                        List.of(row)));
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
                    List.of(value)));
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
                List.of(row)));

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
                Operand.constant(elementSize, Operand.Type.I32)));
        sourceTypesByOperand.put(result, arrayType);
        return result;
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

    private void initializeObjectFields(
            IRProgram ir,
            Operand object,
            ClassRecord classNode) {
        String parentName = classNode.superClass();
        if (parentName != null && !parentName.equals("Object")) {
            ClassRecord parent = classes.get(parentName);
            if (parent == null) {
                throw unsupported("Classe pai não encontrada: " + parentName + ".");
            }
            initializeObjectFields(ir, object, parent);
        }

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
        } catch (UnsupportedOperationException exception) {
            if (!exception.getMessage().startsWith("Nenhuma sobrecarga suportada")
                    || currentFunction == null || currentFunction.isStatic()) {
                throw exception;
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
        if (destination != null) {
            registerReferenceType(destination, target.returnType());
        }
        return destination;
    }

    private Operand emitMemberCall(IRProgram ir, MemberCallRecord call, boolean keepResult) {
        Operand receiver = expression(ir, call.receiver());
        String receiverClass = sourceTypesByOperand.get(receiver);
        if (receiverClass == null) {
            receiverClass = referenceTypesByOperand.get(receiver);
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

    private Operand emitThisCall(IRProgram ir, ThisCallRecord call, boolean keepResult) {
        Operand receiver = resolveVariable("this");
        if (receiver == null) {
            throw unsupported("this só pode ser usado em um método de instância.");
        }
        List<Operand> arguments = new ArrayList<>();
        for (ExpressionInterface argument : call.arguments()) {
            arguments.add(expression(ir, argument));
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
        }
        if (candidateLabels.isEmpty()) {
            ClassRecord owner = classes.get(currentClassName);
            while (owner != null) {
                addMethods(candidateLabels, owner, call.name());
                owner = owner.superClass() == null ? null : classes.get(owner.superClass());
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
            throw unsupported("Nenhuma sobrecarga suportada corresponde à chamada '" + call.name() + "'.");
        return best;
    }

    private void addMethods(List<String> labels, ClassRecord owner, String methodName) {
        for (FunctionRecord function : owner.functions()) {
            if (function.name().equals(methodName) && function.isStatic() && !function.isAbstract()) {
                labels.add(functionLabel(owner, function));
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
        List<String> parameters = function.parameters().stream()
                .map(parameter -> mangleType(parameter.type().name()))
                .toList();
        return owner.name() + "." + function.name() + "__"
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

    private UnsupportedOperationException unsupported(String message) {
        return new UnsupportedOperationException(message);
    }
}
