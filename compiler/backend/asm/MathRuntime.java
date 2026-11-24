package compiler.backend.asm;

import java.util.List;

/** Native assembly implementations for jvs.util.Math. */
final class MathRuntime {
    private MathRuntime() {
    }

    static void append(StringBuilder asm) {
        appendConstants(asm);
        appendFunctions(asm);
    }

    private static void appendConstants(StringBuilder asm) {
        asm.append("section .rodata\n")
                .append("align 8\n")
                .append("__jvs_math_abs_mask: dq 0x7fffffffffffffff, 0x7fffffffffffffff\n")
                .append("__jvs_math_sign_mask: dq 0x8000000000000000, 0\n")
                .append("__jvs_math_one: dq 0x3ff0000000000000\n")
                .append("__jvs_math_zero: dq 0\n")
                .append("__jvs_math_half: dq 0x3fe0000000000000\n")
                .append("__jvs_math_third: dq ").append(bits(1.0 / 3.0)).append('\n')
                .append("__jvs_math_pi: dq ").append(bits(Math.PI)).append('\n')
                .append("__jvs_math_two_pi: dq ").append(bits(Math.PI * 2.0)).append('\n')
                .append("__jvs_math_to_radians: dq ").append(bits(Math.PI / 180.0)).append('\n')
                .append("__jvs_math_to_degrees: dq ").append(bits(180.0 / Math.PI)).append('\n')
                .append("__jvs_math_two63: dq 0x43e0000000000000\n")
                .append("__jvs_math_neg_two63: dq 0xc3e0000000000000\n")
                .append("__jvs_math_infinity: dq 0x7ff0000000000000\n")
                .append("__jvs_math_nan: dq 0x7ff8000000000000\n")
                .append("section .bss\n")
                .append("alignb 8\n__jvs_math_random_state: resq 1\n")
                .append("section .text\n");
    }

    private static long bits(double value) {
        return Double.doubleToRawLongBits(value);
    }

    private static void appendFunctions(StringBuilder asm) {
        asm.append(method("abs", new String[] { "double" }, "double"))
                .append(":\n    andpd xmm0, [rel __jvs_math_abs_mask]\n    ret\n\n");

        appendRandom(asm);

        asm.append(method("sqrt", new String[] { "double" }, "double"))
                .append(":\n    sqrtsd xmm0, xmm0\n    ret\n\n");

        appendCbrt(asm);
        appendPow(asm);
        appendMinMax(asm, true);
        appendMinMax(asm, false);
        appendRound(asm);
        appendIntegralRounding(asm, "floor", 1);
        appendIntegralRounding(asm, "ceil", 2);
        appendTrig(asm, "sin");
        appendTrig(asm, "cos");
        appendTrig(asm, "tan");

        asm.append(method("toRadians", new String[] { "double" }, "double"))
                .append(":\n    mulsd xmm0, [rel __jvs_math_to_radians]\n    ret\n\n")
                .append(method("toDegrees", new String[] { "double" }, "double"))
                .append(":\n    mulsd xmm0, [rel __jvs_math_to_degrees]\n    ret\n\n");
    }

    private static void appendRandom(StringBuilder asm) {
        asm.append(method("random", new String[0], "double")).append(":\n")
                .append("    mov rax, qword [rel __jvs_math_random_state]\n")
                .append("    test rax, rax\n    jnz .math_random_next\n")
                .append("    rdtsc\n    shl rdx, 32\n    or rax, rdx\n")
                .append("    xor rax, rsp\n    test rax, rax\n    jnz .math_random_seeded\n")
                .append("    mov rax, 0x9e3779b97f4a7c15\n")
                .append(".math_random_seeded:\n    mov qword [rel __jvs_math_random_state], rax\n")
                .append(".math_random_next:\n")
                .append("    mov rdx, rax\n    shr rdx, 12\n    xor rax, rdx\n")
                .append("    mov rdx, rax\n    shl rdx, 25\n    xor rax, rdx\n")
                .append("    mov rdx, rax\n    shr rdx, 27\n    xor rax, rdx\n")
                .append("    mov qword [rel __jvs_math_random_state], rax\n")
                .append("    mov rcx, 0x2545f4914f6cdd1d\n    imul rax, rcx\n")
                .append("    shr rax, 11\n    mov rcx, 0x3ff0000000000000\n    or rax, rcx\n")
                .append("    movq xmm0, rax\n    subsd xmm0, [rel __jvs_math_one]\n    ret\n\n");
    }

