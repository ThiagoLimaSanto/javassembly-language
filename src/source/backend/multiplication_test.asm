default rel
extern printf
section .data
fmt_i32: db "%d", 10, 0
fmt_i64: db "%ld", 10, 0

fmt_f64: db "%f", 10, 0
fmt_string: db "%s", 10, 0

section .rodata
str_data_0: db 37, 115, 32, 37, 100, 32, 10, 0
str_data_1: db 82, 101, 115, 117, 108, 116, 97, 100, 111, 58, 0

section .text

MultiplicationBackendTest.multiply__int__int__ret_int:
    push rbp
    mov rbp, rsp
    sub rsp, 16
    mov dword [rbp-4], edi
    mov dword [rbp-8], esi
    mov eax, dword [rbp-4]
    mov ecx, dword [rbp-8]
    imul eax, ecx
    mov dword [rbp-12], eax
    mov eax, dword [rbp-12]
    leave
    ret

MultiplicationBackendTest.main__String_array__ret_void:
    push rbp
    mov rbp, rsp
    sub rsp, 16
    mov qword [rbp-8], rdi
    mov edi, 6
    mov esi, 7
    call MultiplicationBackendTest.multiply__int__int__ret_int
    mov dword [rbp-12], eax
    lea rsi, [rel str_data_1]
    mov edx, dword [rbp-12]
    lea rdi, [rel str_data_0]
    mov eax, 0
    call printf
    leave
    ret

global main
main:
    push rbp
    mov rbp, rsp
    mov rdi, rsi
    call MultiplicationBackendTest.main__String_array__ret_void
    xor eax, eax
    leave
    ret
