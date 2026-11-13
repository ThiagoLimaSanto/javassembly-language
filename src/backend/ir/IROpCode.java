package src.backend.ir;

public enum IROpCode {
    ASSIGN,
    CAST_I32_TO_I64,
    ADD,
    SUB,
    MUL,
    DIV,
    MOD,
    NEG,

    LT,
    GT,
    LE,
    GE,
    EQ,
    NE,

    LABEL,
    JUMP,
    JUMP_IF_FALSE,
    JUMP_IF_TRUE,

    CALL,
    RETURN,
    PRINT,
    PARAM
}