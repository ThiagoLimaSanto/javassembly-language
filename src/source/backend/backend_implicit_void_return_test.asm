default rel
extern printf
section .data
fmt_i32: db "%d", 10, 0
fmt_i64: db "%ld", 10, 0

fmt_f64: db "%f", 10, 0
fmt_string: db "%s", 10, 0

section .text

BackendImplicitVoidReturnTest.printValue__void__ret_void:
    push rbp
    mov rbp, rsp
    mov esi, 42
    lea rdi, [rel fmt_i32]
    xor eax, eax
    call printf
    leave
    ret

BackendImplicitVoidReturnTest.main__String_array__ret_void:
    push rbp
    mov rbp, rsp
    sub rsp, 16
    mov qword [rbp-8], rdi
    call BackendImplicitVoidReturnTest.printValue__void__ret_void
    leave
    ret

global main
main:
    push rbp
    mov rbp, rsp
    mov rdi, rsi
    call BackendImplicitVoidReturnTest.main__String_array__ret_void
    xor eax, eax
    leave
    ret
