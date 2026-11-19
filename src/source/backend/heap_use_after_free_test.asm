default rel
section .rodata
__invalid_reference_message: db 69, 114, 114, 111, 32, 100, 101, 32, 101, 120, 101, 99, 117, 195, 167, 195, 163, 111, 58, 32, 116, 101, 110, 116, 97, 116, 105, 118, 97, 32, 100, 101, 32, 97, 99, 101, 115, 115, 97, 114, 32, 117, 109, 32, 111, 98, 106, 101, 116, 111, 32, 110, 117, 108, 111, 32, 111, 117, 32, 106, 195, 161, 32, 108, 105, 98, 101, 114, 97, 100, 111, 46, 10, 0
__invalid_reference_message_length equ $ - __invalid_reference_message - 1
__array_bounds_message: db 69, 114, 114, 111, 32, 100, 101, 32, 101, 120, 101, 99, 117, 195, 167, 195, 163, 111, 58, 32, 195, 173, 110, 100, 105, 99, 101, 32, 102, 111, 114, 97, 32, 100, 111, 115, 32, 108, 105, 109, 105, 116, 101, 115, 32, 100, 111, 32, 97, 114, 114, 97, 121, 46, 10, 0
__array_bounds_message_length equ $ - __array_bounds_message - 1
__array_size_message: db 69, 114, 114, 111, 32, 100, 101, 32, 101, 120, 101, 99, 117, 195, 167, 195, 163, 111, 58, 32, 116, 97, 109, 97, 110, 104, 111, 32, 105, 110, 118, 195, 161, 108, 105, 100, 111, 32, 112, 97, 114, 97, 32, 111, 32, 97, 114, 114, 97, 121, 46, 10, 0
__array_size_message_length equ $ - __array_size_message - 1
__invalid_free_message: db 69, 114, 114, 111, 32, 100, 101, 32, 101, 120, 101, 99, 117, 195, 167, 195, 163, 111, 58, 32, 102, 114, 101, 101, 32, 114, 101, 99, 101, 98, 101, 117, 32, 117, 109, 32, 112, 111, 110, 116, 101, 105, 114, 111, 32, 105, 110, 118, 195, 161, 108, 105, 100, 111, 32, 111, 117, 32, 106, 195, 161, 32, 108, 105, 98, 101, 114, 97, 100, 111, 46, 10, 0
__invalid_free_message_length equ $ - __invalid_free_message - 1
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
__heap_start: resq 1
__heap_end: resq 1
__number_buffer: resb 32

section .text

HeapUseAfterFreeTest.main__String_array__ret_void:
    push rbp
    mov rbp, rsp
    sub rsp, 32
    mov edi, 4
    call __heap_alloc
    mov qword [rbp-8], rax
    mov rax, qword [rbp-8]
    mov qword [rbp-16], rax
    mov rax, qword [rbp-16]
    mov qword [rbp-24], rax
    mov r11, qword [rbp-16]
    test r11, r11
    jz __runtime_invalid_reference
    mov rdi, r11
    call __heap_validate
    mov edx, 42
    mov dword [r11], edx
    mov rdi, qword [rbp-16]
    test rdi, rdi
    jz .L_free_done_0
    call __heap_free
    xor eax, eax
    mov qword [rbp-16], rax
.L_free_done_0:
    mov rax, qword [rbp-24]
    test rax, rax
    jz __runtime_invalid_reference
    mov rdi, rax
    push rax
    call __heap_validate
    pop rax
    mov eax, dword [rax]
    mov dword [rbp-28], eax
    mov eax, dword [rbp-28]
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
    call HeapUseAfterFreeTest.main__String_array__ret_void
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
__heap_alloc:
    mov r8, rdi
    add r8, 15
    jc .heap_alloc_failed
    and r8, -16
    add r8, 16
    jc .heap_alloc_failed
    mov r9, qword [rel __heap_start]
    test r9, r9
    jnz .heap_search
    xor edi, edi
    mov eax, 12
    syscall
    add rax, 15
    jc .heap_alloc_failed
    and rax, -16
    mov qword [rel __heap_start], rax
    mov qword [rel __heap_end], rax
    mov r9, rax
