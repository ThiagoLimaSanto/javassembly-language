default rel
section .rodata
str_data_0: db 10, 0
str_data_1: db 45, 0
str_data_2: db 46, 0
str_data_3: db 110, 97, 110, 0
str_data_4: db 105, 110, 102, 0
str_data_5: db 111, 118, 101, 114, 102, 108, 111, 119, 0
str_data_6: db 40, 110, 117, 108, 108, 41, 0
__f64_scale: dq 0x412e848000000000
__f64_round: dq 0x3fe0000000000000

section .bss
__number_buffer: resb 32

section .text

ObjectInheritanceTest.main__String_array__ret_void:
    push rbp
    mov rbp, rsp
    sub rsp, 48
    mov esi, 24
    mov eax, 9
    xor edi, edi
    mov edx, 3
    mov r10d, 34
    mov r8, -1
    xor r9d, r9d
    syscall
    test rax, rax
    jns .L_alloc_ok_0
    mov edi, 1
    mov eax, 60
    syscall
.L_alloc_ok_0:
    mov qword [rbp-8], rax
    mov r11, qword [rbp-8]
    mov edx, 18
    mov dword [r11], edx
    mov r11, qword [rbp-8]
    mov rdx, 3000000002
    mov qword [r11 + 8], rdx
    mov r11, qword [rbp-8]
    mov rax, 0x40ab590000000000
    movq xmm0, rax
    movsd qword [r11 + 16], xmm0
    mov rax, qword [rbp-8]
    mov eax, dword [rax]
    mov dword [rbp-12], eax
    mov eax, dword [rbp-12]
    movsxd rax, eax
    call __print_i64
    lea rsi, [rel str_data_0]
    mov edx, 1
    call __write_all
    mov rax, qword [rbp-8]
    mov rdx, qword [rax + 8]
    mov qword [rbp-24], rdx
    mov rax, qword [rbp-24]
    call __print_i64
    lea rsi, [rel str_data_0]
    mov edx, 1
    call __write_all
    mov rax, qword [rbp-8]
    movsd xmm0, qword [rax + 16]
    movsd qword [rbp-32], xmm0
    movsd xmm0, qword [rbp-32]
    call __print_f64
    lea rsi, [rel str_data_0]
    mov edx, 1
    call __write_all
    mov r11, qword [rbp-8]
    mov rdx, 4000000003
    mov qword [r11 + 8], rdx
    mov rax, qword [rbp-8]
    mov rdx, qword [rax + 8]
    mov qword [rbp-40], rdx
    mov rax, qword [rbp-40]
    call __print_i64
    lea rsi, [rel str_data_0]
    mov edx, 1
    call __write_all
    leave
    ret

global _start
_start:
    xor ebp, ebp
    lea rdi, [rsp+8]
    call ObjectInheritanceTest.main__String_array__ret_void
    xor edi, edi
    mov eax, 60
    syscall
__write_all:
    test rdx, rdx
    jz .write_done
.write_loop:
    mov eax, 1
    mov edi, 1
    syscall
    test rax, rax
    jle .write_error
    add rsi, rax
    sub rdx, rax
    jnz .write_loop
.write_done:
    ret
.write_error:
    mov edi, 1
    mov eax, 60
    syscall
    ud2
__write_cstr:
    test rsi, rsi
    jnz .cstring_scan
    lea rsi, [rel str_data_6]
    mov edx, 6
    jmp __write_all
.cstring_scan:
    mov rdx, rsi
.cstring_loop:
    cmp byte [rdx], 0
    je .cstring_ready
    inc rdx
    jmp .cstring_loop
.cstring_ready:
    sub rdx, rsi
    jmp __write_all
__print_u64:
    lea r8, [rel __number_buffer + 32]
    mov r9d, 10
.u64_loop:
    xor edx, edx
    div r9
    dec r8
    add dl, '0'
    mov byte [r8], dl
    test rax, rax
    jnz .u64_loop
    mov rsi, r8
    lea rdx, [rel __number_buffer + 32]
    sub rdx, r8
    jmp __write_all
__print_i64:
    test rax, rax
    jns .i64_positive
    mov r10, rax
    neg r10
    lea rsi, [rel str_data_1]
    mov edx, 1
    call __write_all
    mov rax, r10
    jmp __print_u64
.i64_positive:
    jmp __print_u64
__print_f64:
    movq rax, xmm0
    mov rcx, 0x8000000000000000
    test rax, rcx
    jz .f64_abs_ready
    shl rax, 1
    shr rax, 1
    movq xmm0, rax
    lea rsi, [rel str_data_1]
    mov edx, 1
    call __write_all
.f64_abs_ready:
    movq rax, xmm0
    mov rcx, 0x7ff0000000000000
    mov rdx, rax
    and rdx, rcx
    cmp rdx, rcx
    jne .f64_finite
    mov rdx, 0x000fffffffffffff
    test rax, rdx
    jnz .f64_nan
    lea rsi, [rel str_data_4]
    mov edx, 3
    jmp __write_all
.f64_nan:
    lea rsi, [rel str_data_3]
    mov edx, 3
    jmp __write_all
.f64_finite:
    mulsd xmm0, qword [rel __f64_scale]
    addsd xmm0, qword [rel __f64_round]
    cvttsd2si rax, xmm0
    mov rcx, 0x7fffffffffffffff
    cmp rax, rcx
    ja .f64_overflow
    mov ecx, 1000000
    xor edx, edx
    div rcx
    mov r10, rdx
    call __print_u64
    lea rsi, [rel str_data_2]
    mov edx, 1
    call __write_all
    mov rax, r10
    lea r8, [rel __number_buffer + 6]
    mov r9d, 10
    mov ecx, 6
.f64_fraction_loop:
    xor edx, edx
    div r9
    dec r8
    add dl, '0'
    mov byte [r8], dl
    loop .f64_fraction_loop
    mov rsi, r8
    mov edx, 6
    jmp __write_all
.f64_overflow:
    lea rsi, [rel str_data_5]
    mov edx, 8
    jmp __write_all
section .note.GNU-stack noalloc noexec nowrite progbits
