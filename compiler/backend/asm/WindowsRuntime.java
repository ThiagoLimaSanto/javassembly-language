package compiler.backend.asm;

/** Win64 ABI adapters for the calling convention used inside generated language code. */
final class WindowsRuntime {
    private WindowsRuntime() {
    }

    static void appendImports(StringBuilder asm) {
        asm.append("extern __imp_GetStdHandle\n")
                .append("extern __imp_ReadFile\n")
                .append("extern __imp_WriteFile\n")
                .append("extern __imp_VirtualAlloc\n")
                .append("extern __imp_ExitProcess\n")
                .append("extern __imp_SetConsoleCP\n")
                .append("extern __imp_SetConsoleOutputCP\n")
                .append("extern __imp_GetCommandLineW\n")
                .append("extern __imp_CommandLineToArgvW\n")
                .append("extern __imp_WideCharToMultiByte\n")
                .append("extern __imp_LocalFree\n\n");
    }

    static void appendIoSupport(StringBuilder asm) {
        appendApiFrameStart(asm, "__jvs_write_once");
        asm.append("    mov ecx, -11\n")
                .append("    cmp qword [rbp-8], 2\n")
                .append("    jne .write_handle_ready\n")
                .append("    mov ecx, -12\n")
                .append(".write_handle_ready:\n")
                .append("    call qword [rel __imp_GetStdHandle]\n")
                .append("    mov qword [rbp-176], rax\n")
                .append("    mov rcx, qword [rbp-176]\n")
                .append("    mov rdx, qword [rbp-16]\n")
                .append("    mov r8d, dword [rbp-24]\n")
                .append("    lea r9, [rbp-168]\n")
                .append("    mov qword [rsp+32], 0\n")
                .append("    call qword [rel __imp_WriteFile]\n")
                .append("    test eax, eax\n")
                .append("    jz .write_failed\n")
                .append("    mov eax, dword [rbp-168]\n")
                .append("    jmp .write_done\n")
                .append(".write_failed:\n")
                .append("    mov rax, -1\n")
                .append(".write_done:\n");
        appendApiFrameEnd(asm);

        appendApiFrameStart(asm, "__jvs_read_once");
        asm.append("    mov ecx, -10\n")
                .append("    call qword [rel __imp_GetStdHandle]\n")
                .append("    mov qword [rbp-176], rax\n")
                .append("    mov rcx, qword [rbp-176]\n")
                .append("    mov rdx, qword [rbp-16]\n")
                .append("    mov r8d, dword [rbp-24]\n")
                .append("    lea r9, [rbp-168]\n")
                .append("    mov qword [rsp+32], 0\n")
                .append("    call qword [rel __imp_ReadFile]\n")
                .append("    test eax, eax\n")
                .append("    jz .read_failed\n")
                .append("    mov eax, dword [rbp-168]\n")
                .append("    jmp .read_done\n")
                .append(".read_failed:\n")
                .append("    mov rax, -1\n")
                .append(".read_done:\n");
        appendApiFrameEnd(asm);

        appendApiFrameStart(asm, "__jvs_virtual_alloc");
        asm.append("    mov rcx, qword [rbp-8]\n")
                .append("    mov rdx, qword [rbp-16]\n")
                .append("    mov r8, qword [rbp-24]\n")
                .append("    mov r9, qword [rbp-32]\n")
                .append("    call qword [rel __imp_VirtualAlloc]\n");
        appendApiFrameEnd(asm);

        asm.append("__jvs_exit:\n")
                .append("    push rbp\n")
                .append("    mov rbp, rsp\n")
                .append("    mov ecx, edi\n")
                .append("    and rsp, -16\n")
                .append("    sub rsp, 32\n")
                .append("    call qword [rel __imp_ExitProcess]\n")
                .append("    ud2\n\n");
    }

    static void appendConsoleCodePageSetup(StringBuilder asm) {
        asm.append("    sub rsp, 32\n")
                .append("    mov ecx, 65001\n")
                .append("    call qword [rel __imp_SetConsoleOutputCP]\n")
                .append("    mov ecx, 65001\n")
                .append("    call qword [rel __imp_SetConsoleCP]\n")
                .append("    add rsp, 32\n");
    }

