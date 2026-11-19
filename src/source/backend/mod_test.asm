default rel
extern printf
section .data
fmt_i32: db "%d", 10, 0
fmt_i64: db "%ld", 10, 0

fmt_f64: db "%f", 10, 0
fmt_string: db "%s", 10, 0

section .text

ModBackendTest.remainder__int__int__ret_int:
    push rbp
    mov rbp, rsp
    sub rsp, 16
    mov dword [rbp-4], edi
    mov dword [rbp-8], esi
    mov eax, dword [rbp-4]
    mov ecx, dword [rbp-8]
    cmp ecx, -1
    jne .L_div_normal_0
    cmp eax, -2147483648
    jne .L_div_normal_0
    xor edx, edx
    jmp .L_div_done_0
.L_div_normal_0:
    cdq
    idiv ecx
.L_div_done_0:
    mov dword [rbp-12], edx
    mov eax, dword [rbp-12]
    leave
    ret

ModBackendTest.main__String_array__ret_void:
    push rbp
    mov rbp, rsp
    sub rsp, 16
    mov qword [rbp-8], rdi
    mov edi, 17
    mov esi, 5
    call ModBackendTest.remainder__int__int__ret_int
    mov dword [rbp-12], eax
    mov esi, dword [rbp-12]
    lea rdi, [rel fmt_i32]
    xor eax, eax
    call printf
    leave
    ret

global main
main:
    push rbp
    mov rbp, rsp
    mov rdi, rsi
    call ModBackendTest.main__String_array__ret_void
    xor eax, eax
    leave
    ret