    private static void appendCbrt(StringBuilder asm) {
        asm.append(method("cbrt", new String[] { "double" }, "double")).append(":\n")
                .append("    ucomisd xmm0, xmm0\n    jp .math_cbrt_done\n")
                .append("    movq rax, xmm0\n    mov rcx, rax\n")
                .append("    btr rax, 63\n")
                .append("    movq xmm1, rax\n    ucomisd xmm1, [rel __jvs_math_infinity]\n")
                .append("    je .math_cbrt_done\n")
                .append("    test rax, rax\n    jz .math_cbrt_done\n")
                .append("    push rbp\n    mov rbp, rsp\n    sub rsp, 32\n")
                .append("    mov qword [rbp-8], rcx\n    mov qword [rbp-16], rax\n")
                .append("    fld qword [rbp-16]\n    fld qword [rel __jvs_math_third]\n")
                .append("    fxch st1\n    fyl2x\n")
                .append("    fld st0\n    frndint\n    fxch st1\n    fsub st0, st1\n")
                .append("    f2xm1\n    fld1\n    faddp st1, st0\n    fscale\n")
                .append("    fstp qword [rbp-24]\n    fstp st0\n")
                .append("    mov rax, qword [rbp-24]\n")
                .append("    mov rcx, qword [rbp-8]\n    test rcx, rcx\n    jns .math_cbrt_sign_ready\n")
                .append("    bts rax, 63\n    jmp .math_cbrt_sign_store\n")
                .append(".math_cbrt_sign_ready:\n    btr rax, 63\n")
                .append(".math_cbrt_sign_store:\n    movq xmm0, rax\n")
                .append("    leave\n.math_cbrt_done:\n    ret\n\n");
    }

