default rel
extern printf
section .data
fmt_i32: db "%d", 10, 0
fmt_i64: db "%ld", 10, 0

fmt_f64: db "%f", 10, 0
fmt_string: db "%s", 10, 0

section .rodata
str_data_0: db 105, 110, 116, 61, 37, 100, 32, 108, 111, 110, 103, 61, 37, 108, 100, 32, 108, 111, 110, 103, 45, 105, 110, 116, 61, 37, 108, 105, 32, 102, 108, 111, 97, 116, 61, 37, 102, 32, 100, 111, 117, 98, 108, 101, 61, 37, 108, 102, 32, 116, 101, 120, 116, 61, 37, 115, 32, 98, 111, 111, 108, 61, 37, 100, 32, 37, 37, 10, 0
str_data_1: db 74, 97, 118, 97, 115, 115, 101, 109, 98, 108, 121, 0
str_data_2: db 49, 48, 48, 37, 37, 32, 99, 111, 110, 99, 108, 117, 195, 173, 100, 111, 10, 0

section .text

PrintfFormatTest.main__String_array__ret_void:
    push rbp
    mov rbp, rsp
    mov esi, 42
    mov rdx, 3000000000
    mov rcx, 3000000000
    mov rax, 0x400c000000000000
    movq xmm0, rax
    mov rax, 0x400c000000000000
    movq xmm1, rax
    lea r8, [rel str_data_1]
    mov r9d, 1
    lea rdi, [rel str_data_0]
    mov eax, 2
    call printf
    lea rdi, [rel str_data_2]
    mov eax, 0
    call printf
    leave
    ret

global main
main:
    push rbp
    mov rbp, rsp
    mov rdi, rsi
    call PrintfFormatTest.main__String_array__ret_void
    xor eax, eax
    leave
    ret
