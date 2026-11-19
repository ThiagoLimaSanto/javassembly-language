default rel
extern printf
section .data
fmt_i32: db "%d", 10, 0
fmt_i64: db "%ld", 10, 0

fmt_f64: db "%f", 10, 0
fmt_string: db "%s", 10, 0

section .rodata
str_data_0: db 114, 101, 115, 117, 108, 116, 97, 100, 111, 58, 32, 37, 100, 10, 0

section .text

IRSmokeTest.main__String_array__ret_void:
    push rbp
    mov rbp, rsp
    mov esi, 7
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
    call IRSmokeTest.main__String_array__ret_void
    xor eax, eax
    leave
    ret