    private static void appendPow(StringBuilder asm) {
        asm.append(method("pow", new String[] { "double", "double" }, "double"))
                .append(":\n    push rbp\n    mov rbp, rsp\n    sub rsp, 64\n")
                .append("    movsd qword [rbp-8], xmm0\n    movsd qword [rbp-16], xmm1\n")
                .append("    mov qword [rbp-24], 0\n")
                .append("    ucomisd xmm1, [rel __jvs_math_zero]\n    jp .math_pow_y_nan\n")
                .append("    je .math_pow_one\n")
                .append("    ucomisd xmm0, [rel __jvs_math_one]\n    je .math_pow_one\n")
                .append("    ucomisd xmm0, xmm0\n    jp .math_pow_nan\n")
                .append(".math_pow_y_nan:\n")
                .append("    ucomisd xmm0, [rel __jvs_math_one]\n    je .math_pow_one\n")
                .append("    ucomisd xmm1, xmm1\n    jp .math_pow_nan\n")
                .append("    movq rax, xmm1\n    btr rax, 63\n")
                .append("    movq xmm2, rax\n    ucomisd xmm2, [rel __jvs_math_infinity]\n")
                .append("    je .math_pow_infinite_exponent\n")
                .append("    movq rax, xmm0\n    mov rcx, rax\n")
                .append("    btr rax, 63\n    movq xmm2, rax\n")
                .append("    ucomisd xmm2, [rel __jvs_math_infinity]\n    je .math_pow_infinite_base\n")
                .append("    test rax, rax\n    jz .math_pow_zero_base\n")
                .append(".math_pow_nonzero:\n    test rcx, rcx\n    jns .math_pow_positive_base\n")
                .append("    fld qword [rbp-16]\n    fld st0\n    frndint\n")
                .append("    fstp qword [rbp-32]\n    fstp st0\n")
                .append("    movsd xmm1, qword [rbp-16]\n    ucomisd xmm1, qword [rbp-32]\n")
                .append("    jne .math_pow_nan\n")
                .append("    ucomisd xmm1, [rel __jvs_math_two63]\n    jae .math_pow_even\n")
                .append("    cvttsd2si rax, xmm1\n    test al, 1\n    jz .math_pow_even\n")
                .append("    mov qword [rbp-24], 1\n    jmp .math_pow_positive_base\n")
                .append(".math_pow_even:\n    mov qword [rbp-24], 0\n")
                .append(".math_pow_positive_base:\n")
                .append("    mov rax, qword [rbp-8]\n    btr rax, 63\n")
                .append("    mov qword [rbp-32], rax\n")
                .append("    fld qword [rbp-32]\n    fld qword [rbp-16]\n    fxch st1\n    fyl2x\n")
                .append("    fld st0\n    frndint\n    fxch st1\n    fsub st0, st1\n")
                .append("    f2xm1\n    fld1\n    faddp st1, st0\n    fscale\n")
                .append("    fstp qword [rbp-40]\n    fstp st0\n")
                .append("    movsd xmm0, qword [rbp-40]\n")
                .append("    cmp qword [rbp-24], 0\n    je .math_pow_done\n")
                .append("    xorpd xmm0, [rel __jvs_math_sign_mask]\n    jmp .math_pow_done\n")
                .append(".math_pow_infinite_exponent:\n")
                .append("    movq rax, xmm0\n    btr rax, 63\n")
                .append("    movq xmm2, rax\n    ucomisd xmm2, [rel __jvs_math_one]\n")
                .append("    je .math_pow_nan\n")
                .append("    ja .math_pow_abs_gt_one\n")
                .append("    jb .math_pow_abs_lt_one\n")
                .append(".math_pow_abs_gt_one:\n")
                .append("    ucomisd xmm1, [rel __jvs_math_zero]\n    ja .math_pow_infinity\n")
                .append("    jmp .math_pow_zero\n")
                .append(".math_pow_abs_lt_one:\n")
                .append("    ucomisd xmm1, [rel __jvs_math_zero]\n    ja .math_pow_zero\n")
                .append("    jmp .math_pow_infinity\n")
                .append(".math_pow_zero_base:\n")
                .append("    test rcx, rcx\n    jns .math_pow_zero_sign_ready\n")
                .append("    call .math_pow_check_odd_exponent\n")
                .append(".math_pow_zero_sign_ready:\n")
                .append("    ucomisd xmm1, [rel __jvs_math_zero]\n    ja .math_pow_zero\n")
                .append("    jmp .math_pow_infinity\n")
                .append(".math_pow_infinite_base:\n")
                .append("    test rcx, rcx\n    jns .math_pow_inf_sign_ready\n")
                .append("    call .math_pow_check_odd_exponent\n")
                .append(".math_pow_inf_sign_ready:\n")
                .append("    ucomisd xmm1, [rel __jvs_math_zero]\n    ja .math_pow_infinity\n")
                .append("    jmp .math_pow_zero\n")
                .append(".math_pow_check_odd_exponent:\n")
                .append("    mov qword [rbp-24], 0\n")
                .append("    fld qword [rbp-16]\n    fld st0\n    frndint\n")
                .append("    fstp qword [rbp-32]\n    fstp st0\n")
                .append("    movsd xmm2, qword [rbp-16]\n    ucomisd xmm2, qword [rbp-32]\n")
                .append("    jne .math_pow_check_odd_done\n")
                .append("    ucomisd xmm2, [rel __jvs_math_two63]\n    jae .math_pow_check_odd_done\n")
                .append("    cvttsd2si rax, xmm2\n    test al, 1\n    jz .math_pow_check_odd_done\n")
                .append("    mov qword [rbp-24], 1\n")
                .append(".math_pow_check_odd_done:\n    ret\n")
                .append(".math_pow_zero:\n    pxor xmm0, xmm0\n")
                .append("    cmp qword [rbp-24], 0\n    je .math_pow_done\n")
                .append("    xorpd xmm0, [rel __jvs_math_sign_mask]\n    jmp .math_pow_done\n")
                .append(".math_pow_infinity:\n    movsd xmm0, qword [rel __jvs_math_infinity]\n")
                .append("    cmp qword [rbp-24], 0\n    je .math_pow_done\n")
                .append("    xorpd xmm0, [rel __jvs_math_sign_mask]\n    jmp .math_pow_done\n")
                .append(".math_pow_one:\n    movsd xmm0, [rel __jvs_math_one]\n    jmp .math_pow_done\n")
                .append(".math_pow_nan:\n    movsd xmm0, [rel __jvs_math_nan]\n")
                .append(".math_pow_done:\n    leave\n    ret\n\n");
    }

    private static void appendMinMax(StringBuilder asm, boolean maximum) {
        String name = maximum ? "max" : "min";
        asm.append(method(name, new String[] { "double", "double" }, "double"))
                .append(":\n    ucomisd xmm0, xmm0\n    jp .math_" + name + "_nan\n")
                .append("    ucomisd xmm1, xmm1\n    jp .math_" + name + "_nan\n")
                .append("    ucomisd xmm0, xmm1\n")
                .append(maximum ? "    ja .math_" + name + "_first\n    jb .math_" + name + "_second\n"
                        : "    jb .math_" + name + "_first\n    ja .math_" + name + "_second\n")
                .append("    movq rax, xmm0\n    btr rax, 63\n")
                .append("    test rax, rax\n    jnz .math_" + name + "_first\n")
                .append("    movq rax, xmm0\n    test rax, rax\n")
                .append(maximum ? "    jns .math_" + name + "_first\n    movapd xmm0, xmm1\n    ret\n"
                        : "    js .math_" + name + "_first\n    movapd xmm0, xmm1\n    ret\n")
                .append(".math_" + name + "_first:\n    ret\n")
                .append(".math_" + name + "_second:\n    movapd xmm0, xmm1\n    ret\n")
                .append(".math_" + name + "_nan:\n    movsd xmm0, [rel __jvs_math_nan]\n    ret\n\n");
    }

