default rel
extern printf
section .data
fmt_i32: db "%d", 10, 0

section .text

SubtractionBackendTest.subtract:
    push rbp
    mov rbp, rsp
    sub rsp, 16
    mov dword [rbp-4], edi
    mov dword [rbp-8], esi
    mov eax, dword [rbp-4]
    mov ecx, dword [rbp-8]
    sub eax, ecx
    mov dword [rbp-12], eax
    mov eax, dword [rbp-12]
    leave
    ret

SubtractionBackendTest.main:
    push rbp
    mov rbp, rsp
    sub rsp, 16
    mov qword [rbp-8], rdi
    mov edi, 17
    mov esi, 5
    call SubtractionBackendTest.subtract
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
    call SubtractionBackendTest.main
    xor eax, eax
    leave
    ret
