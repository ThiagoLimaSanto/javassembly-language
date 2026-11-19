package src.backend.ir;

public enum IROpCode {
    ASSIGN,
    CAST_I32_TO_I64,
    CAST_I32_TO_F64,
    CAST_I64_TO_F64,
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
    PRINT_FORMAT,
    PARAM,

    LOAD_FIELD,
    STORE_FIELD,
    ALLOC_OBJECT,
    FREE_OBJECT,
    ALLOC_ARRAY,
    LOAD_ARRAY,
    STORE_ARRAY,
    ARRAY_LENGTH,
    FREE_ARRAY,
    CHECK_ARRAY_SIZE,
}
