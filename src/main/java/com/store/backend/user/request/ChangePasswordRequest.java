package com.store.backend.user.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class ChangePasswordRequest {
  @NotBlank(message = "Yêu cầu nhập mật khẩu cũ")
  @Size(min = 6, message = "Mật khẩu phải có ít nhất 6 ký tự")
  private String oldPassword;

  @NotBlank(message = "Yêu cầu nhập mật khẩu mới")
  @Size(min = 6, message = "Mật khẩu phải có ít nhất 6 ký tự")
  private String newPassword;
}
