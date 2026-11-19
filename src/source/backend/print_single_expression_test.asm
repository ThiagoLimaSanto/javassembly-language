default rel
extern printf
section .data
fmt_i32: db "%d", 10, 0
fmt_i64: db "%ld", 10, 0

fmt_f64: db "%f", 10, 0
fmt_string: db "%s", 10, 0

section .rodata
str_data_0: db 116, 101, 120, 116, 111, 32, 99, 111, 109, 32, 37, 100, 32, 108, 105, 116, 101, 114, 97, 108, 0

section .text

PrintSingleExpressionTest.main__String_array__ret_void:
    push rbp
    mov rbp, rsp
    sub rsp, 48
    mov qword [rbp-8], rdi
    mov eax, 42
    mov dword [rbp-12], eax
    mov rax, 3000000000
    mov qword [rbp-24], rax
    mov rax, 0x400c000000000000
    movq xmm0, rax
    movsd qword [rbp-32], xmm0
    mov eax, 1
    mov dword [rbp-36], eax
    lea rax, [rel str_data_0]
    mov qword [rbp-48], rax
    mov esi, 42
    lea rdi, [rel fmt_i32]
    xor eax, eax
    call printf
    mov rsi, 3000000000
    lea rdi, [rel fmt_i64]
    xor eax, eax
    call printf
    mov rax, 0x400c000000000000
    movq xmm0, rax
    lea rdi, [rel fmt_f64]
    mov eax, 1
    call printf
    mov esi, 1
    lea rdi, [rel fmt_i32]
    xor eax, eax
    call printf
    lea rsi, [rel str_data_0]
    lea rdi, [rel fmt_string]
    xor eax, eax
    call printf
    leave
    ret

global main
main:
    push rbp
    mov rbp, rsp
    mov rdi, rsi
    call PrintSingleExpressionTest.main__String_array__ret_void
    xor eax, eax
    leave
    ret
