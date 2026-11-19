package src.backend.ir;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
import src.ast.records.FreeRecord;
import src.ast.records.FunctionRecord;
import src.ast.records.IfRecord;
import src.ast.records.LiteralRecord;
import src.ast.records.MemberAccessRecord;
import src.ast.records.NewObjectRecord;
import src.ast.records.ParameterRecord;
import src.ast.records.PrintRecord;
import src.ast.records.PrintfRecord;
import src.ast.records.ProgramRecord;
import src.ast.records.ReturnRecord;
import src.ast.records.TypeRecord;
import src.ast.records.UnaryExpressionRecord;
import src.ast.records.VariableRecord;
import src.ast.records.WhileRecord;
import src.backend.memory.ClassLayout;
import src.backend.memory.FieldLayout;
import src.lexer.TokenType;

public final class IRGenerator {
    private record ResolvedField(Operand receiver, FieldLayout field) {
    }

    private final Deque<Map<String, Operand>> variableScopes = new ArrayDeque<>();
    private final Map<String, ClassLayout> classLayouts;
    private final Map<Operand, String> referenceTypesByOperand = new HashMap<>();
    private final Set<Operand> stringVariables = new HashSet<>();
    private final Map<String, ClassRecord> classes = new HashMap<>();
    private final Map<String, FunctionRecord> functionsByLabel = new HashMap<>();
    private final Map<String, ClassRecord> ownersByLabel = new HashMap<>();
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
        ownersByLabel.clear();
        referenceTypesByOperand.clear();

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
                if (!function.isStatic()) {
                    throw unsupported("A geração de código ainda não suporta métodos de instância: "
                            + classNode.name() + "." + function.name() + ".");
                }
                validateFunctionTypes(function);
                String label = functionLabel(classNode, function);
                functionsByLabel.put(label, function);
                ownersByLabel.put(label, classNode);
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
                stringVariables.clear();
                pushVariableScope();
                ir.add(new IRInstruction(IROpCode.LABEL,
                        Operand.label(functionLabel(classNode, function)), null, null));

                for (int index = 0; index < function.parameters().size(); index++) {
                    ParameterRecord parameter = function.parameters().get(index);
                    Operand.Type parameterType = typeOf(parameter.type());
                    Operand parameterOperand = declareVariable(parameter.name(), parameterType);
                    registerReferenceType(parameterOperand, parameter.type());
                    if (parameter.type().name().equals("String")) {
                        stringVariables.add(parameterOperand);
                    }
                    ir.add(new IRInstruction(IROpCode.PARAM,
                            parameterOperand,
                            Operand.constant(index, Operand.Type.I32), null));
                }

                emitStatement(ir, function.block());

                if (function.returnType().name().equals("void")) {
                    ir.add(new IRInstruction(IROpCode.RETURN, null, null, null));
                }
                variableScopes.clear();
                stringVariables.clear();
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
            if (declaration.type().name().equals("String")) {
                stringVariables.add(destination);
            }
            if (declaration.value() != null) {
                Operand source = coerce(ir, expression(ir, declaration.value()), type);
                ir.add(new IRInstruction(IROpCode.ASSIGN,
                        destination, source, null));
            }
        } else if (statement instanceof ReturnRecord returned) {
            Operand value = returned.value() == null ? null : expression(ir, returned.value());
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
                    && !(value.type() == Operand.Type.REF && isStringExpression(print.expression(), ir))) {
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
            if (object.type() != Operand.Type.REF
                    || (!referenceTypesByOperand.containsKey(object) && !nullLiteral)) {
                throw unsupported("free exige uma referência a um objeto de classe.");
            }
            ir.add(new IRInstruction(IROpCode.FREE_OBJECT, null, object, null));
        } else if (statement instanceof AssignmentRecord assignment) {
            if (assignment.target() instanceof VariableRecord variable) {
                Operand destination = expression(ir, variable);
                Operand value = coerce(ir, expression(ir, assignment.value()), destination.type());
                ir.add(new IRInstruction(IROpCode.ASSIGN, destination, value, null));
            } else if (assignment.target() instanceof MemberAccessRecord member) {
                ResolvedField target = resolveField(ir, member);
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
                throw unsupported("Atribuições a campos e arrays ainda não são suportadas pelo backend.");
            }
        } else if (statement instanceof ExpressionStatementRecord expressionStatement) {
            if (expressionStatement.expression() instanceof CallRecord call) {
                emitCall(ir, call, false);
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
                throw unsupported("Variável sem símbolo na geração de IR: " + variable.name() + ".");
            }
            return operand;
        }
        if (expression instanceof MemberAccessRecord member) {
            ResolvedField resolved = resolveField(ir, member);
            FieldLayout field = resolved.field();

            Operand.Type fieldType = typeOf(new TypeRecord(field.type()));
            Operand result = Operand.temporary("t" + nextTemp++, fieldType);

            ir.add(new IRInstruction(
                    IROpCode.LOAD_FIELD,
                    result,
                    resolved.receiver(),
                    Operand.constant(field.offSet(), Operand.Type.I32)));

            registerReferenceType(result, new TypeRecord(field.type()));
            return result;
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
                    null));

            referenceTypesByOperand.put(result, className);
            initializeObjectFields(ir, result, classes.get(className));
            return result;
        }
        if (expression instanceof CallRecord call)
            return emitCall(ir, call, true);
        throw unsupported("Expressão ainda não suportada: " + expression.getClass().getSimpleName() + ".");
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

            Operand value = coerce(
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
        String receiverClass = referenceTypesByOperand.get(receiver);
        if (receiverClass == null) {
            throw unsupported(
                    "Não foi possível determinar a classe do objeto usado em '"
                            + member.name() + "'.");
        }

        ClassLayout layout = classLayouts.get(receiverClass);
        if (layout == null) {
            throw unsupported("Layout não encontrado para a classe '" + receiverClass + "'.");
        }

        FieldLayout field = layout.getField(member.name());
        if (field == null) {
            throw unsupported(
                    "Campo '" + member.name() + "' não encontrado em '" + receiverClass + "'.");
        }
        return new ResolvedField(receiver, field);
    }

    private boolean isStringExpression(ExpressionInterface expression, IRProgram ir) {
        if (expression instanceof LiteralRecord literal) {
            return literal.value() instanceof String;
        }
        if (expression instanceof VariableRecord variable) {
            Operand operand = resolveVariable(variable.name());
            return operand != null && stringVariables.contains(operand);
        }
        if (expression instanceof CallRecord && !ir.instructions().isEmpty()) {
            IRInstruction last = ir.instructions().get(ir.instructions().size() - 1);
            if (last.opCode() == IROpCode.CALL && last.left() != null) {
                FunctionRecord function = functionsByLabel.get(last.left().value().toString());
                return function != null && function.returnType().name().equals("String");
            }
        }
        return false;
    }

    private Operand emitCall(IRProgram ir, CallRecord call, boolean keepResult) {
        List<Operand> arguments = new ArrayList<>();
        for (ExpressionInterface argument : call.arguments())
            arguments.add(expression(ir, argument));

        String targetLabel = resolveCallTarget(call, arguments);
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
        return destination;
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
