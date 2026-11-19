default rel
extern printf
section .data
fmt_i32: db "%d", 10, 0
fmt_i64: db "%ld", 10, 0

fmt_f64: db "%f", 10, 0
fmt_string: db "%s", 10, 0

section .rodata
str_data_0: db 84, 111, 116, 97, 108, 58, 32, 37, 100, 10, 0
str_data_1: db 118, 101, 114, 100, 97, 100, 101, 105, 114, 111, 58, 32, 37, 100, 10, 0
str_data_2: db 70, 97, 108, 115, 111, 58, 32, 37, 100, 10, 0
str_data_3: db 87, 104, 105, 108, 101, 58, 32, 37, 100, 10, 0
str_data_4: db 70, 111, 114, 58, 32, 37, 100, 10, 0

section .text

IRSmokeTest.sum__int__int__ret_int:
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
    leave
    ret

IRSmokeTest.main__String_array__ret_void:
    push rbp
    mov rbp, rsp
    sub rsp, 48
    mov qword [rbp-8], rdi
    mov edi, 2
    mov esi, 3
    call IRSmokeTest.sum__int__int__ret_int
    mov dword [rbp-12], eax
    mov eax, dword [rbp-12]
    mov dword [rbp-16], eax
    mov esi, dword [rbp-12]
    lea rdi, [rel str_data_0]
    mov eax, 0
    call printf
    mov eax, dword [rbp-12]
    mov ecx, 4
    cmp eax, ecx
    setg al
    movzx eax, al
    mov dword [rbp-20], eax
    mov eax, dword [rbp-20]
    test eax, eax
    jz L0
    mov esi, dword [rbp-16]
    lea rdi, [rel str_data_1]
    mov eax, 0
    call printf
    jmp L1
L0:
    mov esi, 0
    lea rdi, [rel str_data_2]
    mov eax, 0
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
    lea rdi, [rel str_data_3]
    mov eax, 0
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
    lea rdi, [rel str_data_4]
    mov eax, 0
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
    call IRSmokeTest.main__String_array__ret_void
    xor eax, eax
    leave
    ret