    private static void appendRound(StringBuilder asm) {
        asm.append(method("round", new String[] { "double" }, "long"))
                .append(":\n    push rbp\n    mov rbp, rsp\n    sub rsp, 32\n")
                .append("    ucomisd xmm0, xmm0\n    jp .math_round_nan\n")
                .append("    ucomisd xmm0, [rel __jvs_math_two63]\n    jae .math_round_max\n")
                .append("    ucomisd xmm0, [rel __jvs_math_neg_two63]\n    jbe .math_round_min\n")
                .append("    addsd xmm0, [rel __jvs_math_half]\n    call __jvs_math_floor\n")
                .append("    cvttsd2si rax, xmm0\n    jmp .math_round_done\n")
                .append(".math_round_nan:\n    xor eax, eax\n    jmp .math_round_done\n")
                .append(".math_round_max:\n    mov rax, 0x7fffffffffffffff\n    jmp .math_round_done\n")
                .append(".math_round_min:\n    mov rax, 0x8000000000000000\n")
                .append(".math_round_done:\n    leave\n    ret\n\n");
    }

    private static void appendIntegralRounding(StringBuilder asm, String name, int mode) {
        String helper = "__jvs_math_" + name;
        asm.append(method(name, new String[] { "double" }, "double"))
                .append(":\n    jmp ").append(helper).append('\n')
                .append(helper).append(":\n    push rbp\n    mov rbp, rsp\n    sub rsp, 32\n")
                .append("    movsd qword [rbp-8], xmm0\n    fnstcw word [rbp-10]\n")
                .append("    movzx eax, word [rbp-10]\n    and ax, 0xf3ff\n")
                .append("    or ax, ").append(mode << 10).append('\n')
                .append("    mov word [rbp-12], ax\n    fldcw word [rbp-12]\n")
                .append("    fld qword [rbp-8]\n    frndint\n    fstp qword [rbp-24]\n")
                .append("    fldcw word [rbp-10]\n    movsd xmm0, qword [rbp-24]\n")
                .append("    leave\n    ret\n\n");
    }

    private static void appendTrig(StringBuilder asm, String name) {
        String helper = "__jvs_math_" + name;
        asm.append(method(name, new String[] { "double" }, "double"))
                .append(":\n    jmp ").append(helper).append('\n')
                .append(helper).append(":\n    push rbp\n    mov rbp, rsp\n    sub rsp, 32\n")
                .append("    movsd qword [rbp-8], xmm0\n    fld qword [rel __jvs_math_two_pi]\n")
                .append("    fld qword [rbp-8]\n")
                .append(".math_" + name + "_reduce:\n    fprem\n    fnstsw ax\n    test ah, 4\n    jnz .math_" + name + "_reduce\n")
                .append("    fstp st1\n");
        switch (name) {
            case "sin" -> asm.append("    fsin\n");
            case "cos" -> asm.append("    fcos\n");
            case "tan" -> asm.append("    fptan\n    fstp st0\n");
            default -> throw new IllegalArgumentException("Unknown trig operation: " + name);
        }
        asm.append("    fstp qword [rbp-16]\n    movsd xmm0, qword [rbp-16]\n")
                .append("    leave\n    ret\n\n");
    }

    private static String method(String name, String[] parameters, String result) {
        StringBuilder label = new StringBuilder("jvs.util.Math.").append(name).append("__");
        if (parameters.length == 0) {
            label.append("void");
        } else {
            for (int index = 0; index < parameters.length; index++) {
                if (index > 0) label.append("__");
                label.append(mangle(parameters[index]));
            }
        }
        return label.append("__ret_").append(mangle(result)).toString();
    }

    private static String mangle(String type) {
        if (List.of("int", "long", "double", "boolean", "void").contains(type)) {
            return type;
        }
        StringBuilder encoded = new StringBuilder("T");
        type.codePoints().forEach(codePoint -> encoded.append('_')
                .append(Integer.toHexString(codePoint)).append('_'));
        return encoded.toString();
    }
}
