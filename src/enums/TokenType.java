package src.enums;
public enum TokenType {

    // Palavras-chave
    FUNC,
    INT,
    WHILE,
    FOR,
    RETURN,
    CLASS,
    THIS,
    TRUE,
    FALSE,
    NULL,
    NEW,
    SUPER,
    PUBLIC,
    PRIVATE,
    DOUBLE,
    BOOLEAN,
    NUMBER,
    STRING,
    TEXT,
    VOID,
    MAIN,

    // Instruções Assembly
    MOV,
    ADD,
    SUB,
    MUL,
    DIV,
    PUSH,
    POP,
    CMP,
    JMP,
    PRINT,

    // Condições do CMP
    EQ,     // equal
    NE,     // not equal
    LT,     // less than
    GT,     // greater than
    LE,     // less or equal
    GE,     // greater or equal

    // Registradores e identificadores
    REGISTER,
    IDENTIFIER,

    // Operadores
    ASSIGN,
    AND,
    OR,

    // Delimitadores
    OPEN_BRACE,
    CLOSE_BRACE,
    OPEN_PARENTHESIS,
    CLOSE_PARENTHESIS,

    // Separadores
    SEMICOLON,
    COMMA,
    DOT,
    QUOTE,
    SINGLE_QUOTE,

    // Fim de arquivo
    EOF
}
