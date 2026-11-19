default rel
section .rodata
__invalid_reference_message: db 69, 114, 114, 111, 32, 100, 101, 32, 101, 120, 101, 99, 117, 195, 167, 195, 163, 111, 58, 32, 116, 101, 110, 116, 97, 116, 105, 118, 97, 32, 100, 101, 32, 97, 99, 101, 115, 115, 97, 114, 32, 117, 109, 32, 111, 98, 106, 101, 116, 111, 32, 110, 117, 108, 111, 32, 111, 117, 32, 106, 195, 161, 32, 108, 105, 98, 101, 114, 97, 100, 111, 46, 10, 0
__invalid_reference_message_length equ $ - __invalid_reference_message - 1
__array_bounds_message: db 69, 114, 114, 111, 32, 100, 101, 32, 101, 120, 101, 99, 117, 195, 167, 195, 163, 111, 58, 32, 195, 173, 110, 100, 105, 99, 101, 32, 102, 111, 114, 97, 32, 100, 111, 115, 32, 108, 105, 109, 105, 116, 101, 115, 32, 100, 111, 32, 97, 114, 114, 97, 121, 46, 10, 0
__array_bounds_message_length equ $ - __array_bounds_message - 1
__array_size_message: db 69, 114, 114, 111, 32, 100, 101, 32, 101, 120, 101, 99, 117, 195, 167, 195, 163, 111, 58, 32, 116, 97, 109, 97, 110, 104, 111, 32, 105, 110, 118, 195, 161, 108, 105, 100, 111, 32, 112, 97, 114, 97, 32, 111, 32, 97, 114, 114, 97, 121, 46, 10, 0
__array_size_message_length equ $ - __array_size_message - 1
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

ArrayNegativeInnerSizeTest.main__String_array__ret_void:
    push rbp
    mov rbp, rsp
    sub rsp, 48
    mov eax, 0
    test eax, eax
    js __runtime_invalid_array_size
    mov eax, -1
    test eax, eax
    js __runtime_invalid_array_size
    mov eax, 0
    test eax, eax
    js __runtime_invalid_array_size
    movsxd rsi, eax
    imul rsi, 8
    add rsi, 16
    jc __runtime_invalid_array_size
    mov eax, 9
    xor edi, edi
    mov edx, 3
    mov r10d, 34
    mov r8, -1
    xor r9d, r9d
    syscall
    test rax, rax
    jns .L_array_alloc_ok_0
    mov edi, 1
    mov eax, 60
    syscall
.L_array_alloc_ok_0:
    mov qword [rax], rsi
    mov ecx, 0
    mov qword [rax + 8], rcx
    add rax, 16
    mov qword [rbp-8], rax
    mov eax, 0
    mov dword [rbp-12], eax
L0:
    mov eax, dword [rbp-12]
    mov ecx, 0
    cmp eax, ecx
    setl al
    movzx eax, al
    mov dword [rbp-16], eax
    mov eax, dword [rbp-16]
    test eax, eax
    jz L1
    mov eax, -1
    test eax, eax
    js __runtime_invalid_array_size
    movsxd rsi, eax
    imul rsi, 4
    add rsi, 16
    jc __runtime_invalid_array_size
    mov eax, 9
    xor edi, edi
    mov edx, 3
    mov r10d, 34
    mov r8, -1
    xor r9d, r9d
    syscall
    test rax, rax
    jns .L_array_alloc_ok_1
    mov edi, 1
    mov eax, 60
    syscall
.L_array_alloc_ok_1:
    mov qword [rax], rsi
    mov ecx, -1
    mov qword [rax + 8], rcx
    add rax, 16
    mov qword [rbp-24], rax
    mov r11, qword [rbp-8]
    test r11, r11
    jz __runtime_invalid_reference
    mov eax, dword [rbp-12]
    test eax, eax
    js __runtime_array_bounds
    movsxd r10, eax
    cmp r10, qword [r11 - 8]
    jae __runtime_array_bounds
    imul r10, 8
    add r11, 16
    mov rdx, qword [rbp-24]
    mov qword [r11 + r10], rdx
    mov eax, dword [rbp-12]
    mov ecx, 1
    add eax, ecx
    mov dword [rbp-28], eax
    mov eax, dword [rbp-28]
    mov dword [rbp-12], eax
    jmp L0
L1:
    mov rax, qword [rbp-8]
    mov qword [rbp-40], rax
    mov r11, qword [rbp-40]
    test r11, r11
    jz __runtime_invalid_reference
    mov eax, dword [r11 - 8]
    mov dword [rbp-44], eax
    mov eax, dword [rbp-44]
    movsxd rax, eax
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
    call ArrayNegativeInnerSizeTest.main__String_array__ret_void
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
__runtime_invalid_reference:
    mov eax, 1
    mov edi, 2
    lea rsi, [rel __invalid_reference_message]
    mov edx, __invalid_reference_message_length
    syscall
    mov eax, 60
    mov edi, 1
    syscall
    ud2
__runtime_array_bounds:
    mov eax, 1
    mov edi, 2
    lea rsi, [rel __array_bounds_message]
    mov edx, __array_bounds_message_length
    syscall
    mov eax, 60
    mov edi, 1
    syscall
    ud2
__runtime_invalid_array_size:
    mov eax, 1
    mov edi, 2
    lea rsi, [rel __array_size_message]
    mov edx, __array_size_message_length
    syscall
    mov eax, 60
    mov edi, 1
    syscall
    ud2
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