.heap_search:
    cmp r9, qword [rel __heap_end]
    jae .heap_grow
    cmp qword [r9 + 8], 0
    jne .heap_next_block
    cmp qword [r9], r8
    jae .heap_reuse_block
.heap_next_block:
    add r9, qword [r9]
    jmp .heap_search
.heap_reuse_block:
    mov r10, qword [r9]
    sub r10, r8
    cmp r10, 32
    jb .heap_mark_allocated
    lea rax, [r9 + r8]
    mov qword [rax], r10
    mov qword [rax + 8], 0
    mov qword [r9], r8
.heap_mark_allocated:
    mov qword [r9 + 8], 1
    jmp .heap_clear_payload
.heap_grow:
    mov r9, qword [rel __heap_end]
    mov r10, r9
    add r10, r8
    jc .heap_alloc_failed
    mov rdi, r10
    mov eax, 12
    syscall
    cmp rax, r10
    jb .heap_alloc_failed
    mov qword [rel __heap_end], r10
    mov qword [r9], r8
    mov qword [r9 + 8], 1
.heap_clear_payload:
    mov qword [r9 + 8], 1
    lea rdi, [r9 + 16]
    mov rcx, r8
    shr rcx, 3
    sub rcx, 2
    xor eax, eax
    cld
    rep stosq
    lea rax, [r9 + 16]
    ret
.heap_alloc_failed:
    mov edi, 1
    mov eax, 60
    syscall
    ud2
__heap_free:
    test rdi, rdi
    jz .heap_free_done
    mov r11, rdi
    mov r8, qword [rel __heap_start]
    test r8, r8
    jz .heap_invalid_free
.heap_free_search:
    cmp r8, qword [rel __heap_end]
    jae .heap_invalid_free
    lea rax, [r8 + 16]
    cmp rax, r11
    je .heap_free_found
    mov rax, qword [r8]
    test rax, rax
    jz .heap_invalid_free
    add r8, rax
    jmp .heap_free_search
.heap_free_found:
    cmp qword [r8 + 8], 1
    jne .heap_invalid_free
    mov qword [r8 + 8], 0
    mov r9, qword [r8]
    lea r10, [r8 + r9]
    cmp r10, qword [rel __heap_end]
    jae .heap_try_previous
    cmp qword [r10 + 8], 0
    jne .heap_try_previous
    add r9, qword [r10]
    mov qword [r8], r9
.heap_try_previous:
    mov r10, qword [rel __heap_start]
    xor edx, edx
.heap_previous_search:
    cmp r10, r8
    jae .heap_free_done
    mov rdx, r10
    add r10, qword [r10]
    jmp .heap_previous_search
.heap_free_done:
    test rdx, rdx
    jz .heap_free_return
    cmp qword [rdx + 8], 0
    jne .heap_free_return
    mov rax, qword [r8]
    add qword [rdx], rax
.heap_free_return:
    ret
.heap_invalid_free:
    mov eax, 1
    mov edi, 2
    lea rsi, [rel __invalid_free_message]
    mov edx, __invalid_free_message_length
    syscall
    mov eax, 60
    mov edi, 1
    syscall
    ud2
__heap_validate:
    mov r11, rdi
    mov r8, qword [rel __heap_start]
    test r8, r8
    jz __runtime_invalid_reference
.heap_validate_search:
    cmp r8, qword [rel __heap_end]
    jae __runtime_invalid_reference
    lea rax, [r8 + 16]
    cmp rax, r11
    je .heap_validate_found
    mov rax, qword [r8]
    test rax, rax
    jz __runtime_invalid_reference
    add r8, rax
    jmp .heap_validate_search
.heap_validate_found:
    cmp qword [r8 + 8], 1
    jne __runtime_invalid_reference
    ret
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
