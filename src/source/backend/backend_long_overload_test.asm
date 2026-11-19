default rel
extern printf
section .data
fmt_i32: db "%d", 10, 0

fmt_i64: db "%ld", 10, 0

section .text

BackendLongOverloadTest.choose__int__ret_int:
    push rbp
    mov rbp, rsp
    sub rsp, 16
    mov dword [rbp-4], edi
    mov eax, dword [rbp-4]
    mov ecx, 10
    add eax, ecx
    mov dword [rbp-8], eax
    mov eax, dword [rbp-8]
    leave
    ret

BackendLongOverloadTest.choose__long__ret_long:
    push rbp
    mov rbp, rsp
    sub rsp, 16
    mov qword [rbp-8], rdi
    mov rax, qword [rbp-8]
    mov rcx, 20
    add rax, rcx
    mov qword [rbp-16], rax
    mov rax, qword [rbp-16]
    leave
    ret

BackendLongOverloadTest.show__int__ret_void:
    push rbp
    mov rbp, rsp
    sub rsp, 16
    mov dword [rbp-4], edi
    mov esi, dword [rbp-4]
    lea rdi, [rel fmt_i32]
    xor eax, eax
    call printf
    leave
    ret

BackendLongOverloadTest.main__String_array__ret_void:
    push rbp
    mov rbp, rsp
    sub rsp, 112
    mov qword [rbp-8], rdi
    mov eax, 7
    movsxd rax, eax
    mov qword [rbp-16], rax
    mov rax, qword [rbp-16]
    mov qword [rbp-24], rax
    mov rsi, qword [rbp-24]
    lea rdi, [rel fmt_i64]
    xor eax, eax
    call printf
    mov rax, 3000000000
    mov rcx, 2
    add rax, rcx
    mov qword [rbp-32], rax
    mov rax, qword [rbp-32]
    mov qword [rbp-40], rax
    mov rsi, qword [rbp-40]
    lea rdi, [rel fmt_i64]
    xor eax, eax
    call printf
    mov edi, 1
    call BackendLongOverloadTest.choose__int__ret_int
    mov dword [rbp-44], eax
    mov esi, dword [rbp-44]
    lea rdi, [rel fmt_i32]
    xor eax, eax
    call printf
    mov rdi, 1
    call BackendLongOverloadTest.choose__long__ret_long
    mov qword [rbp-56], rax
    mov rsi, qword [rbp-56]
    lea rdi, [rel fmt_i64]
    xor eax, eax
    call printf
    mov edi, 99
    call BackendLongOverloadTest.show__int__ret_void
    mov rax, -9223372036854775808
    neg rax
    mov qword [rbp-64], rax
    mov rax, qword [rbp-64]
    mov qword [rbp-72], rax
    mov rax, 1
    neg rax
    mov qword [rbp-80], rax
    mov rax, qword [rbp-72]
    mov rcx, qword [rbp-80]
    cmp rcx, -1
    jne .L_div_normal_0
    mov rdx, -9223372036854775808
    cmp rax, rdx
    jne .L_div_normal_0
    jmp .L_div_done_0
.L_div_normal_0:
    cqo
    idiv rcx
.L_div_done_0:
    mov qword [rbp-88], rax
    mov rsi, qword [rbp-88]
    lea rdi, [rel fmt_i64]
    xor eax, eax
    call printf
    mov rax, 1
    neg rax
    mov qword [rbp-96], rax
    mov rax, qword [rbp-72]
    mov rcx, qword [rbp-96]
    cmp rcx, -1
    jne .L_div_normal_1
    mov rdx, -9223372036854775808
    cmp rax, rdx
    jne .L_div_normal_1
    xor edx, edx
    jmp .L_div_done_1
.L_div_normal_1:
    cqo
    idiv rcx
.L_div_done_1:
    mov qword [rbp-104], rdx
    mov rsi, qword [rbp-104]
    lea rdi, [rel fmt_i64]
    xor eax, eax
    call printf
    leave
    ret

global main
main:
    push rbp
    mov rbp, rsp
    mov rdi, rsi
    call BackendLongOverloadTest.main__String_array__ret_void
    xor eax, eax
    leave
    ret
