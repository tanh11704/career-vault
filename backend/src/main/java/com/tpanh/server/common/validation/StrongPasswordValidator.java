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
        // (?=.*[0-9])       : Ít nhất 1 số
        // (?=.*[a-z])       : Ít nhất 1 chữ thường
        // (?=.*[A-Z])       : Ít nhất 1 chữ hoa
        // (?=.*[@#$%^&+=!.]) : Ít nhất 1 ký tự đặc biệt (thêm các ký tự bạn muốn cho phép)
        // (?=\S+$)          : Không chứa khoảng trắng
        // .{8,}             : Tối thiểu 8 ký tự
        // $                 : Kết thúc chuỗi
        return password.matches("^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=!.])(?=\\S+$).{8,}$");
    }
}
