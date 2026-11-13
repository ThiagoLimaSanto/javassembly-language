default rel
extern printf
section .data
fmt_i32: db "%d", 10, 0

section .text

IRSmokeTest.sum:
    push rbp
    mov rbp, rsp
    sub rsp, 16
    mov dword [rbp-4], edi
    mov dword [rbp-8], esi
    mov eax, dword [rbp-4]
    mov ecx, dword [rbp-8]
    add eax, ecx
    mov dword [rbp-12], eax
    mov eax, dword [rbp-12]
    mov dword [rbp-16], eax
    mov eax, dword [rbp-16]
    leave
    ret

IRSmokeTest.main:
    push rbp
    mov rbp, rsp
    sub rsp, 48
    mov qword [rbp-8], rdi
    mov edi, 2
    mov esi, 3
    call IRSmokeTest.sum
    mov dword [rbp-12], eax
    mov eax, dword [rbp-12]
    mov dword [rbp-16], eax
    mov esi, dword [rbp-16]
    lea rdi, [rel fmt_i32]
    xor eax, eax
    call printf
    mov eax, dword [rbp-16]
    mov ecx, 4
    cmp eax, ecx
    setg al
    movzx eax, al
    mov dword [rbp-20], eax
    mov eax, dword [rbp-20]
    test eax, eax
    jz L0
    mov esi, dword [rbp-16]
    lea rdi, [rel fmt_i32]
    xor eax, eax
    call printf
    jmp L1
L0:
    mov esi, 0
    lea rdi, [rel fmt_i32]
    xor eax, eax
    call printf
L1:
    mov eax, 0
    mov dword [rbp-24], eax
L2:
    mov eax, dword [rbp-24]
    mov ecx, 2
    cmp eax, ecx
    setl al
    movzx eax, al
    mov dword [rbp-28], eax
    mov eax, dword [rbp-28]
    test eax, eax
    jz L3
    mov esi, dword [rbp-24]
    lea rdi, [rel fmt_i32]
    xor eax, eax
    call printf
    mov eax, dword [rbp-24]
    mov ecx, 1
    add eax, ecx
    mov dword [rbp-32], eax
    mov eax, dword [rbp-32]
    mov dword [rbp-24], eax
    jmp L2
L3:
    mov eax, 0
    mov dword [rbp-36], eax
L4:
    mov eax, dword [rbp-36]
    mov ecx, 2
    cmp eax, ecx
    setl al
    movzx eax, al
    mov dword [rbp-40], eax
    mov eax, dword [rbp-40]
    test eax, eax
    jz L5
    mov esi, dword [rbp-36]
    lea rdi, [rel fmt_i32]
    xor eax, eax
    call printf
    mov eax, dword [rbp-36]
    mov ecx, 1
    add eax, ecx
    mov dword [rbp-44], eax
    mov eax, dword [rbp-44]
    mov dword [rbp-36], eax
    jmp L4
L5:
    leave
    ret

global main
main:
    push rbp
    mov rbp, rsp
    mov rdi, rsi
    call IRSmokeTest.main
    xor eax, eax
    leave
    ret