    static void appendCommandLineSupport(StringBuilder asm) {
        asm.append("__jvs_build_windows_args:\n")
                .append("    push rbp\n")
                .append("    mov rbp, rsp\n")
                .append("    sub rsp, 160\n")
                .append("    call qword [rel __imp_GetCommandLineW]\n")
                .append("    mov rcx, rax\n")
                .append("    lea rdx, [rbp-16]\n")
                .append("    call qword [rel __imp_CommandLineToArgvW]\n")
                .append("    test rax, rax\n")
                .append("    jz __runtime_io_error\n")
                .append("    mov qword [rbp-8], rax\n")
                .append("    mov eax, dword [rbp-16]\n")
                .append("    test eax, eax\n")
                .append("    jz .windows_args_zero\n")
                .append("    dec eax\n")
                .append("    jmp .windows_args_count_ready\n")
                .append(".windows_args_zero:\n")
                .append("    xor eax, eax\n")
                .append(".windows_args_count_ready:\n")
                .append("    mov qword [rbp-24], rax\n")
                .append("    shl rax, 3\n")
                .append("    jc __runtime_out_of_memory\n")
                .append("    add rax, 32\n")
                .append("    jc __runtime_out_of_memory\n")
                .append("    mov rdi, rax\n")
                .append("    call __heap_alloc\n")
                .append("    mov qword [rbp-32], rax\n")
                .append("    lea rdx, [rel __jvs_array_vtable]\n")
                .append("    mov qword [rax], rdx\n")
                .append("    mov rdx, qword [rbp-24]\n")
                .append("    mov qword [rax+8], rdx\n")
                .append("    lea rdx, [rel __jvs_main_args_array_type]\n")
                .append("    mov qword [rax+16], rdx\n")
                .append("    lea rdx, [rel __jvs_main_args_element_type]\n")
                .append("    mov qword [rax+24], rdx\n")
                .append("    mov qword [rbp-40], 0\n")
                .append(".windows_args_loop:\n")
                .append("    mov rax, qword [rbp-40]\n")
                .append("    cmp rax, qword [rbp-24]\n")
                .append("    jae .windows_args_done\n")
                .append("    inc rax\n")
                .append("    shl rax, 3\n")
                .append("    mov rdx, qword [rbp-8]\n")
                .append("    mov rsi, qword [rdx+rax]\n")
                .append("    mov qword [rbp-48], rsi\n")
                .append("    mov ecx, 65001\n")
                .append("    xor edx, edx\n")
                .append("    mov r8, rsi\n")
                .append("    mov r9d, -1\n")
                .append("    mov qword [rsp+32], 0\n")
                .append("    mov qword [rsp+40], 0\n")
                .append("    mov qword [rsp+48], 0\n")
                .append("    mov qword [rsp+56], 0\n")
                .append("    call qword [rel __imp_WideCharToMultiByte]\n")
                .append("    test eax, eax\n")
                .append("    jz __runtime_io_error\n")
                .append("    mov qword [rbp-56], rax\n")
                .append("    mov rdi, rax\n")
                .append("    call __heap_alloc\n")
                .append("    mov qword [rbp-64], rax\n")
                .append("    mov ecx, 65001\n")
                .append("    xor edx, edx\n")
                .append("    mov r8, qword [rbp-48]\n")
                .append("    mov r9d, -1\n")
                .append("    mov qword [rsp+32], rax\n")
                .append("    mov rax, qword [rbp-56]\n")
                .append("    mov qword [rsp+40], rax\n")
                .append("    mov qword [rsp+48], 0\n")
                .append("    mov qword [rsp+56], 0\n")
                .append("    call qword [rel __imp_WideCharToMultiByte]\n")
                .append("    test eax, eax\n")
                .append("    jz __runtime_io_error\n")
                .append("    mov rax, qword [rbp-32]\n")
                .append("    mov rdx, qword [rbp-40]\n")
                .append("    mov rcx, qword [rbp-64]\n")
                .append("    mov qword [rax+rdx*8+32], rcx\n")
                .append("    inc qword [rbp-40]\n")
                .append("    jmp .windows_args_loop\n")
                .append(".windows_args_done:\n")
                .append("    mov rcx, qword [rbp-8]\n")
                .append("    call qword [rel __imp_LocalFree]\n")
                .append("    mov rax, qword [rbp-32]\n")
                .append("    leave\n")
                .append("    ret\n\n");
    }

    private static void appendApiFrameStart(StringBuilder asm, String name) {
        asm.append(name).append(":\n")
                .append("    push rbp\n")
                .append("    mov rbp, rsp\n")
                .append("    and rsp, -16\n")
                .append("    sub rsp, 224\n")
                .append("    mov qword [rbp-8], rdi\n")
                .append("    mov qword [rbp-16], rsi\n")
                .append("    mov qword [rbp-24], rdx\n")
                .append("    mov qword [rbp-32], r8\n")
                .append("    mov qword [rbp-40], r9\n")
                .append("    mov qword [rbp-48], r10\n")
                .append("    movdqu [rbp-64], xmm0\n")
                .append("    movdqu [rbp-80], xmm1\n")
                .append("    movdqu [rbp-96], xmm2\n")
                .append("    movdqu [rbp-112], xmm3\n")
                .append("    movdqu [rbp-128], xmm4\n")
                .append("    movdqu [rbp-144], xmm5\n");
    }

    private static void appendApiFrameEnd(StringBuilder asm) {
        asm.append("    movdqu xmm0, [rbp-64]\n")
                .append("    movdqu xmm1, [rbp-80]\n")
                .append("    movdqu xmm2, [rbp-96]\n")
                .append("    movdqu xmm3, [rbp-112]\n")
                .append("    movdqu xmm4, [rbp-128]\n")
                .append("    movdqu xmm5, [rbp-144]\n")
                .append("    mov r10, qword [rbp-48]\n")
                .append("    mov r9, qword [rbp-40]\n")
                .append("    mov r8, qword [rbp-32]\n")
                .append("    mov rsi, qword [rbp-16]\n")
                .append("    mov rdx, qword [rbp-24]\n")
                .append("    mov rdi, qword [rbp-8]\n")
                .append("    leave\n")
                .append("    ret\n\n");
    }
}
