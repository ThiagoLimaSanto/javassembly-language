package src.backend.ir;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import src.ast.ExpressionInterface;
import src.ast.StatementInterface;
import src.ast.records.AssignmentRecord;
import src.ast.records.BinaryExpressionRecord;
import src.ast.records.BlockRecord;
import src.ast.records.CallRecord;
import src.ast.records.ClassRecord;
import src.ast.records.DeclarationRecord;
import src.ast.records.ForRecord;
import src.ast.records.FunctionRecord;
import src.ast.records.IfRecord;
import src.ast.records.LiteralRecord;
import src.ast.records.ParameterRecord;
import src.ast.records.PrintRecord;
import src.ast.records.ProgramRecord;
import src.ast.records.ReturnRecord;
import src.ast.records.TypeRecord;
import src.ast.records.VariableRecord;
import src.ast.records.WhileRecord;

public final class IRGenerator {

    private Map<String, Operand.Type> variableTypes = new HashMap<>();

    private int nextTemp = 0;
    private int nextLabel = 0;
    private String currentClassName;

    public IRProgram generate(ProgramRecord ast) {
        nextTemp = 0;
        nextLabel = 0;
        IRProgram ir = new IRProgram();

        for (ClassRecord classNode : ast.classes()) {
            currentClassName = classNode.name();
            for (FunctionRecord function : classNode.functions()) {
                variableTypes = new HashMap<>();
                ir.add(new IRInstruction(
                        IROpCode.LABEL,
                        Operand.label(classNode.name() + "." + function.name()),
                        null,
                        null));

                for (int i = 0; i < function.parameters().size(); i++) {
                    ParameterRecord parameter = function.parameters().get(i);
                    
                    Operand.Type parameterType = typeOf(parameter.type());
                    variableTypes.put(parameter.name(), parameterType);

                    ir.add(new IRInstruction(
                            IROpCode.PARAM,
                            Operand.variable(parameter.name(), parameterType),
                            Operand.constant(i, Operand.Type.I32),
                            null));
                }

                if (function.block() != null) {
                    for (StatementInterface statement : function.block().commands()) {
                        emitStatement(ir, statement);
                    }
                }
            }
        }

        return ir;
    }

    private void emitStatement(IRProgram ir, StatementInterface statement) {
        if (statement instanceof BlockRecord block) {
            for (StatementInterface child : block.commands()) {
                emitStatement(ir, child);
            }
        } else if (statement instanceof DeclarationRecord declaration) {
            Operand.Type declarationType = typeOf(declaration.type());
            variableTypes.put(declaration.name(), declarationType);

            if (declaration.value() != null) {
                Operand destination = Operand.variable(declaration.name(), declarationType);

                Operand value = expression(ir, declaration.value());

                ir.add(new IRInstruction(
                        IROpCode.ASSIGN,
                        destination,
                        value,
                        null));
            }
        } else if (statement instanceof ReturnRecord returnStatement) {
            Operand value = returnStatement.value() == null ? null : expression(ir, returnStatement.value());

            ir.add(new IRInstruction(
                    IROpCode.RETURN,
                    null,
                    value,
                    null));
        } else if (statement instanceof IfRecord IFStatement) {
            String elseLabel = newLabel();
            String endLabel = newLabel();

            Operand condition = expression(ir, IFStatement.condition());

            ir.add(new IRInstruction(
                    IROpCode.JUMP_IF_FALSE,
                    Operand.label(elseLabel),
                    condition,
                    null));

            emitStatement(ir, IFStatement.thenBranch());

            if (IFStatement.elseBranch() != null) {
                ir.add(new IRInstruction(
                        IROpCode.JUMP,
                        Operand.label(endLabel),
                        null,
                        null));
            }

            ir.add(new IRInstruction(
                    IROpCode.LABEL,
                    Operand.label(elseLabel),
                    null,
                    null));

            if (IFStatement.elseBranch() != null) {
                emitStatement(ir, IFStatement.elseBranch());

                ir.add(new IRInstruction(
                        IROpCode.LABEL,
                        Operand.label(endLabel),
                        null,
                        null));
            }
        } else if (statement instanceof WhileRecord whileStatement) {
            String testLabel = newLabel();
            String endLabel = newLabel();

            ir.add(new IRInstruction(
                    IROpCode.LABEL,
                    Operand.label(testLabel),
                    null,
                    null));

            Operand condition = expression(ir, whileStatement.expression());

            ir.add(new IRInstruction(
                    IROpCode.JUMP_IF_FALSE,
                    Operand.label(endLabel),
                    condition,
                    null));

            emitStatement(ir, whileStatement.block());

            ir.add(new IRInstruction(
                    IROpCode.JUMP,
                    Operand.label(testLabel),
                    null,
                    null));

            ir.add(new IRInstruction(
                    IROpCode.LABEL,
                    Operand.label(endLabel),
                    null,
                    null));
        } else if (statement instanceof ForRecord forStatement) {
            String testLabel = newLabel();
            String endLabel = newLabel();

            emitStatement(ir, forStatement.initializer());

            ir.add(new IRInstruction(
                    IROpCode.LABEL,
                    Operand.label(testLabel),
                    null,
                    null));

            if (forStatement.condition() != null) {
                Operand condition = expression(ir, forStatement.condition());

                ir.add(new IRInstruction(
                        IROpCode.JUMP_IF_FALSE,
                        Operand.label(endLabel),
                        condition,
                        null));
            }

            emitStatement(ir, forStatement.block());
            emitStatement(ir, forStatement.update());

            ir.add(new IRInstruction(
                    IROpCode.JUMP,
                    Operand.label(testLabel),
                    null,
                    null));

            ir.add(new IRInstruction(
                    IROpCode.LABEL,
                    Operand.label(endLabel),
                    null,
                    null));
        } else if (statement instanceof PrintRecord printStatement) {
            Operand value = expression(ir, printStatement.expression());

            ir.add(new IRInstruction(
                    IROpCode.PRINT,
                    null,
                    value,
                    null));
        } else if (statement instanceof AssignmentRecord assignment) {
            if (!(assignment.target() instanceof VariableRecord)) {
                throw new UnsupportedOperationException(
                        "Por enquanto, só há suporte a atribuição para variável local.");
            }

            Operand destination = expression(ir, assignment.target());
            Operand value = expression(ir, assignment.value());

            ir.add(new IRInstruction(
                    IROpCode.ASSIGN,
                    destination,
                    value,
                    null));
        } else {
            throw new UnsupportedOperationException(
                    "Statement ainda não suportado: "
                            + statement.getClass().getSimpleName());
        }
    }

