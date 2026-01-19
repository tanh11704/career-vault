package com.tpanh.server.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class StrongPasswordValidator implements ConstraintValidator<StrongPassword, String> {

    @Override
    public boolean isValid(String password, ConstraintValidatorContext constraintValidatorContext) {
        // 1. Check null
        if (password == null) {
            return false;
        }

        // 2. Check Regex
        // ^                 : Bắt đầu chuỗi
        // (?=.*[0-9])       : Có chứa ít nhất 1 số (bất kể vị trí)
        // (?=.*[a-z])       : Có chứa ít nhất 1 chữ thường (bất kể vị trí)
        // (?=.*[A-Z])       : Có chứa ít nhất 1 chữ hoa (bất kể vị trí)
        // (?=.*[^a-zA-Z0-9]) : Có chứa ít nhất 1 ký tự KHÔNG PHẢI chữ/số (tức là ký tự đặc biệt bất kỳ: @, #, $, %, space, ...)
        // (?=\S+$)          : Đảm bảo toàn bộ chuỗi KHÔNG chứa khoảng trắng (nếu bạn muốn cấm dấu cách)
        // .{8,}             : Độ dài tổng cộng ít nhất 8 ký tự
        // $                 : Kết thúc chuỗi
        return password.matches("^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[^a-zA-Z0-9])(?=\\S+$).{8,}$");
    }
}
