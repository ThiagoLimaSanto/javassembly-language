default rel
extern printf
section .data
fmt_i32: db "%d", 10, 0
fmt_i64: db "%ld", 10, 0

fmt_f64: db "%f", 10, 0
fmt_string: db "%s", 10, 0

section .rodata
str_data_0: db 115, 117, 109, 61, 37, 108, 102, 32, 109, 105, 120, 101, 100, 61, 37, 108, 102, 32, 110, 101, 103, 97, 116, 105, 118, 101, 61, 37, 108, 102, 32, 102, 117, 110, 99, 116, 105, 111, 110, 61, 37, 108, 102, 10, 0

section .text

PrintfDoubleExpressionTest.multiply__double__double__ret_double:
    push rbp
    mov rbp, rsp
    sub rsp, 32
    movsd qword [rbp-8], xmm0
    movsd qword [rbp-16], xmm1
    movsd xmm0, qword [rbp-8]
    movsd xmm1, qword [rbp-16]
    mulsd xmm0, xmm1
    movsd qword [rbp-24], xmm0
    movsd xmm0, qword [rbp-24]
    leave
    ret

PrintfDoubleExpressionTest.main__String_array__ret_void:
    push rbp
    mov rbp, rsp
    sub rsp, 80
    mov qword [rbp-8], rdi
    mov rax, 0x3ff8000000000000
    movq xmm0, rax
    mov rax, 0x4002000000000000
    movq xmm1, rax
    addsd xmm0, xmm1
    movsd qword [rbp-16], xmm0
    movsd xmm0, qword [rbp-16]
    movsd qword [rbp-24], xmm0
    mov eax, 2
    cvtsi2sd xmm0, eax
    movsd qword [rbp-32], xmm0
    movsd xmm0, qword [rbp-32]
    mov rax, 0x3fe0000000000000
    movq xmm1, rax
    addsd xmm0, xmm1
    movsd qword [rbp-40], xmm0
    movsd xmm0, qword [rbp-40]
    movsd qword [rbp-48], xmm0
    mov rax, 0x4012000000000000
    movq xmm0, rax
    mov rax, 0x8000000000000000
    movq xmm1, rax
    xorpd xmm0, xmm1
    movsd qword [rbp-56], xmm0
    movsd xmm0, qword [rbp-56]
    movsd qword [rbp-64], xmm0
    mov rax, 0x3ff8000000000000
    movq xmm0, rax
    mov rax, 0x4000000000000000
    movq xmm1, rax
    call PrintfDoubleExpressionTest.multiply__double__double__ret_double
    movsd qword [rbp-72], xmm0
    movsd xmm0, qword [rbp-24]
    movsd xmm1, qword [rbp-48]
    movsd xmm2, qword [rbp-64]
    movsd xmm3, qword [rbp-72]
    lea rdi, [rel str_data_0]
    mov eax, 4
    call printf
    leave
    ret

global main
main:
    push rbp
    mov rbp, rsp
    mov rdi, rsi
    call PrintfDoubleExpressionTest.main__String_array__ret_void
    xor eax, eax
    leave
    ret