    private Operand expression(IRProgram ir, ExpressionInterface expression) {
        if (expression instanceof LiteralRecord literal
                && literal.value() instanceof Integer) {
            return Operand.constant(literal.value(), Operand.Type.I32);
        }

        if (expression instanceof VariableRecord variable) {
            return Operand.variable(
                    variable.name(),
                    variableTypes.getOrDefault(variable.name(), Operand.Type.I32));
        }

        if (expression instanceof BinaryExpressionRecord binary) {
            Operand left = expression(ir, binary.left());
            Operand right = expression(ir, binary.right());

            IROpCode opCode = switch (binary.operator()) {
                case ADD -> IROpCode.ADD;
                case SUB -> IROpCode.SUB;
                case MUL -> IROpCode.MUL;
                case DIV -> IROpCode.DIV;
                case LT -> IROpCode.LT;
                case GT -> IROpCode.GT;
                case LE -> IROpCode.LE;
                case GE -> IROpCode.GE;
                case EQ -> IROpCode.EQ;
                case NE -> IROpCode.NE;
                default -> throw new UnsupportedOperationException(
                        "Operador ainda não suportado: " + binary.operator());
            };

            boolean comparison = switch (opCode) {
                case LT, GT, LE, GE, EQ, NE -> true;
                default -> false;
            };

            Operand.Type resultType = comparison
                    ? Operand.Type.BOOL
                    : Operand.Type.I32;

            Operand result = Operand.temporary(
                    "t" + nextTemp++,
                    resultType);

            ir.add(new IRInstruction(opCode, result, left, right));
            return result;
        }

        if (expression instanceof CallRecord call) {
            List<Operand> arguments = call.arguments().stream()
                    .map(argument -> expression(ir, argument))
                    .toList();

            Operand result = Operand.temporary(
                    "t" + nextTemp++,
                    Operand.Type.I32);

            ir.add(new IRInstruction(
                    IROpCode.CALL,
                    result,
                    Operand.label(currentClassName + "." + call.name()),
                    null,
                    arguments));

            return result;
        }

        throw new UnsupportedOperationException(
                "Expressão ainda não suportada: "
                        + expression.getClass().getSimpleName());
    }

    private Operand.Type typeOf(TypeRecord type) {
        String name = type.name();

        return switch (name) {
            case "int" -> Operand.Type.I32;
            case "boolean" -> Operand.Type.BOOL;
            case "double" -> Operand.Type.F64;
            default -> Operand.Type.REF;
        };
    }

    private String newLabel() {
        return "L" + nextLabel++;
    }
}
